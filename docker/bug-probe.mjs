/**
 * 缺陷复现脚本：只证实/证伪，不修任何东西。
 *   docker run --rm --network exam-system_exam-net -e TZ=Asia/Shanghai \
 *     -v "$PWD/docker:/work:ro" -e API=http://backend:8082/api node:24-alpine node /work/bug-probe.mjs
 *
 * 跑完会留下名字带「探针」的考试/试卷/账号，用下面这条清掉：
 *   docker exec exam-mysql mysql --default-character-set=utf8mb4 -uroot -pexam123456 -D exam_db -e "
 *     DELETE ai FROM an_answer_item ai JOIN an_exam_record r ON r.id=ai.record_id
 *       JOIN ex_exam e ON e.id=r.exam_id WHERE e.title LIKE '探针%';
 *     DELETE FROM an_exam_record WHERE exam_id IN (SELECT id FROM ex_exam WHERE title LIKE '探针%');
 *     DELETE FROM ex_exam WHERE title LIKE '探针%';
 *     DELETE FROM ex_paper_question WHERE paper_id IN (SELECT id FROM ex_paper WHERE title LIKE '探针%');
 *     DELETE FROM ex_paper WHERE title LIKE '探针%';
 *     DELETE FROM sys_user WHERE username LIKE 'probe%';
 *     UPDATE qz_question q SET use_count=(SELECT COUNT(*) FROM ex_paper_question pq WHERE pq.question_id=q.id);"
 */
const API = process.env.API || 'http://localhost:8082/api';

