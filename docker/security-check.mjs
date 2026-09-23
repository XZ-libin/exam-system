/**
 * 安全测试：认证、越权、注入、存储型 XSS、配置与传输、防作弊边界。
 *   docker run --rm --network exam-system_exam-net -e TZ=Asia/Shanghai \
 *     -v "$PWD/docker:/work:ro" -e API=http://backend:8082/api node:24-alpine node /work/security-check.mjs
 *
 * 结论分两类：FAIL = 存在漏洞且应修；FINDING = 风险项（课程项目可接受，但要在文档里写明）。
 */
import crypto from 'node:crypto'

const API = process.env.API || 'http://localhost:8082/api';
const SECRET = 'exam-system-graduation-project-secret-key-please-change-2026'  // application.yml 默认值
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url')

function sign(payload, secret = SECRET, alg = 'HS256') {
  const head = b64({ alg, typ: 'JWT' })
  const body = b64(payload)
  if (alg === 'none') return `${head}.${body}.`
  const sig = crypto.createHmac('sha256', secret).update(`${head}.${body}`).digest('base64url')
  return `${head}.${body}.${sig}`
}

async function req(method, path, { token, body, headers = {} } = {}) {
  const started = Date.now()
  const r = await fetch(API + path, {
    method,
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await r.text()
  let json = null
  try { json = JSON.parse(text) } catch { /* 非 JSON 响应 */ }
  return { http: r.status, code: json?.code, message: json?.message, data: json?.data, text, ms: Date.now() - started, headers: Object.fromEntries(r.headers.entries()) }
}
const login = async (u, p = '123456') => (await req('POST', '/auth/login', { body: { username: u, password: p } })).data?.token

let fail = 0
let finding = 0
let ok = 0
function check(title, passed, detail, severity = 'FAIL') {
  if (passed) { ok++; console.log(` ✓ ${title}`) }
  else if (severity === 'FINDING') { finding++; console.log(` ⚠ FINDING ${title}\n     ${detail}`) }
  else { fail++; console.log(` ✗ FAIL ${title}\n     ${detail}`) }
}

async function main() {
  const admin = await login('admin')
  const teacher = await login('T2025001')
  const stuA = await login('20230101')
  const stuB = await login('20230102')
  check('环境就绪：可登录四种身份', !!(admin && teacher && stuA && stuB), '登录失败')

  // ==================== S1 认证 ====================
  console.log('\n──── S1 认证与会话 ────')
  const noToken = await req('GET', '/users?page=1')
  check('无令牌访问受保护接口被拒', noToken.code === 1001 && noToken.http === 401, `code=${noToken.code} http=${noToken.http}`)
  const forged = await req('GET', '/logs?page=1', { token: sign({ sub: '4', username: 'x', realName: 'x', className: 'c', roles: ['ADMIN'] }) })
  check('伪造 ADMIN 令牌不能越权（角色回库校验）', forged.code === 1002 || forged.code === 1001, `code=${forged.code} ${forged.message}`)
  const wrongSecret = await req('GET', '/auth/me', { token: sign({ sub: '1', roles: ['ADMIN'] }, 'another-secret-value-1234567890abcd') })
  check('错误密钥签发的令牌被拒', wrongSecret.code === 1001, `code=${wrongSecret.code}`)
  const noneAlg = await req('GET', '/auth/me', { token: sign({ sub: '1', roles: ['ADMIN'] }, '', 'none') })
  check('alg=none 令牌被拒', noneAlg.code === 1001, `code=${noneAlg.code} http=${noneAlg.http}`)
  const expired = await req('GET', '/auth/me', { token: sign({ sub: '1', roles: ['ADMIN'], exp: Math.floor(Date.now() / 1000) - 3600 }) })
  check('过期令牌被拒', expired.code === 1001, `code=${expired.code}`)
  const garbage = await req('GET', '/auth/me', { token: 'abc.def.ghi' })
  check('畸形令牌不报 500', garbage.code === 1001 && garbage.http === 401, `code=${garbage.code} http=${garbage.http}`)
  const loginLeak = await req('POST', '/auth/login', { body: { username: 'admin', password: 'wrong-password' } })
  check('登录失败响应不含密码哈希等敏感字段', !/password|salt|hash|\$2a\$/i.test(loginLeak.text), loginLeak.text.slice(0, 120))
  const badPwd = await req('POST', '/auth/login', { body: { username: 'admin', password: '123456' } })
  check('弱口令 admin/123456 可登录（默认口令风险）', badPwd.code !== 0, '登录成功', 'FINDING')

  // ==================== S2 越权 ====================
  console.log('\n──── S2 越权与访问控制 ────')
  const studentEndpoints = [
    ['GET', '/users?page=1'], ['POST', '/questions', { categoryId: 3, qType: 5, content: 'x', answer: ['y'] }],
    ['GET', '/logs?page=1'], ['GET', '/scores?page=1'], ['GET', '/stats/overview'],
    ['GET', '/papers/1'], ['GET', '/questions/1'], ['GET', '/grading/pending'],
    ['POST', '/papers', { title: 'x' }], ['POST', '/exams', { paperId: 1, title: 'x' }],
    ['PUT', '/users/1/status', { status: 0 }], ['DELETE', '/questions/1'],
  ]
  for (const [method, path, body] of studentEndpoints) {
    const r = await req(method, path, { token: stuA, body })
    check(`学生调用 ${method} ${path} 被拒`, r.code === 1002, `code=${r.code} ${r.message}`)
  }
  const teacherAdmin = await req('GET', '/logs?page=1', { token: teacher })
  check('教师调用管理员专属 /logs 被拒', teacherAdmin.code === 1002, `code=${teacherAdmin.code}`)
  const teacherCreateUser = await req('POST', '/users', { token: teacher, body: { username: 'sec-x', realName: 'x' } })
  check('教师不能建账号', teacherCreateUser.code === 1002, `code=${teacherCreateUser.code}`)
  const crossRecord = await req('GET', '/exam/record/1/result', { token: stuB })
  check('学生读他人答卷被拒（record 1 属于 20230101）', crossRecord.code === 1002, `code=${crossRecord.code}`)
  const crossUser = await req('GET', '/users/1', { token: stuA })
  check('学生读他人账号详情被拒', crossUser.code === 1002, `code=${crossUser.code}`)
  const ownRecord = await req('GET', '/exam/record/1/result', { token: stuA })
  check('学生读自己的答卷允许', ownRecord.code === 0, `code=${ownRecord.code} ${ownRecord.message}`)

  const swagger = await fetch(`${API.replace('/api', '')}/swagger-ui.html`, { redirect: 'manual' })
  const apiDocs = await fetch(`${API.replace('/api', '')}/v3/api-docs`)
  check('匿名无法访问 Swagger / 接口清单', apiDocs.status !== 200 && swagger.status !== 200,
    `swagger-ui=${swagger.status} /v3/api-docs=${apiDocs.status}，文档里含 ${apiDocs.ok ? (await apiDocs.text()).length : 0} 字节接口定义`, 'FINDING')
  const health = await req('GET', '/health')
  check('匿名 /api/health 不泄露内部信息', !/\d+ tables/i.test(String(health.data?.database)), JSON.stringify(health.data))
  const teacherPii = await req('GET', '/users?page=1&size=5', { token: teacher })
  const exposed = teacherPii.code === 0 && (teacherPii.data?.records ?? []).some((r) => r.phone || r.email)
  check('教师批量读取学生手机号/邮箱已脱敏', !exposed,
    `返回 ${teacherPii.data?.records?.length} 行，明文联系方式 ${exposed ? '存在' : '0'} 条`, 'FINDING')

  // ==================== S3 注入 ====================
  console.log('\n──── S3 注入 ────')
  const sqli = [
    "' OR '1'='1", "1'--", "'; DROP TABLE sys_user; --", "1 UNION SELECT null,null--", "%", "_", "\\", "' AND SLEEP(3) AND '1'='1",
  ]
  for (const payload of sqli) {
    const r = await req(`GET`, `/users?page=1&size=5&keyword=${encodeURIComponent(payload)}`, { token: admin })
    const slow = r.ms > 2500
    const dumped = r.code === 0 && r.data?.total > 16
    check(`关键字注入/通配不奏效：${JSON.stringify(payload).slice(0, 26)}`,
      !slow && !dumped && r.code !== 1500, `耗时 ${r.ms}ms，total=${r.data?.total ?? r.code}`)
  }
  const numInject = await req('GET', '/questions?page=1&size=5&categoryId=' + encodeURIComponent('1 OR 1=1'), { token: teacher })
  check('数字参数注入不报 500 且不越权', numInject.code === 1003 || numInject.code === 1004 || numInject.code === 0 && numInject.data.total === 0,
    `code=${numInject.code} ${numInject.message}`)
  const pathInject = await req('GET', '/questions/' + encodeURIComponent('1;DROP'), { token: teacher })
  check('路径参数类型错误返回参数错误而非 500', pathInject.code === 1003, `code=${pathInject.code}`)
  const afterSqli = await req('GET', '/users?page=1&size=1', { token: admin })
  check('注入尝试后数据完好（用户表可正常读取）', afterSqli.code === 0 && afterSqli.data.total >= 16, `total=${afterSqli.data?.total}`)

  // ==================== S4 存储型 XSS ====================
  console.log('\n──── S4 存储型 XSS ────')
  const xss = '<img src=x onerror=alert(1)><script>fetch("//evil")</script>'
  const catId = (await req('POST', '/categories', { token: teacher, body: { parentId: 0, name: `sec-${Date.now()}` } })).data
  const qid = (await req('POST', '/questions', {
    token: teacher, body: { categoryId: catId, qType: 5, content: `sec-xss ${xss}`, answer: [xss], analysis: xss, difficulty: 1, score: 2 },
  })).data
  const readBack = await req('GET', `/questions/${qid}`, { token: teacher })
  check('XSS 载荷以原文存取（前端为文本插值，不执行脚本）',
    readBack.data.content.includes('onerror'), '未原样返回', 'FINDING')
  const profileXss = await req('PUT', '/auth/profile', { token: admin, body: { realName: '系统管理员', phone: '', email: '', avatar: 'javascript:alert(1)' } })
  check('avatar 字段拒绝 javascript: 协议', profileXss.code !== 0, `javascript: 仍被接受 code=${profileXss.code}`)

  // ==================== S5 配置与传输 ====================
  console.log('\n──── S5 配置、头部与传输 ────')
  const WEB = process.env.WEB || 'http://web'
  const page = await fetch(WEB + '/')
  const h = Object.fromEntries(page.headers.entries())
  const missing = ['content-security-policy', 'x-frame-options', 'x-content-type-options', 'referrer-policy'].filter((k) => !(k in h))
  check('nginx 已配置基础安全响应头', missing.length === 0, `缺少：${missing.join(', ')}`, 'FINDING')
  const server = h['server'] || ''
  check('响应头未泄露后端组件版本', !/nginx\/\d|tomcat\/\d|spring/i.test(server), `Server: ${server || '（未返回）'}`)
  const corsPreflight = await fetch(`${API}/users?page=1`, { method: 'OPTIONS', headers: { Origin: 'http://evil.example', 'Access-Control-Request-Method': 'GET' } })
  const acao = corsPreflight.headers.get('access-control-allow-origin')
  check('CORS 未对所有来源开放携带凭证', acao !== 'http://evil.example' || corsPreflight.headers.get('access-control-allow-credentials') !== 'true',
    `Allow-Origin=${acao} credentials=${corsPreflight.headers.get('access-control-allow-credentials')}`, 'FINDING')
  const errLeak = await req('POST', '/users', { token: admin, body: { username: 'a'.repeat(200), realName: 'x' } })
  check('异常响应不回显 SQL/堆栈', !/sql|exception|at com\.|nested exception/i.test(errLeak.text), errLeak.text.slice(0, 160))
  const actuator = await fetch(`${API.replace('/api', '')}/actuator/env`)
  check('未暴露 Spring Actuator', actuator.status === 404 || actuator.status === 401, `status=${actuator.status}`)

  // ==================== S6 防作弊与业务边界 ====================
  console.log('\n──── S6 防作弊与成绩可见性 ────')
  const stuRecord = (await req('GET', '/scores/my?page=1&size=5', { token: stuA })).data.records[0]
  if (stuRecord) {
    const sheet = await req('GET', `/exam/record/${stuRecord.recordId}/result`, { token: stuA })
    const published = sheet.data.scorePublished === 1
    const leaks = !published && (sheet.data.totalScore !== null || sheet.data.items.some((i) => i.correctFlag !== null || i.standardAnswer))
    check('成绩未发布时不泄露总分/答案/对错', !leaks,
      `scorePublished=${sheet.data.scorePublished} total=${sheet.data.totalScore} 首题 correctFlag=${sheet.data.items[0]?.correctFlag}`)
  }
  const answerBank = await req('GET', '/questions?page=1&size=3', { token: stuA })
  check('学生无法批量拖库（题库列表被拒）', answerBank.code === 1002, `code=${answerBank.code}`)
  const paperKey = await req('GET', '/papers/1/preview', { token: stuA })
  check('学生无法取试卷答案键', paperKey.code === 1002, `code=${paperKey.code}`)
  const beforeBatch = await req('GET', `/questions?page=1&size=1&categoryId=${catId}`, { token: teacher })
  const huge = await req('POST', '/questions/batch', {
    token: teacher, body: { items: Array.from({ length: 600 }, (_, i) => ({ categoryId: catId, qType: 5, content: `sec-批量 ${i}`, answer: ['x'] })) },
  })
  const afterBatch = await req('GET', `/questions?page=1&size=1&categoryId=${catId}`, { token: teacher })
  const bigText = Array.from({ length: 400 }, (_, i) => `【判断】sec-导入 ${i}\n答案：对`).join('\n\n')
  const form = new FormData()
  form.append('file', new Blob([bigText], { type: 'text/plain' }), 'sec-import.txt')
  form.append('categoryId', String(catId))
  const hugeImport = await (async () => {
    const started = Date.now()
    const r = await fetch(`${API}/questions/import`, { method: 'POST', headers: { Authorization: `Bearer ${teacher}` }, body: form })
    const text = await r.text()
    let json = null
    try { json = JSON.parse(text) } catch { /* 非 JSON 响应 */ }
    return { http: r.status, code: json?.code, message: json?.message, ms: Date.now() - started, text }
  })()
  const afterImport = await req('GET', `/questions?page=1&size=1&categoryId=${catId}`, { token: teacher })
  const rejected = (r) => r.code !== 0 && r.code !== 1500
  const noGrowth = (a, b) => (a.data?.total ?? -1) === (b.data?.total ?? -2)
  check('一次提交 600 题被条数上限拒绝且不落库',
    rejected(huge) && noGrowth(afterBatch, beforeBatch),
    `code=${huge.code} ${huge.message}，该分类 ${beforeBatch.data?.total} → ${afterBatch.data?.total} 条，耗时 ${huge.ms}ms`)
  check('一次导入 400 题同样被上限拒绝',
    rejected(hugeImport) && noGrowth(afterImport, beforeBatch),
    `code=${hugeImport.code} ${hugeImport.message}，该分类仍为 ${afterImport.data?.total} 条`, 'FINDING')
  const stillUp = await req('GET', '/health')
  check('大量请求后服务仍可用', stillUp.code === 0, `code=${stillUp.code}`)

  // ==================== 清理 ====================
  const leftovers = await req('GET', `/questions?page=1&size=200&categoryId=${catId}`, { token: teacher })
  for (const row of leftovers.data?.records ?? []) {
    await req('DELETE', `/questions/${row.id}`, { token: teacher })
  }
  await req('DELETE', `/categories/${catId}`, { token: teacher })
  await req('PUT', '/auth/profile', { token: admin, body: { realName: '系统管理员', phone: '13900000001', email: 'admin@exam.edu', avatar: null } })

  console.log(`\n══════════ 安全测试汇总 ══════════`)
  console.log(`通过 ${ok} 项，FAIL ${fail} 项，FINDING（风险项）${finding} 项`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch((e) => { console.error('脚本异常：', e); process.exit(2) })
