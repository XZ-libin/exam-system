/**
 * 试卷模块专项验证：规则抽题、分值校验、发布锁定、题快照不可变、题量不足报错。
 *   docker run --rm --network exam-system_exam-net \
 *     -v "$PWD/docker:/work:ro" -e API=http://backend:8082/api node:24-alpine node /work/paper-flow-check.mjs
 */
const API = process.env.API || 'http://localhost:8082/api';
const token = (await (await fetch(`${API}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: process.env.ACCOUNT || 'T2025001', password: '123456' }),
})).json()).data.token;

async function call(method, path, body) {
  const response = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = await response.json()
  return { http: response.status, ...json }
}

let pass = 0
let failed = 0
function check(label, ok, detail = '') {
  if (ok) {
    pass++
    console.log(`✓ ${label}${detail ? ' — ' + detail : ''}`)
  } else {
    failed++
    console.log(`✗ ${label}${detail ? ' — ' + detail : ''}`)
  }
}

// 1 规则组卷：按题型 + 难度抽题
const auto = await call('POST', '/papers/auto-generate', {
  title: '临时规则卷（自动抽题）',
  description: '接口验证用',
  categoryId: 1,
  passScore: 20,
  suggestMinutes: 20,
  rules: [
    { qType: 1, count: 3, difficulty: 2, scorePerItem: 4 },
    { qType: 2, count: 2, scorePerItem: 6 },
  ],
})
check('规则组卷成功', auto.code === 0, JSON.stringify(auto.message || ''))
const autoId = auto.data

let detail = await call('GET', `/papers/${autoId}`)
const types = detail.data.questions.map((q) => q.qType)
check('抽出 5 题', detail.data.questionCount === 5, `接口返回 ${detail.data.questionCount} 题`)
check('总分 = 各题分值之和', Number(detail.data.totalScore) === 3 * 4 + 2 * 6, `总分 ${detail.data.totalScore}`)
check('题型数量符合规则',
  types.filter((t) => t === 1).length === 3 && types.filter((t) => t === 2).length === 2)
check('人工组卷标记为规则抽题', detail.data.buildType === 2)

// 2 题量不足应整体失败并提示可用题数
const notEnough = await call('POST', '/papers/auto-generate', {
  title: '不可能凑齐的卷子',
  categoryId: 1,
  rules: [{ qType: 5, count: 999, scorePerItem: 10 }],
})
check('题量不足被拒绝', notEnough.code !== 0, `code=${notEnough.code} ${notEnough.message}`)

// 3 快照不可变：改题库题干，已入卷的题不受影响
const manual = (await call('POST', '/papers', {
  title: '临时人工卷（快照验证）',
  categoryId: 1,
  passScore: 10,
  suggestMinutes: 15,
})).data
await call('POST', `/papers/${manual}/questions`, [{ questionId: 1 }])
const before = (await call('GET', `/papers/${manual}`)).data.questions[0]
const bank = (await call('GET', '/questions/1')).data
await call('PUT', '/questions/1', { ...bank, content: bank.content + '（题库已改动）' })
const after = (await call('GET', `/papers/${manual}`)).data.questions[0]
check('题库改动不影响试卷快照', before.content === after.content)
check('题库本身确实变了', (await call('GET', '/questions/1')).data.content.includes('题库已改动'))
await call('PUT', '/questions/1', bank)

// 4 发布后锁定
const published = await call('PUT', `/papers/${manual}/publish`)
check('空分值校验下允许发布', published.code === 0, published.message || '')
const locked = await call('POST', `/papers/${manual}/questions`, [{ questionId: 2 }])
check('已发布试卷禁止再加题', locked.code !== 0, `code=${locked.code} ${locked.message}`)

// 5 题库引用计数
const usedQuestion = await call('GET', '/questions/1')
check('入卷后 use_count 回写', Number(usedQuestion.data.useCount) >= 1, `useCount=${usedQuestion.data.useCount}`)
const deleteUsed = await call('DELETE', '/questions/1')
check('被引用的题目不允许删除', deleteUsed.code !== 0, `code=${deleteUsed.code} ${deleteUsed.message}`)

// 清理临时数据
await call('DELETE', `/papers/${autoId}`)
await call('DELETE', `/papers/${manual}`).then(async () => {
  const archived = await call('PUT', `/papers/${manual}/archive`)
  if (archived.code !== 0) console.log('  （临时卷已删除，无需归档）')
})

console.log(`\n通过 ${pass} 项，失败 ${failed} 项。`)
process.exit(failed === 0 ? 0 : 1)