async function login(username) {
  const r = await fetch(`${API}/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: '123456' }),
  }).then((x) => x.json())
  if (r.code !== 0) throw new Error(`登录 ${username} 失败：${r.message}`)
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

// 后端接收的是本地墙上时间，容器必须带 TZ=Asia/Shanghai，否则 toISOString 给的是 UTC
const local = (date) => new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
const report = []

function note(id, title, confirmed, evidence) {
  report.push({ id, title, confirmed })
  console.log(`${confirmed ? '✗ 成立' : '· 未复现'} [${id}] ${title}\n    ${evidence}`)
}

async function main() {
  const admin = await login('admin')
  const student = await login('20230111')

  // 1 学生能否读到答案键
  const paper = await hit(student, 'GET', '/papers/1')
  const bank = await hit(student, 'GET', '/questions?page=1&size=2')
  note('1', '学生可读到试卷/题库的标准答案',
    (paper.code === 0 && paper.data?.questions?.[0]?.answer !== undefined)
    || (bank.code === 0 && bank.data?.records?.[0]?.answer !== undefined),
    `GET /papers/1 → code=${paper.code} answer=${JSON.stringify(paper.data?.questions?.[0]?.answer ?? paper.message)}；`
    + `GET /questions → code=${bank.code} answer=${JSON.stringify(bank.data?.records?.[0]?.answer ?? bank.message)}`)

  // 3 成绩未发布时逐题对错是否仍返回
  // 前提：先用 SQL 把考试 1 的成绩收回未发布 —— UPDATE ex_exam SET score_published=0 WHERE id=1;
  const own = await hit(await login('20230101'), 'GET', '/exam/record/1/result')
  const head = own.data
  note('3', '未发布成绩时逐题对错/得分仍返回',
    !!head && head.scorePublished === 0
    && (head.items?.some((i) => i.correctFlag !== null || i.score !== null) || head.objectiveScore !== null),
    `record 1：scorePublished=${head?.scorePublished} 总分=${head?.totalScore} 客观=${head?.objectiveScore} `
    + `第一题 correctFlag=${head?.items?.[0]?.correctFlag} score=${head?.items?.[0]?.score}`)

  // 探针考试 A：允许迟到 60 分钟、切屏上限 2；探针考试 B：迟到 0 分钟
  const examA = (await hit(admin, 'POST', '/exams', {
    paperId: 2, title: '探针A-切屏与并发',
    startTime: local(new Date(Date.now() - 30 * 60_000)), endTime: local(new Date(Date.now() + 2 * 3600_000)),
    durationMinutes: 20, lateMinutes: 60, maxAttempts: 1, audienceType: 1, switchLimit: 2, status: 1,
  })).data
  const examB = (await hit(admin, 'POST', '/exams', {
    paperId: 2, title: '探针B-迟到0分钟',
    startTime: local(new Date(Date.now() - 2 * 3600_000)), endTime: local(new Date(Date.now() + 2 * 3600_000)),
    durationMinutes: 20, lateMinutes: 0, maxAttempts: 1, audienceType: 1, switchLimit: 0, status: 1,
  })).data
  if (!examA || !examB) {
    console.log(`探针考试创建失败（A=${examA} B=${examB}），跳过答题相关检查`)
    return finish()
  }

  // 7 lateMinutes = 0 的语义
  const lateEntry = await hit(await login('20230115'), 'POST', `/exam/${examB}/enter`)
  note('7', 'lateMinutes=0 被当成「不限迟到」', lateEntry.code === 0,
    `开考 2 小时后仍能进入：code=${lateEntry.code} ${lateEntry.message}`)

  // 5 并发进入同一场考试
  const twin = await login('20230112')
  const both = await Promise.all([
    hit(twin, 'POST', `/exam/${examA}/enter`),
    hit(twin, 'POST', `/exam/${examA}/enter`),
  ])
  note('5', '并发进入考试出现 500', both.some((r) => r.code === 1500 || r.code === -1),
    both.map((r) => `code=${r.code} ${r.message}`).join(' | '))

  // 2 切屏达上限的强制交卷是否真的落库
  const token13 = await login('20230113')
  const entered = await hit(token13, 'POST', `/exam/${examA}/enter`)
  const recordId = entered.data?.recordId
  if (recordId) {
    const first = await hit(token13, 'POST', `/exam/record/${recordId}/switch`)
    const second = await hit(token13, 'POST', `/exam/record/${recordId}/switch`)
    const after = await hit(token13, 'GET', `/exam/record/${recordId}/result`)
    note('2', '切屏达上限后强制交卷被自身事务回滚',
      after.data?.status === 0,
      `第1次 switchCount=${first.data?.switchCount ?? first.message}；第2次 code=${second.code} `
      + `forced=${second.data?.forced} ${second.message || ''}；复查答卷 status=${after.data?.status}`
      + `（0=还在作答中＝交卷没落库，1/2/3=已交卷）`)

    // 6 留空的简答题是否进阅卷台
    const pending = await hit(admin, 'GET', '/grading/pending?page=1&size=50')
    const inPending = pending.data?.records?.some((r) => r.recordId === recordId)
    const essay = after.data?.items?.find((i) => i.qType === 5)
    note('6', '留空的简答题被算成客观题 0 分、永不进阅卷台',
      !!essay && essay.reviewStatus === 0 && !inPending,
      `简答题 reviewStatus=${essay?.reviewStatus}（1=待人工阅卷）score=${essay?.score}；`
      + `待阅清单里${inPending ? '有' : '没有'}这份答卷`)
  } else {
    note('2', '切屏上限路径未能测试', false, `进入考试失败：code=${entered.code} ${entered.message}`)
  }

  // 9 禁用账号后旧令牌是否仍可用（按用户名查到真实 id，别再写死）
  const victimName = '20230114'
  const victim = await login(victimName)
  const victimId = (await hit(admin, 'GET', `/users?page=1&size=5&keyword=${victimName}`)).data.records[0].id
  await hit(admin, 'PUT', `/users/${victimId}/status`, { status: 0 })
  const stillWorks = await hit(victim, 'GET', '/auth/me')
  note('9', '禁用账号后旧令牌仍可用', stillWorks.code === 0,
    `${victimName}(id=${victimId}) 已禁用，/auth/me → code=${stillWorks.code} ${stillWorks.data?.user?.realName || stillWorks.message}`)
  await hit(admin, 'PUT', `/users/${victimId}/status`, { status: 1 })

  // 10 清空手机号 / 11 同名账号二次删除
  const uid = (await hit(admin, 'POST', '/users', {
    username: 'probe0001', realName: '探针同学', className: '计算机2199', phone: '13800000000', roles: ['STUDENT'],
  })).data
  if (uid) {
    await hit(admin, 'PUT', `/users/${uid}`, { realName: '探针同学', className: '计算机2199', phone: '', roles: ['STUDENT'] })
    const read = await hit(admin, 'GET', `/users/${uid}`)
    note('10', '清空手机号保存后仍是旧值', read.data?.phone === '13800000000',
      `提交 phone="" 后读回 phone=${JSON.stringify(read.data?.phone)}`)
    const del1 = await hit(admin, 'DELETE', `/users/${uid}`)
    const again = await hit(admin, 'POST', '/users', { username: 'probe0001', realName: '探针二号', roles: ['STUDENT'] })
    const del2 = again.code === 0
      ? await hit(admin, 'DELETE', `/users/${again.data}`)
      : { code: -1, message: '重建失败：' + again.message }
    note('11', '同名账号二次删除撞唯一键（500）', del2.code !== 0,
      `首次删除 code=${del1.code}，重建 code=${again.code}，二次删除 code=${del2.code} ${del2.message}`)
  }

  // 8 删除试卷后题目引用计数
  const probePaper = (await hit(admin, 'POST', '/papers', { title: '探针空卷', categoryId: 3, passScore: 1, suggestMinutes: 5 })).data
  if (probePaper) {
    await hit(admin, 'POST', `/papers/${probePaper}/questions`, [{ questionId: 37 }])
    await hit(admin, 'DELETE', `/papers/${probePaper}`)
    const after = await hit(admin, 'GET', '/questions/37')
    const del = await hit(admin, 'DELETE', '/questions/37')
    note('8', '删除试卷后题目仍被记为引用、无法删除',
      Number(after.data?.useCount) > 0 || del.code !== 0,
      `删卷后 use_count=${after.data?.useCount}，删题 code=${del.code} ${del.message}`)
  }

  finish()
}

function finish() {
  console.log('\n=== 汇总 ===')
  report.forEach((r) => console.log(`${r.confirmed ? '成立  ' : '未复现'} ${r.id} ${r.title}`))
  console.log(`成立 ${report.filter((r) => r.confirmed).length} / ${report.length}`)
}

main().catch((error) => {
  console.error('探针脚本异常：', error)
  process.exit(1)
})
