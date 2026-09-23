/**
 * 写接口检查：覆盖 smoke-check 打不到的增删改与导入路径，跑完自己清理干净。
 *   docker run --rm --network exam-system_exam-net \
 *     -v "$PWD/docker:/work:ro" -e API=http://backend:8082/api node:24-alpine node /work/write-path-check.mjs
 */
const API = process.env.API || 'http://localhost:8082/api';
const token = (await (await fetch(`${API}/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: '123456' }),
})).json()).data.token;

let pass = 0
let failed = 0
function check(label, ok, detail = '') {
  if (ok) { pass++; console.log(`✓ ${label}${detail ? ' — ' + detail : ''}`) }
  else { failed++; console.log(`✗ ${label}${detail ? ' — ' + detail : ''}`) }
}

async function call(method, path, body, headers = {}) {
  const payload = body === undefined ? undefined
    : (typeof body === 'string' || body instanceof FormData ? body : JSON.stringify(body))
  const response = await fetch(API + path, {
    method,
    headers: { Authorization: `Bearer ${token}`, ...(payload === undefined ? {} : { 'Content-Type': 'application/json' }), ...headers },
    body: payload,
  })
  const json = await response.json().catch(() => ({ code: -1, message: 'HTTP ' + response.status }))
  return json
}

const created = { categories: [], questions: [], papers: [], exams: [], users: [] }
const cleanup = async () => {
  const drop = async (label, method, path) => {
    const result = await call(method, path)
    if (result.code !== 0) console.log(`  ! 清理 ${label} 失败：code=${result.code} ${result.message}`)
  }
  for (const id of created.exams) await drop(`exams/${id}`, 'DELETE', `/exams/${id}`)
  for (const id of created.papers) await drop(`papers/${id}`, 'DELETE', `/papers/${id}`)
  for (const id of new Set(created.questions)) await drop(`questions/${id}`, 'DELETE', `/questions/${id}`)
  for (const id of created.categories) await drop(`categories/${id}`, 'DELETE', `/categories/${id}`)
  for (const id of created.users) await drop(`users/${id}`, 'DELETE', `/users/${id}`)
}

try {
  // ---------- 分类 ----------
  const categoryId = (await call('POST', '/categories', { parentId: 0, name: '临时科目', sortNo: 99 })).data
  created.categories.push(categoryId)
  let tree = await call('GET', '/categories/tree')
  check('新建分类出现在树里', tree.code === 0 && tree.data.some((n) => n.id === categoryId))
  check('分类重命名', (await call('PUT', `/categories/${categoryId}`, { parentId: 0, name: '临时科目2', sortNo: 99 })).code === 0)
  check('带题目的分类不允许删除', (await call('DELETE', '/categories/3')).code !== 0)

  // ---------- 题目：增 / 改 / 校验 / 批 / 导入 ----------
  const goodQuestion = {
    categoryId, qType: 1, content: '临时题：1 + 1 等于几？',
    options: [{ key: 'A', text: '1' }, { key: 'B', text: '2' }], answer: ['B'],
    analysis: '常识。', difficulty: 1, score: 2, status: 1,
  }
  const questionId = (await call('POST', '/questions', goodQuestion)).data
  created.questions.push(questionId)
  check('新增题目', !!questionId)
  check('修改题目', (await call('PUT', `/questions/${questionId}`, { ...goodQuestion, content: '临时题：1 + 2 等于几？' })).code === 0)
  const badOption = await call('POST', '/questions', { ...goodQuestion, answer: ['Z'] })
  check('答案不在选项内被拒', badOption.code !== 0, badOption.message)
  const badBlank = await call('POST', '/questions', {
    categoryId, qType: 4, content: '填空但没有下划线', answer: ['x'], difficulty: 2, score: 2,
  })
  check('填空题缺 ____ 被拒', badBlank.code !== 0, badBlank.message)

  const batch = await call('POST', '/questions/batch', {
    items: [
      { ...goodQuestion, content: '批量题一' },
      { ...goodQuestion, content: '批量题二' },
      { categoryId, qType: 1, content: '批量坏题', options: [{ key: 'A', text: 'x' }], answer: ['A'] },
    ],
  })
  check('批量录入：2 成功 1 失败且不整批回滚',
    batch.code === 0 && batch.data.saved === 2 && batch.data.failed === 1,
    `saved=${batch.data && batch.data.saved} failed=${batch.data && batch.data.failed}`)
  ;(batch.data?.createdIds || []).forEach((id) => created.questions.push(id))

  const template = await fetch(`${API}/questions/template`, { headers: { Authorization: `Bearer ${token}` } })
  const templateText = await template.text()
  check('导入模板可下载', template.ok && templateText.includes('【单选】'), `${templateText.length} 字符`)

  const form = new FormData()
  form.append('file', new Blob([
    '【判断】2 是偶数。\n答案：对\n解析：显然。\n\n【多选】下列哪些是质数？\nA. 2\nB. 4\nC. 5\n答案：A,C\n',
  ], { type: 'text/plain' }))
  const imported = await fetch(`${API}/questions/import?categoryId=${categoryId}`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form,
  }).then((r) => r.json())
  check('文本导入解析成功 2 题', imported.code === 0 && imported.data.saved === 2,
    JSON.stringify(imported.data || imported.message))
  ;(imported.data?.createdIds || []).forEach((id) => created.questions.push(id))
  // 批量与导入接口不回传 id，按临时分类兜底登记，保证脚本能自我清理
  const inTempCategory = await call('GET', `/questions?page=1&size=50&categoryId=${categoryId}`)
  inTempCategory.data.records.forEach((q) => created.questions.push(q.id))

  // ---------- 试卷 ----------
  const paperId = (await call('POST', '/papers', { title: '临时卷', categoryId, passScore: 3, suggestMinutes: 10 })).data
  created.papers.push(paperId)
  check('新建草稿试卷', !!paperId)
  check('加入题目', (await call('POST', `/papers/${paperId}/questions`, [{ questionId }])).code === 0)
  check('重复加题被拒', (await call('POST', `/papers/${paperId}/questions`, [{ questionId }])).code === 3015)
  const updated = await call('PUT', `/papers/${paperId}/questions/${questionId}`, { score: 5 })
  check('改单题分值', updated.code === 0, updated.message)
  const afterScore = await call('GET', `/papers/${paperId}`)
  check('总分随分值重算为 5', Number(afterScore.data.totalScore) === 5, `实际 ${afterScore.data.totalScore}`)
  check('发布试卷', (await call('PUT', `/papers/${paperId}/publish`)).code === 0)
  const emptyPaper = (await call('POST', '/papers', { title: '空卷', categoryId, passScore: 3, suggestMinutes: 10 })).data
  created.papers.push(emptyPaper)
  check('空卷不允许发布', (await call('PUT', `/papers/${emptyPaper}/publish`)).code === 3012)
  check('未发布的草稿卷不能归档', (await call('PUT', `/papers/${emptyPaper}/archive`)).code === 1003)
  check('被引用的题目删除被拒', (await call('DELETE', `/questions/${questionId}`)).code === 2012)

  // ---------- 考试 ----------
  const examId = (await call('POST', '/exams', {
    paperId, title: '临时考试',
    startTime: '2027-01-01T09:00:00', endTime: '2027-01-01T10:00:00',
    durationMinutes: 30, lateMinutes: 10, maxAttempts: 1, audienceType: 2, classNames: ['计算机2101'],
  })).data
  created.exams.push(examId)
  check('新建考试（含班级范围）', !!examId)
  const badTime = await call('POST', '/exams', {
    paperId, title: '时间倒挂', startTime: '2027-01-02T09:00:00', endTime: '2027-01-01T09:00:00',
    durationMinutes: 30, audienceType: 1,
  })
  check('结束早于开始被拒', badTime.code !== 0, badTime.message)
  check('编辑考试', (await call('PUT', `/exams/${examId}`, {
    paperId, title: '临时考试（改名）', startTime: '2027-01-01T09:00:00', endTime: '2027-01-01T10:30:00',
    durationMinutes: 45, lateMinutes: 10, maxAttempts: 2, audienceType: 2, classNames: ['计算机2102'],
  })).code === 0)
  const detail = await call('GET', `/exams/${examId}`)
  check('班级范围已更新', JSON.stringify(detail.data.classNames) === '["计算机2102"]', JSON.stringify(detail.data.classNames))
  check('发布考试', (await call('PUT', `/exams/${examId}/status`, { status: 1 })).code === 0)
  check('非法状态被拒', (await call('PUT', `/exams/${examId}/status`, { status: 7 })).code !== 0)
  check('强制收卷（0 份）', (await call('POST', `/exams/${examId}/force-submit`)).code === 0)
  check('成绩发布', (await call('POST', `/exams/${examId}/publish-score`)).code === 0)
  // 归档放在考试断言之后：被考试引用的试卷要留着判分，先删考试才能归档
  check('被考试引用的试卷不能归档', (await call('PUT', `/papers/${paperId}/archive`)).code === 1002)
  check('删除无答卷的考试', (await call('DELETE', `/exams/${examId}`)).code === 0)
  created.exams = created.exams.filter((x) => x !== examId)
  check('归档已发布试卷', (await call('PUT', `/papers/${paperId}/archive`)).code === 0)

  // ---------- 用户 ----------
  const userId = (await call('POST', '/users', {
    username: 'tmp0001', realName: '临时同学', className: '计算机2199', roles: ['STUDENT'],
  })).data
  created.users.push(userId)
  check('新建用户', !!userId)
  check('重名账号被拒', (await call('POST', '/users', { username: 'tmp0001', realName: '重复' })).code === 1012)
  check('禁用用户', (await call('PUT', `/users/${userId}/status`, { status: 0 })).code === 0)
  check('禁用后无法登录', (await call('POST', '/auth/login', { username: 'tmp0001', password: '123456' })).code === 1011)
  check('重置密码返回初始密码', (await call('POST', `/users/${userId}/reset-password`)).data === '123456')
  check('改角色为教师', (await call('POST', `/users/${userId}/roles`, { roles: ['TEACHER'] })).code === 0)
  const roles = await call('GET', `/users?page=1&size=50&roleCode=TEACHER`)
  check('角色筛选生效', roles.data.records.some((u) => u.id === userId))
  check('未知角色编码被拒', (await call('POST', `/users/${userId}/roles`, { roles: ['HACKER'] })).code !== 0)

  // ---------- 个人中心 ----------
  check('修改本人资料', (await call('PUT', '/auth/profile', { realName: '系统管理员', phone: '13900000001', email: 'admin@exam.edu' })).code === 0)
  const wrongOld = await call('PUT', '/auth/password', { oldPassword: 'wrongpass', newPassword: 'abc12345' })
  check('原密码错误被拒', wrongOld.code === 1013, wrongOld.message)
  check('改密（新→旧）', (await call('PUT', '/auth/password', { oldPassword: '123456', newPassword: 'abc12345' })).code === 0)
  const relogin = await call('POST', '/auth/login', { username: 'admin', password: 'abc12345' })
  check('新密码可登录', relogin.code === 0)
  // 换回原密码，避免影响演示
  const restore = await fetch(`${API}/auth/password`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${relogin.data.token}` },
    body: JSON.stringify({ oldPassword: 'abc12345', newPassword: '123456' }),
  }).then((r) => r.json())
  check('密码已换回 123456', restore.code === 0)
  check('新密码太短被拒', (await call('PUT', '/auth/password', { oldPassword: '123456', newPassword: '12' })).code === 1003)

  // ---------- 导出 ----------
  const csv = await fetch(`${API}/scores/export?examId=1`, { headers: { Authorization: `Bearer ${token}` } })
  const bytes = new Uint8Array(await csv.arrayBuffer())
  const hasBom = bytes[0] === 0xEF && bytes[1] === 0xBB && bytes[2] === 0xBF
  const csvText = new TextDecoder('utf-8').decode(bytes)
  check('成绩导出 CSV（带 BOM，Excel 中文不乱码）',
    csv.ok && hasBom && csvText.split('\n').length > 5, `${csvText.split('\n').length - 1} 行`)

  // ---------- 越权 ----------
  const studentToken = (await call('POST', '/auth/login', { username: '20230102', password: '123456' })).data.token
  const asStudent = async (method, path, body) => (await fetch(API + path, {
    method,
    headers: { Authorization: `Bearer ${studentToken}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })).json()
  check('学生不能建题', (await asStudent('POST', '/questions', goodQuestion)).code === 1002)
  check('学生不能看全班成绩', (await asStudent('GET', '/scores?page=1&size=5')).code === 1002)
  check('学生不能看日志', (await asStudent('GET', '/logs?page=1&size=5')).code === 1002)
  check('学生不能改别人资料', (await asStudent('PUT', '/users/1/status', { status: 0 })).code === 1002)
  const otherSheet = await asStudent('GET', '/exam/record/1/result')
  check('学生不能看别人的答卷', otherSheet.code === 1002, `code=${otherSheet.code}`)
  check('无令牌访问被拒',
    (await (await fetch(`${API}/users?page=1`)).json()).code === 1001)
} finally {
  await cleanup()
  const leftovers = await call('GET', '/questions?page=1&size=5&keyword=临时题')
  const treeAfter = await call('GET', '/categories/tree')
  check('清理后没有残留临时分类', !treeAfter.data.some((n) => n.name.startsWith('临时科目')))
  check('清理后没有残留临时题目', leftovers.data.total === 0, `剩 ${leftovers.data.total} 条`)
}

console.log(`\n通过 ${pass} 项，失败 ${failed} 项。`)
process.exit(failed === 0 ? 0 : 1)
