/**
 * 加固项检查：针对上一轮「已知未修」的问题逐条复现。
 *   docker run --rm --network exam-system_exam-net -e TZ=Asia/Shanghai \
 *     -v "$PWD/docker:/work:ro" -e API=http://backend:8082/api node:24-alpine node /work/hardening-check.mjs
 * 结束后用 README §3 的重置命令清掉 hd- 前缀数据。
 */
const API = process.env.API || 'http://localhost:8082/api';
const local = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
const wait = (ms) => new Promise((r) => setTimeout(r, ms))

async function login(u) {
  const r = await fetch(`${API}/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: u, password: '123456' }),
  }).then((x) => x.json())
  if (r.code !== 0) throw new Error(`登录 ${u} 失败：${r.message}`)
  return r.data.token
}
async function hit(token, method, path, body) {
  const r = await fetch(API + path, {
    method,
    headers: { Authorization: `Bearer ${token}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await r.json().catch(() => ({ code: -1, message: 'HTTP ' + r.status }))
  return { http: r.status, ...json }
}

let pass = 0
let fail = 0
function t(title, ok, detail) {
  if (ok) { pass++; console.log(` ✓ ${title}`) }
  else { fail++; console.log(` ✗ ${title}\n     ${detail}`) }
}

const cleanup = []

async function main() {
  const admin = await login('admin')
  const t1 = await login('T2025001')
  const t2 = await login('T2025002')
  const s11 = await login('20230111')
  const s12 = await login('20230112')
  const s13 = await login('20230113')

  // ---------- H1 并发阅卷：两份同时改同一份答卷，不能丢分 ----------
  const exam = (await hit(t1, 'POST', '/exams', {
    paperId: 2, title: 'hd-并发阅卷', startTime: local(new Date(Date.now() - 60000)),
    endTime: local(new Date(Date.now() + 2 * 3600000)), durationMinutes: 30, lateMinutes: 60,
    maxAttempts: 1, audienceType: 1, status: 1,
  })).data
  cleanup.push(`/exams/${exam}`)
  const entered = await hit(s11, 'POST', `/exam/${exam}/enter`)
  const recordId = entered.data.recordId
  await hit(s11, 'POST', `/exam/record/${recordId}/answers`, {
    answers: entered.data.questions.filter((q) => q.qType === 5)
      .map((q) => ({ paperQuestionId: q.paperQuestionId, answer: ['学生作答内容'] })),
  })
  await hit(s11, 'POST', `/exam/record/${recordId}/submit`)
  const sheet = await hit(t1, 'GET', `/grading/record/${recordId}`)
  const essayItem = sheet.data.items.find((i) => i.reviewStatus === 1)
  const full = Number(essayItem.fullScore)
  const two = await Promise.all([
    hit(t1, 'POST', '/grading/submit', { recordId, items: [{ answerItemId: essayItem.answerItemId, score: full }], finish: true }),
    hit(t2, 'POST', '/grading/submit', { recordId, items: [{ answerItemId: essayItem.answerItemId, score: full / 2 }], finish: true }),
  ])
  const after = await hit(t1, 'GET', `/exam/record/${recordId}/result`)
  t('H1 并发阅卷不丢分（总分为客观+主观，状态已完成）',
    after.data.status === 2 && Number(after.data.totalScore) != null
    && Math.abs(Number(after.data.totalScore) - (Number(after.data.objectiveScore) + Number(after.data.subjectiveScore))) < 0.05,
    `两次提交 code=${two.map((r) => r.code).join('/')}，最终 status=${after.data.status} 客观=${after.data.objectiveScore} 主观=${after.data.subjectiveScore} 总分=${after.data.totalScore}`)

  // ---------- H2 并发规则组卷不撞死锁 ----------
  const auto = await Promise.all([1, 2, 3, 4, 5].map((i) => hit(t1, 'POST', '/papers/auto-generate', {
    title: `hd-并发抽题${i}`, categoryId: 1, passScore: 10, suggestMinutes: 20,
    rules: [{ qType: 1, count: 3, scorePerItem: 3 }],
  })))
  const deadlocks = auto.filter((r) => r.code === 1500)
  auto.filter((r) => r.code === 0).forEach((r) => cleanup.push(`/papers/${r.data}`))
  t('H2 并发规则组卷无 500 死锁', deadlocks.length === 0,
    `5 次结果 ${auto.map((r) => r.code).join(',')}，死锁 ${deadlocks.length} 次：${deadlocks[0]?.message}`)

  // ---------- H3 并发切屏计数不丢 ----------
  const switchExam = (await hit(t1, 'POST', '/exams', {
    paperId: 2, title: 'hd-切屏计数', startTime: local(new Date(Date.now() - 60000)),
    endTime: local(new Date(Date.now() + 2 * 3600000)), durationMinutes: 30, lateMinutes: 60,
    maxAttempts: 1, audienceType: 1, switchLimit: 0, status: 1,
  })).data
  cleanup.push(`/exams/${switchExam}`)
  const sEnt = await hit(s12, 'POST', `/exam/${switchExam}/enter`)
  const switches = await Promise.all([1, 2, 3, 4, 5].map(() => hit(s12, 'POST', `/exam/record/${sEnt.data.recordId}/switch`)))
  const finalCount = Number(switches[switches.length - 1].data?.switchCount)
  const recorded = await hit(s12, 'GET', `/exam/record/${sEnt.data.recordId}/result`)
  t('H3 并发上报 5 次切屏，计数为 5', Number(recorded.data.switchCount) === 5,
    `返回序列 ${switches.map((s) => s.data?.switchCount).join(',')}，最终库里 switchCount=${recorded.data.switchCount}`)
  void finalCount

  // ---------- H4 保存接口不虚报 savedCount，且不吞异常 ----------
  const fake = await hit(s12, 'POST', `/exam/record/${sEnt.data.recordId}/answers`, {
    answers: [{ paperQuestionId: 999999, answer: ['A'] }],
  })
  t('H4 不存在的题号 savedCount 为 0', fake.code === 0 && fake.data.savedCount === 0,
    `savedCount=${fake.data?.savedCount}`)
  const nullItem = await hit(s12, 'POST', `/exam/record/${sEnt.data.recordId}/answers`, { answers: [null] })
  t('H5 answers 含 null 不报 500', nullItem.code === 0 || nullItem.code === 1003,
    `code=${nullItem.code} HTTP ${nullItem.http} ${nullItem.message}`)

  // ---------- H6 并发交卷：只有一次成功，其余给一致的错误码 ----------
  const dblExam = (await hit(t1, 'POST', '/exams', {
    paperId: 2, title: 'hd-并发交卷', startTime: local(new Date(Date.now() - 60000)),
    endTime: local(new Date(Date.now() + 2 * 3600000)), durationMinutes: 30, lateMinutes: 60,
    maxAttempts: 1, audienceType: 1, status: 1,
  })).data
  cleanup.push(`/exams/${dblExam}`)
  const dEnt = await hit(s13, 'POST', `/exam/${dblExam}/enter`)
  const submits = await Promise.all([1, 2, 3].map(() => hit(s13, 'POST', `/exam/record/${dEnt.data.recordId}/submit`)))
  const okOnes = submits.filter((r) => r.code === 0)
  const rejected = submits.filter((r) => r.code === 4051)
  t('H6 并发交卷：1 次成功、其余返回 4051（不出现空响应）',
    okOnes.length === 1 && rejected.length === 2
    && okOnes[0].data.status !== undefined && okOnes[0].data.totalScore !== undefined,
    `codes=${submits.map((s) => s.code).join(',')}，成功响应键=${Object.keys(okOnes[0].data || {}).join('/')}`)

  // ---------- H7 跨教师改题 / 阅别人的卷 ----------
  const q = await hit(t1, 'POST', '/questions', {
    categoryId: 3, qType: 1, content: 'hd-李老师的题', options: [{ key: 'A', text: 'x' }, { key: 'B', text: 'y' }],
    answer: ['A'], difficulty: 1, score: 2, status: 1,
  })
  cleanup.push(`/questions/${q.data}`)
  const otherEdit = await hit(t2, 'PUT', `/questions/${q.data}`, {
    categoryId: 3, qType: 1, content: 'hd-被王老师改掉的题', options: [{ key: 'A', text: 'x' }, { key: 'B', text: 'y' }],
    answer: ['B'], difficulty: 1, score: 2, status: 1,
  })
  const otherDelete = await hit(t2, 'DELETE', `/questions/${q.data}`)
  t('H7 其他教师不能改/删我的题', otherEdit.code === 1002 && otherDelete.code === 1002,
    `改 code=${otherEdit.code} ${otherEdit.message}，删 code=${otherDelete.code} ${otherDelete.message}`)
  const crossGrade = await hit(t2, 'POST', '/grading/submit', {
    recordId, items: [{ answerItemId: essayItem.answerItemId, score: 1 }], finish: false,
  })
  t('H8 其他教师不能阅别人考试的卷', crossGrade.code === 1002, `code=${crossGrade.code} ${crossGrade.message}`)

  // ---------- H9/H10 试卷状态机 ----------
  const draft = (await hit(t1, 'POST', '/papers', { title: 'hd-草稿卷', categoryId: 3, passScore: 5, suggestMinutes: 20 })).data
  cleanup.push(`/papers/${draft}`)
  const archiveDraft = await hit(t1, 'PUT', `/papers/${draft}/archive`)
  t('H9 草稿卷不能直接归档', archiveDraft.code !== 0, `code=${archiveDraft.code} ${archiveDraft.message}`)
  await hit(t1, 'POST', `/papers/${draft}/questions`, [{ questionId: 1 }])
  const publishOk = await hit(t1, 'PUT', `/papers/${draft}/publish`)
  t('H9b 有题且分值合法的草稿卷可以发布', publishOk.code === 0, `code=${publishOk.code} ${publishOk.message}`)
  const editPublished = await hit(t1, 'PUT', `/papers/${draft}`, { title: 'hd-改掉已发布卷', passScore: 5, suggestMinutes: 20 })
  t('H10 已发布卷不能改基本信息', editPublished.code !== 0, `code=${editPublished.code} ${editPublished.message}`)

  // ---------- H11 入参边界 ----------
  const badPaper = await hit(t1, 'POST', '/papers', { title: 'hd-负数', categoryId: 3, passScore: -1, suggestMinutes: -30 })
  t('H11 负及格线/负时长被拒', badPaper.code === 1003, `code=${badPaper.code} ${badPaper.message}`)
  const badPhone = await hit(admin, 'POST', '/users', { username: 'hd-phone', realName: 'hd 手机号', phone: '不电话123', roles: ['STUDENT'] })
  if (badPhone.code === 0) cleanup.push(`/users/${badPhone.data}`)
  t('H12 手机号格式被校验', badPhone.code === 1003, `code=${badPhone.code} ${badPhone.message}`)
  const badCategory = await hit(t1, 'POST', '/papers', { title: 'hd-不存在分类', categoryId: 999999, passScore: 5, suggestMinutes: 20 })
  t('H13 不存在的分类被拒', badCategory.code === 1003, `code=${badCategory.code} ${badCategory.message}`)

  // ---------- H14 考试时长被窗口截断时，返回的时长要说真话 ----------
  const shortExam = (await hit(t1, 'POST', '/exams', {
    paperId: 2, title: 'hd-时长截断', startTime: local(new Date(Date.now() - 25 * 60000)),
    endTime: local(new Date(Date.now() + 5 * 60000)), durationMinutes: 30, lateMinutes: 60,
    maxAttempts: 1, audienceType: 1, status: 1,
  })).data
  cleanup.push(`/exams/${shortExam}`)
  const capped = await hit(await login('20230114'), 'POST', `/exam/${shortExam}/enter`)
  const remainMin = Math.round(capped.data.remainingSeconds / 60)
  t('H14 剩余时间被窗口截断时，返回时长与实际可用时间一致',
    capped.data.durationMinutes === null || Math.abs(capped.data.durationMinutes - remainMin) <= 1,
    `durationMinutes=${capped.data.durationMinutes} 但 remainingSeconds=${capped.data.remainingSeconds}（≈${remainMin} 分钟）`)

  // ---------- 清理 ----------
  for (const path of cleanup.reverse()) await hit(admin, 'DELETE', path)
  const leftPaper = await hit(admin, 'GET', '/papers?page=1&size=50&keyword=hd-')
  const leftExam = await hit(admin, 'GET', '/exams?page=1&size=50&keyword=hd-')
  const refuseDelete = await hit(admin, 'DELETE', `/exams/${exam}`)
  t('H15 有答卷的考试拒绝删除（残留属预期），草稿类可清干净',
    refuseDelete.code === 4017 && leftExam.data.total >= 1,
    `删已作答考试 code=${refuseDelete.code} ${refuseDelete.message}；残留试卷 ${leftPaper.data.total} 份、考试 ${leftExam.data.total} 场`)
  console.log('  提示：hd- 前缀数据请用 README §3 的重置命令清除')

  console.log(`\n通过 ${pass} / ${pass + fail}`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch((e) => { console.error('脚本异常：', e); process.exit(2) })
