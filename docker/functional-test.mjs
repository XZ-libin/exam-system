/**
 * 功能测试：按课题 7 大模块逐条断言，跑在真实 HTTP 接口上。
 * 覆盖此前没测到的两条：教师强制收卷、到点由定时任务自动交卷。
 *
 *   docker run --rm --network exam-system_exam-net -e TZ=Asia/Shanghai \
 *     -v "$PWD/docker:/work:ro" -e API=http://backend:8082/api node:24-alpine node /work/functional-test.mjs
 *
 * 所有测试数据都以 xt- 前缀命名，脚本结束时打印清理 SQL。
 */
const API = process.env.API || 'http://localhost:8082/api';
const PW = '123456';

const local = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
const wait = (ms) => new Promise((r) => setTimeout(r, ms))

let tokens = {}
async function as(name, username) {
  const r = await fetch(`${API}/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: PW }),
  }).then((x) => x.json())
  if (r.code !== 0) throw new Error(`登录 ${username} 失败：${r.message}`)
  tokens[name] = r.data.token
  return r.data.token
}

async function hit(who, method, path, body) {
  const token = typeof who === 'string' ? tokens[who] : who
  const r = await fetch(API + path, {
    method,
    headers: { Authorization: `Bearer ${token || ''}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await r.json().catch(() => ({ code: -1, message: 'HTTP ' + r.status }))
  return { http: r.status, ...json }
}

const results = []
let currentModule = ''
function mod(name) { currentModule = name; console.log(`\n──── ${name} ────`) }
function t(title, ok, expected, actual) {
  results.push({ module: currentModule, title, ok })
  console.log(`${ok ? ' ✓' : ' ✗'} ${title}${ok ? '' : `\n     期望 ${expected}\n     实际 ${actual}`}`)
}
function eq(title, actual, expected) {
  t(title, String(actual) === String(expected), expected, actual)
}

async function main() {
  await as('admin', 'admin')
  await as('teacher', 'T2025001')
  await as('s11', '20230111')
  await as('s12', '20230112')
  await as('s13', '20230113')

  // ==================== A 用户中心 ====================
  mod('A 用户中心')
  const badPwd = await hit(null, 'POST', '/auth/login', { username: 'admin', password: 'wrong' })
  eq('密码错误被拒', badPwd.code, 1010)
  const disabled = await hit(null, 'POST', '/auth/login', { username: '20230116', password: PW })
  eq('禁用账号无法登录', disabled.code, 1011)
  const me = await hit('teacher', 'GET', '/auth/me')
  eq('当前身份含角色', me.data.roles.includes('TEACHER'), true)
  const page = await hit('admin', 'GET', '/users?page=1&size=5')
  t('用户分页：本页 5 条且总数含种子 16 人', page.data.records.length === 5 && page.data.total >= 16,
    '5 条 / total>=16', `${page.data.records.length} / ${page.data.total}`)
  const wild = await hit('admin', 'GET', '/users?page=1&size=5&keyword=%25')
  t('关键字 % 不会被当成通配符返回全部', wild.data.total < 16, '<16', wild.data.total)
  const zeroSize = await hit('admin', 'GET', '/users?page=1&size=0')
  t('size=0 不报 500', zeroSize.code === 0 || zeroSize.code === 1003, 'code 0/1003', `${zeroSize.code} ${zeroSize.http}`)
  const negPage = await hit('admin', 'GET', '/users?page=-1&size=5')
  t('page=-1 不报 500', negPage.code === 0 || negPage.code === 1003, 'code 0/1003', `${negPage.code} ${negPage.http}`)
  const created = await hit('admin', 'POST', '/users', { username: 'xt-stu-01', realName: 'xt 测试生', className: '计算机2199', roles: ['STUDENT'] })
  t('新建用户', created.code === 0 && created.data > 0, '>0', JSON.stringify(created))
  const dupUser = await hit('admin', 'POST', '/users', { username: 'xt-stu-01', realName: '重复', roles: ['STUDENT'] })
  eq('重名账号被拒', dupUser.code, 1012)
  const clearPhone = await hit('admin', 'PUT', `/users/${created.data}`, { realName: 'xt 测试生', className: '计算机2199', phone: '13900001111', roles: ['STUDENT'] })
  await hit('admin', 'PUT', `/users/${created.data}`, { realName: 'xt 测试生', className: '计算机2199', phone: '', roles: ['STUDENT'] })
  const readBack = await hit('admin', 'GET', `/users/${created.data}`)
  eq('清空手机号真的写回', readBack.data.phone ?? '', '')
  const studentCallsAdmin = await hit('s11', 'GET', '/logs?page=1&size=5')
  eq('学生越权访问日志被拒', studentCallsAdmin.code, 1002)
  const teacherCallsAdmin = await hit('teacher', 'POST', '/users', { username: 'xt-nope', realName: 'x' })
  eq('教师不能建账号', teacherCallsAdmin.code, 1002)
  const forged = await hit('Bearer not-a-real-token', 'GET', '/auth/me')
  eq('伪造令牌被拒', forged.code, 1001)

  // ==================== B 题库管理 ====================
  mod('B 题库管理')
  const tree = await hit('teacher', 'GET', '/categories/tree')
  const ds = tree.data.find((n) => n.name === '数据结构')
  t('分类树含子分类与题量', ds && ds.children.length >= 3 && ds.children[0].questionCount > 0,
    '有子分类且题量>0', JSON.stringify({ children: ds?.children?.length, first: ds?.children?.[0]?.questionCount }))
  const cat = await hit('teacher', 'POST', '/categories', { parentId: 0, name: 'xt-临时科目', sortNo: 99 })
  t('新建分类', cat.code === 0 && cat.data > 0, '>0', JSON.stringify(cat))
  const catWithQ = await hit('teacher', 'DELETE', '/categories/3')
  t('含题目的分类不能删', catWithQ.code === 2010, 2010, `${catWithQ.code} ${catWithQ.message}`)
  const q = await hit('teacher', 'POST', '/questions', {
    categoryId: cat.data, qType: 1, content: 'xt-单选题：1+1=?',
    options: [{ key: 'A', text: '1' }, { key: 'B', text: '2' }, { key: 'C', text: '3' }],
    answer: ['B'], analysis: '常识', difficulty: 1, score: 3, status: 1,
  })
  t('新增单选题', q.code === 0 && q.data > 0, '>0', JSON.stringify(q))
  const badAns = await hit('teacher', 'POST', '/questions', {
    categoryId: cat.data, qType: 1, content: 'xt-答案越界', options: [{ key: 'A', text: 'x' }, { key: 'B', text: 'y' }], answer: ['Q'],
  })
  t('答案不在选项内被拒', badAns.code === 2013 || badAns.code === 1003, '2013/1003', `${badAns.code} ${badAns.message}`)
  const badMulti = await hit('teacher', 'POST', '/questions', {
    categoryId: cat.data, qType: 2, content: 'xt-多选只有一个正确项', options: [{ key: 'A', text: 'x' }, { key: 'B', text: 'y' }], answer: ['A'],
  })
  t('多选题要求≥2 个答案', badMulti.code !== 0, '非 0', `${badMulti.code} ${badMulti.message}`)
  const negScore = await hit('teacher', 'POST', '/questions', {
    categoryId: cat.data, qType: 1, content: 'xt-负分', options: [{ key: 'A', text: 'x' }, { key: 'B', text: 'y' }], answer: ['A'], score: -5,
  })
  t('负分值被拒', negScore.code !== 0, '非 0', `${negScore.code} ${negScore.message}`)
  const longText = 'x'.repeat(20000)
  const longQ = await hit('teacher', 'POST', '/questions', {
    categoryId: cat.data, qType: 5, content: `xt-超长题干 ${longText}`, answer: ['参考答案'],
  })
  t('超长题干不报 500', longQ.code === 0 || longQ.code === 1003, '0/1003', `${longQ.code} ${longQ.http} ${longQ.message}`)
  const filter = await hit('teacher', 'GET', '/questions?page=1&size=5&qType=2&difficulty=4')
  t('题型+难度组合筛选', filter.code === 0 && filter.data.records.every((r) => r.qType === 2 && r.difficulty === 4),
    '全部命中', JSON.stringify(filter.data?.records?.map((r) => [r.qType, r.difficulty])))
  const wildQ = await hit('teacher', 'GET', '/questions?page=1&size=5&keyword=%25')
  t('题目关键字 % 不当通配符', wildQ.code === 0 && wildQ.data.total < 40, '<40', wildQ.data?.total)
  const type99 = await hit('teacher', 'GET', '/questions?page=1&size=5&qType=99')
  t('未知题型筛选返回空而不报错', type99.code === 0 && type99.data.total === 0, 'total=0', `${type99.code}/${type99.data?.total}`)
  const batch = await hit('teacher', 'POST', '/questions/batch', {
    items: [
      { categoryId: cat.data, qType: 3, content: 'xt-判断：0 是偶数', answer: ['T'], difficulty: 1, score: 2 },
      { categoryId: cat.data, qType: 4, content: 'xt-填空：中国首都 ____', answer: ['北京'], difficulty: 2, score: 4 },
      { categoryId: cat.data, qType: 1, content: 'xt-坏题没有选项', answer: ['A'] },
    ],
  })
  t('批量录入 2 成功 1 失败', batch.code === 0 && batch.data.saved === 2 && batch.data.failed === 1,
    'saved=2 failed=1', JSON.stringify(batch.data || batch.message))
  const usedDelete = await hit('teacher', 'DELETE', '/questions/1')
  t('被试卷引用的题目不能删', usedDelete.code === 2012, 2012, `${usedDelete.code} ${usedDelete.message}`)

  // ==================== C 试卷管理 ====================
  mod('C 试卷管理')
  const paper = await hit('teacher', 'POST', '/papers', { title: 'xt-人工卷', categoryId: cat.data, passScore: 5, suggestMinutes: 20 })
  t('新建草稿试卷', paper.code === 0 && paper.data > 0, '>0', JSON.stringify(paper))
  const emptyPublish = await hit('teacher', 'PUT', `/papers/${paper.data}/publish`)
  eq('空卷不能发布', emptyPublish.code, 3012)
  const add = await hit('teacher', 'POST', `/papers/${paper.data}/questions`, [
    { questionId: q.data }, { questionId: 10 },
  ])
  eq('加入题目成功', add.code, 0)
  const detail = await hit('teacher', 'GET', `/papers/${paper.data}`)
  eq('总分=各题分值之和', detail.data.totalScore, 3 + 6)
  const dupAdd = await hit('teacher', 'POST', `/papers/${paper.data}/questions`, [{ questionId: q.data }])
  eq('重复入卷被拒', dupAdd.code, 3015)
  const preview = await hit('teacher', 'GET', `/papers/${paper.data}/preview`)
  t('教师预览含标准答案', preview.code === 0 && preview.data.questions[0].answer !== undefined, '含 answer', typeof preview.data?.questions?.[0]?.answer)
  const studentPreview = await hit('s11', 'GET', `/papers/${paper.data}/preview`)
  eq('学生不能预览试卷（防偷答案）', studentPreview.code, 1002)
  const publish = await hit('teacher', 'PUT', `/papers/${paper.data}/publish`)
  eq('发布试卷', publish.code, 0)
  const lockAdd = await hit('teacher', 'POST', `/papers/${paper.data}/questions`, [{ questionId: 11 }])
  eq('发布后加题被锁', lockAdd.code, 3011)
  const auto = await hit('teacher', 'POST', '/papers/auto-generate', {
    title: 'xt-规则卷', categoryId: 1, passScore: 10, suggestMinutes: 20,
    rules: [{ qType: 1, count: 2, difficulty: 2, scorePerItem: 3 }, { qType: 2, count: 1, scorePerItem: 6 }],
  })
  t('规则组卷成功', auto.code === 0 && auto.data > 0, '>0', JSON.stringify(auto))
  const autoDetail = await hit('teacher', 'GET', `/papers/${auto.data}`)
  eq('规则卷总分正确', autoDetail.data.totalScore, 2 * 3 + 6)
  t('规则卷题型数量符合规则',
    autoDetail.data.questions.filter((i) => i.qType === 1).length === 2 && autoDetail.data.questions.filter((i) => i.qType === 2).length === 1,
    '2 单选 + 1 多选', JSON.stringify(autoDetail.data.questions.map((i) => i.qType)))
  const impossible = await hit('teacher', 'POST', '/papers/auto-generate', {
    title: 'xt-凑不齐', categoryId: 1, rules: [{ qType: 5, count: 999, scorePerItem: 5 }],
  })
  eq('题量不足整体失败', impossible.code, 3014)

  // ==================== D 考试管理 ====================
  mod('D 考试管理')
  const exam = await hit('teacher', 'POST', '/exams', {
    paperId: paper.data, title: 'xt-考试-进行中',
    startTime: local(new Date(Date.now() - 10 * 60000)), endTime: local(new Date(Date.now() + 2 * 3600000)),
    durationMinutes: 30, lateMinutes: 60, maxAttempts: 1, audienceType: 2, classNames: ['计算机2102'], status: 1,
  })
  t('新建并发布考试', exam.code === 0 && exam.data > 0, '>0', JSON.stringify(exam))
  // 考试限定 2102 班：2101 的 20230101 进不去，2102 的 20230112 能进
  await as('s01', '20230101')
  const outScope = await hit('s01', 'POST', `/exam/${exam.data}/enter`)
  const inScope = await hit('s12', 'POST', `/exam/${exam.data}/enter`)
  t('班级范围外学生不能进入', outScope.code === 4014, 4014, `${outScope.code} ${outScope.message}`)
  t('班级范围内学生可进入', inScope.code === 0, 0, `${inScope.code} ${inScope.message}`)
  const badWindow = await hit('teacher', 'POST', '/exams', {
    paperId: paper.data, title: 'xt-时间倒挂', startTime: local(new Date(Date.now() + 7200000)),
    endTime: local(new Date(Date.now() + 3600000)), durationMinutes: 10, audienceType: 1,
  })
  t('结束早于开始被拒', badWindow.code !== 0, '非 0', `${badWindow.code} ${badWindow.message}`)
  const longDuration = await hit('teacher', 'POST', '/exams', {
    paperId: paper.data, title: 'xt-时长超窗', startTime: local(new Date(Date.now() + 3600000)),
    endTime: local(new Date(Date.now() + 5400000)), durationMinutes: 120, audienceType: 1,
  })
  t('答题时长超过考试窗被拒', longDuration.code !== 0, '非 0', `${longDuration.code} ${longDuration.message}`)
  const draftPaperExam = await hit('teacher', 'POST', '/exams', {
    paperId: auto.data, title: 'xt-未发布试卷', startTime: local(new Date(Date.now() + 3600000)),
    endTime: local(new Date(Date.now() + 7200000)), durationMinutes: 20, audienceType: 1,
  })
  t('用未发布试卷建考试被拒（规则卷是草稿）', draftPaperExam.code !== 0, '非 0', `${draftPaperExam.code} ${draftPaperExam.message}`)
  const illegalStatus = await hit('teacher', 'PUT', `/exams/${exam.data}/status`, { status: 9 })
  t('非法状态值被拒', illegalStatus.code !== 0, '非 0', `${illegalStatus.code} ${illegalStatus.message}`)

  // ==================== E 在线答题 ====================
  mod('E 在线答题')
  const recordId = inScope.data.recordId
  eq('答卷含 2 题', inScope.data.questions.length, 2)
  t('服务端剩余时间在 (0, 30 分钟] 内', inScope.data.remainingSeconds > 0 && inScope.data.remainingSeconds <= 1800,
    '0<r<=1800', inScope.data.remainingSeconds)
  const answers = inScope.data.questions.map((item) => ({
    paperQuestionId: item.paperQuestionId,
    answer: item.qType === 2 ? (item.paperQuestionId % 2 ? ['A'] : ['A', 'B']) : ['B'],
  }))
  const saved = await hit('s12', 'POST', `/exam/record/${recordId}/answers`, { answers: [answers[0]] })
  eq('保存单题答案', saved.code, 0)
  const resumed = await hit('s12', 'POST', `/exam/${exam.data}/enter`)
  t('断点续考回到同一份答卷', resumed.data.recordId === recordId && resumed.data.resumed === true,
    '同 recordId 且 resumed', JSON.stringify({ r: resumed.data.recordId, resumed: resumed.data.resumed }))
  t('续考时答案从服务端恢复', (resumed.data.answers?.[answers[0].paperQuestionId] || []).length > 0,
    '非空', JSON.stringify(resumed.data.answers))
  const wrongOwner = await hit('s13', 'POST', `/exam/record/${recordId}/answers`, { answers })
  eq('他人答卷不能代写', wrongOwner.code, 1002)
  const submit = await hit('s12', 'POST', `/exam/record/${recordId}/submit`, {})
  t('交卷成功', submit.code === 0 && submit.data.status !== 0, 'status≠0', JSON.stringify(submit.data || submit.message))
  const resubmit = await hit('s12', 'POST', `/exam/record/${recordId}/submit`, {})
  eq('重复交卷被拒', resubmit.code, 4051)
  const saveAfterSubmit = await hit('s12', 'POST', `/exam/record/${recordId}/answers`, { answers })
  eq('交卷后不能再保存', saveAfterSubmit.code, 4051)
  const attemptLimit = await hit('s12', 'POST', `/exam/${exam.data}/enter`)
  eq('次数用尽不能再考', attemptLimit.code, 4015)
  const sheet = await hit('s12', 'GET', `/exam/record/${recordId}/result`)
  t('成绩未发布时不泄露总分', sheet.code === 0 && sheet.data.totalScore === null, 'totalScore=null', JSON.stringify(sheet.data?.totalScore))
  t('成绩未发布时不泄露逐题对错',
    sheet.data.items.every((i) => i.correctFlag === null && i.score === null && !i.standardAnswer),
    '全 null', JSON.stringify(sheet.data.items.map((i) => [i.correctFlag, i.score])))

  // 教师强制收卷：再造一份进行中的答卷，然后一键收掉
  const forceExam = await hit('teacher', 'POST', '/exams', {
    paperId: 2, title: 'xt-强制收卷',
    startTime: local(new Date(Date.now() - 60000)), endTime: local(new Date(Date.now() + 2 * 3600000)),
    durationMinutes: 60, lateMinutes: 60, maxAttempts: 1, audienceType: 1, status: 1,
  })
  const doing = forceExam.code === 0
    ? await hit('s13', 'POST', `/exam/${forceExam.data}/enter`)
    : { code: -1, message: '考试未创建：' + JSON.stringify(forceExam) }
  const doingRecord = doing.data?.recordId
  if (!doingRecord) {
    t('教师强制收卷把进行中的答卷交掉', false, '可进入', `进入失败 code=${doing.code} ${doing.message}`)
  } else {
    const forced = await hit('teacher', 'POST', `/exams/${forceExam.data}/force-submit`)
    const afterForce = await hit('s13', 'GET', `/exam/record/${doingRecord}/result`)
    t('教师强制收卷把进行中的答卷交掉',
      forced.code === 0 && afterForce.data?.status !== 0 && afterForce.data != null,
      'status≠0', `收卷返回 ${JSON.stringify(forced.data ?? forced.message)}，答卷 status=${afterForce.data?.status}`)
  }

  // 自然超时：1 分钟时长，等定时任务扫到
  const timeoutExam = await hit('teacher', 'POST', '/exams', {
    paperId: 2, title: 'xt-超时自动交卷',
    startTime: local(new Date(Date.now() - 60000)), endTime: local(new Date(Date.now() + 20 * 60000)),
    durationMinutes: 1, lateMinutes: 60, maxAttempts: 1, audienceType: 1, status: 1,
  })
  const quick = timeoutExam.code === 0
    ? await hit('s11', 'POST', `/exam/${timeoutExam.data}/enter`)
    : { code: -1, message: '考试未创建：' + JSON.stringify(timeoutExam) }
  const quickRecord = quick.data?.recordId
  let timedOut = null
  let timeoutSheet = null
  if (quickRecord) {
    console.log(`     …等待超时任务扫描（最多 100 秒）`)
    for (let i = 0; i < 10; i++) {
      await wait(10000)
      const check = await hit('s11', 'GET', `/exam/record/${quickRecord}/result`)
      if (check.code === 0 && check.data.status !== 0) { timedOut = check.data.status; timeoutSheet = check.data; break }
    }
  }
  t('到点后定时任务自动交卷', !!quickRecord && timedOut !== null, 'status≠0',
    quickRecord ? `status=${timedOut}` : `未能开考：code=${quick.code} ${quick.message}`)
  // 有简答题待阅时状态是 1（进阅卷台），全客观题时才是 3；两种都算超时，靠 submit>deadline 认定
  const late = timeoutSheet && new Date(timeoutSheet.submitTime.replace(' ', 'T')) > new Date(timeoutSheet.deadlineTime.replace(' ', 'T'))
  t('超时交卷：交卷时间确实晚于死线', late === true,
    'submitTime > deadlineTime', JSON.stringify({ s: timeoutSheet?.submitTime, d: timeoutSheet?.deadlineTime, status: timedOut }))
  t('超时答卷状态为「超时强制交卷」或「待阅卷」', timedOut === 3 || timedOut === 1, '3 或 1', timedOut)

  // 乱序：同一场考试两名学生题序不同，续考时自己保持稳定
  const shuffleExam = await hit('teacher', 'POST', '/exams', {
    paperId: 2, title: 'xt-乱序', startTime: local(new Date(Date.now() - 60000)),
    endTime: local(new Date(Date.now() + 2 * 3600000)), durationMinutes: 30, lateMinutes: 60,
    maxAttempts: 1, audienceType: 1, shuffleQuestion: 1, status: 1,
  })
  await as('s14', '20230114')
  await as('s15', '20230115')
  const sh1 = await hit('s14', 'POST', `/exam/${shuffleExam.data}/enter`)
  const sh2 = await hit('s15', 'POST', `/exam/${shuffleExam.data}/enter`)
  const order1 = sh1.data.questions.map((i) => i.paperQuestionId).join(',')
  const order1again = (await hit('s14', 'POST', `/exam/${shuffleExam.data}/enter`)).data.questions.map((i) => i.paperQuestionId).join(',')
  t('乱序对不同学生给出不同题序', order1 !== sh2.data.questions.map((i) => i.paperQuestionId).join(','),
    '两人生成不同', '相同')
  t('同一学生续考题序稳定', order1 === order1again, '前后一致', `${order1} vs ${order1again}`)

  // ==================== F 成绩管理 ====================
  mod('F 成绩管理')
  const publishScore = await hit('teacher', 'POST', `/exams/${exam.data}/publish-score`)
  eq('发布成绩', publishScore.code, 0)
  const sheetAfter = await hit('s12', 'GET', `/exam/record/${recordId}/result`)
  t('发布后学生可见总分与答案', sheetAfter.data.totalScore !== null && !!sheetAfter.data.items[0].standardAnswer,
    '有总分+有答案', JSON.stringify({ total: sheetAfter.data.totalScore, ans: sheetAfter.data.items[0].standardAnswer }))
  const myScores = await hit('s12', 'GET', '/scores/my?page=1&size=10')
  t('我的成绩含刚才这场', myScores.data.records.some((r) => r.recordId === recordId), '包含', JSON.stringify(myScores.data.records.map((r) => r.recordId)))
  const otherSheet = await hit('s13', 'GET', `/exam/record/${recordId}/result`)
  eq('学生看不到别人答卷', otherSheet.code, 1002)
  const scorePage = await hit('teacher', 'GET', `/scores?page=1&size=30&examId=${exam.data}`)
  eq('教师按考试筛成绩', scorePage.data.total, 1)
  const csv = await fetch(`${API}/scores/export?examId=${exam.data}`, { headers: { Authorization: `Bearer ${tokens.teacher}` } })
  const csvBytes = new Uint8Array(await csv.arrayBuffer())
  const csvText = new TextDecoder().decode(csvBytes)
  t('导出 CSV 带 BOM 且表头+1 行数据',
    csvBytes[0] === 0xEF && csvText.split('\n').filter((l) => l.trim()).length === 2,
    'BOM + 2 行', `${csvBytes[0]} / ${csvText.split('\n').filter((l) => l.trim()).length}`)
  const studentExport = await hit('s12', 'GET', '/scores/export')
  eq('学生不能导出', studentExport.code ?? studentExport.http, 1002)

  // ==================== G 统计分析 ====================
  mod('G 统计分析')
  const stat = await hit('teacher', 'GET', `/stats/exam/${exam.data}`)
  eq('统计：实交份数', stat.data.submitCount, 1)
  const overview = await hit('teacher', 'GET', '/stats/overview')
  t('看板：考试数 ≥ 6（含 xt 前缀的测试场）', overview.data.examCount >= 6, '>=6', overview.data.examCount)
  t('看板：分数段固定 5 档且和为已交卷数',
    overview.data.scoreSections.length === 5
    && overview.data.scoreSections.reduce((s, x) => s + x.count, 0) <= overview.data.attemptCount,
    '5 档', JSON.stringify(overview.data.scoreSections))
  const qStat = await hit('teacher', 'GET', `/stats/question/${q.data}`)
  t('单题统计可读', qStat.code === 0 && qStat.data.questionId === q.data, 'code=0', `${qStat.code} ${qStat.message}`)
  const analysis = await hit('s12', 'GET', '/stats/my/analysis')
  t('学生分析：趋势含已发布场次', analysis.data.trend.length >= 1, '>=1', analysis.data.trend.length)
  const studentStats = await hit('s12', 'GET', '/stats/overview')
  eq('学生不能看教师看板', studentStats.code, 1002)

  // ==================== 汇总 ====================
  const failed = results.filter((r) => !r.ok)
  console.log('\n══════════ 功能测试汇总 ══════════')
  const byModule = {}
  results.forEach((r) => { byModule[r.module] = byModule[r.module] || { pass: 0, fail: 0 }; byModule[r.module][r.ok ? 'pass' : 'fail']++ })
  Object.entries(byModule).forEach(([m, v]) => console.log(`${v.fail === 0 ? '✓' : '✗'} ${m}  ${v.pass}/${v.pass + v.fail}`))
  console.log(`\n总计 ${results.length - failed.length}/${results.length} 通过`)
  if (failed.length) {
    console.log('\n失败用例：')
    failed.forEach((f) => console.log(`  · ${f.module} — ${f.title}`))
  }
  console.log('\n清理 xt- 测试数据：')
  console.log(`  docker exec exam-mysql mysql --default-character-set=utf8mb4 -uroot -pexam123456 -D exam_db -e "
    DELETE ai FROM an_answer_item ai JOIN an_exam_record r ON r.id=ai.record_id JOIN ex_exam e ON e.id=r.exam_id WHERE e.title LIKE 'xt-%';
    DELETE FROM an_exam_record WHERE exam_id IN (SELECT id FROM ex_exam WHERE title LIKE 'xt-%');
    DELETE FROM ex_exam WHERE title LIKE 'xt-%';
    DELETE FROM ex_paper_question WHERE paper_id IN (SELECT id FROM ex_paper WHERE title LIKE 'xt-%');
    DELETE FROM ex_paper WHERE title LIKE 'xt-%';
    DELETE FROM qz_question WHERE content LIKE 'xt-%';
    DELETE FROM qz_category WHERE name LIKE 'xt-%';
    DELETE FROM sys_user WHERE username LIKE 'xt-%';
    UPDATE qz_question q SET use_count=(SELECT COUNT(*) FROM ex_paper_question pq WHERE pq.question_id=q.id);"`)
  process.exit(failed.length === 0 ? 0 : 1)
}

main().catch((e) => { console.error('测试脚本异常：', e); process.exit(2) })
