/**
 * 接口自检：用管理员/教师令牌逐个打关键读接口，确认没有 5xx、字段不缺失。
 * 运行：
 *   docker run --rm --network exam-system_exam-net \
 *     -v "$PWD/docker:/work:ro" -e API=http://backend:8082/api node:24-alpine node /work/smoke-check.mjs
 */
const API = process.env.API || 'http://localhost:8082/api';

const checks = [
  ['GET', '/health', null],
  ['GET', '/categories/tree', '树节点'],
  ['GET', '/questions?page=1&size=5&qType=1', '分页'],
  ['GET', '/questions?page=1&size=5&difficulty=4', '按难度筛选'],
  ['GET', '/questions/1', '题目详情'],
  ['GET', '/papers?page=1&size=5', '试卷分页'],
  ['GET', '/papers/1', '试卷详情（含快照题）'],
  ['GET', '/papers/1/preview', '试卷预览'],
  ['GET', '/exams?page=1&size=5', '考试分页'],
  ['GET', '/exams/1', '考试详情'],
  ['GET', '/users?page=1&size=5', '用户分页'],
  ['GET', '/users/options', '角色与班级'],
  ['GET', '/scores?page=1&size=5', '成绩分页'],
  ['GET', '/scores/my?page=1&size=5', '我的成绩（教师调用会返回本人数据）'],
  ['GET', '/exam/my-exams', '我的考试列表'],
  ['GET', '/grading/pending?page=1&size=5', '待阅卷列表'],
  ['GET', '/grading/pending-count', '待阅卷份数'],
  ['GET', '/stats/overview', '统计看板'],
  ['GET', '/stats/exam/1', '单场统计'],
  ['GET', '/stats/question/20', '单题统计'],
  ['GET', '/stats/my/analysis', '学生分析'],
  ['GET', '/logs?page=1&size=5', '操作日志']
];

function brief(data) {
  if (Array.isArray(data)) return `数组 ${data.length} 项`
  if (data && typeof data === 'object') {
    if (Array.isArray(data.records)) return `分页 total=${data.total} 本页 ${data.records.length} 行`
    const keys = Object.keys(data)
    return `对象 ${keys.length} 键：${keys.slice(0, 6).join(', ')}${keys.length > 6 ? ' …' : ''}`
  }
  return typeof data
}

async function main() {
  const login = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: process.env.ACCOUNT || 'admin', password: process.env.PASSWORD || '123456' }),
  }).then((r) => r.json())
  if (login.code !== 0) {
    console.error('登录失败：', login.message)
    process.exit(1)
  }
  const token = login.data.token
  console.log(`以 ${process.env.ACCOUNT || 'admin'} 身份检查 ${checks.length} 个接口\n`)

  let bad = 0
  for (const [method, path, label] of checks) {
    const response = await fetch(API + path, { headers: { Authorization: `Bearer ${token}` } })
    let body
    try {
      body = await response.json()
    } catch {
      console.log(`✗ ${method} ${path} HTTP ${response.status} 非 JSON`)
      bad++
      continue
    }
    if (body.code === 0) {
      console.log(`✓ ${method} ${path}  — ${label}：${brief(body.data)}`)
    } else {
      console.log(`! ${method} ${path}  — code=${body.code} ${body.message}`)
      if (response.status >= 500) bad++
    }
  }
  console.log(bad === 0 ? '\n全部接口可用。' : `\n有 ${bad} 个接口返回 5xx，需要修。`)
  process.exit(bad === 0 ? 0 : 1)
}

main().catch((error) => {
  console.error('自检脚本异常：', error)
  process.exit(1)
})
