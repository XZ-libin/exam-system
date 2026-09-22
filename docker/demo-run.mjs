/**
 * 端到端流程验证 + 演示数据生成。
 * 全部通过 HTTP 接口完成，等价于「学生在浏览器里做一遍」：
 *   教师取答案键 -> 多名学生开考/保存/交卷 -> 系统判客观题 -> 教师阅主观题 -> 发布成绩 -> 学生查分 -> 统计校验
 *
 * 运行（与后端同网段的容器里执行）：
 *   docker run --rm --network exam-system_exam-net \
 *     -v "$PWD/docker:/work" -e API=http://backend:8082/api \
 *     node:24-alpine node /work/demo-run.mjs
 */
const API = process.env.API || 'http://localhost:8082/api';
const EXAM_ID = Number(process.env.EXAM_ID || 1);
const PAPER_ID = Number(process.env.PAPER_ID || 1);
const PASSWORD = process.env.PASSWORD || '123456';
const TEACHER = process.env.TEACHER || 'T2025001';
const students = (process.env.STUDENTS || '20230101,20230102,20230103,20230104,20230105,20230106,20230107')
  .split(',').map(s => s.trim()).filter(Boolean);

let step = 0;
const log = (...args) => console.log(`[${String(++step).padStart(2, '0')}]`, ...args);
const fail = (message) => {
  console.error('  ✗ ' + message);
  process.exit(1);
};

async function api(path, { method = 'GET', body, token } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  const response = await fetch(API + path, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  });
  let json;
  try {
    json = await response.json();
  } catch {
    return fail(`${method} ${path} 未返回 JSON（HTTP ${response.status}）`);
  }
  if (json.code !== 0) {
    return fail(`${method} ${path} -> code=${json.code} ${json.message}`);
  }
  return json.data;
}

/** 可复现的伪随机，保证每次生成的成绩分布一致 */
function makeRandom(seed) {
  let state = seed * 2654435761 % 4294967296;
  return () => {
    state = (state * 1103515245 + 12345) % 2147483648;
    return state / 2147483648;
  };
}

function answerFor(question, key, right) {
  const standard = key.get(question.paperQuestionId) || [];
  if (question.qType === 2) {
    if (right) return standard;
    return standard.slice(0, Math.max(1, standard.length - 1));
  }
  if (question.qType === 4) {
    return standard.map((text, index) => (right || index === 0 ? text : '不确定'));
  }
  if (question.qType === 5) {
    return [`第 ${question.sortNo} 题作答要点：${(standard[0] || '').slice(0, 60)}……（本人按课堂讲法展开）`];
  }
  if (right) return standard;
  const pool = (question.options || []).map(o => o.key).filter(k => !standard.includes(k));
  return [pool[0] || (standard[0] === 'T' ? 'F' : 'T')];
}

async function main() {
  log(`API = ${API}，exam = ${EXAM_ID}，学生 ${students.length} 名`);

  const teacherToken = (await api('/auth/login', { method: 'POST', body: { username: TEACHER, password: PASSWORD } })).token;
  log('教师登录成功');

  const health = await api('/health');
  log('后端与数据库：', JSON.stringify(health));

  const preview = await api(`/papers/${PAPER_ID}/preview`, { token: teacherToken });
  const key = new Map();
  for (const snapshot of preview.questions || []) {
    key.set(snapshot.id, JSON.parse(snapshot.answer || '[]'));
  }
  log(`答案键已取到：${preview.questions.length} 题，卷面总分 ${preview.totalScore}，及格线 ${preview.passScore}`);

  const submitted = [];
  for (const [index, username] of students.entries()) {
    const { token } = await api('/auth/login', { method: 'POST', body: { username, password: PASSWORD } });
    const paper = await api(`/exam/${EXAM_ID}/enter`, { method: 'POST', token });
    const random = makeRandom(index + 7);
    // 前几名学生答对率高，制造出分布与及格/不及格的对比
    const target = 0.5 + ((index * 13) % 45) / 100;
    const answers = paper.questions.map(question => ({
      paperQuestionId: question.paperQuestionId,
      answer: answerFor(question, key, random() < target),
    }));
    const half = Math.ceil(answers.length / 2);
    await api(`/exam/record/${paper.recordId}/answers`, { method: 'POST', token, body: { answers: answers.slice(0, half) } });
    await api(`/exam/record/${paper.recordId}/answers`, { method: 'POST', token, body: { answers: answers.slice(half) } });
    if (index === 1) {
      const switchInfo = await api(`/exam/record/${paper.recordId}/switch`, { method: 'POST', token });
      log(`${username} 切屏上报：${switchInfo.switchCount} 次，上限 ${switchInfo.limit}`);
    }
    const result = await api(`/exam/record/${paper.recordId}/submit`, { method: 'POST', token });
    submitted.push({ username, recordId: paper.recordId, needReview: result.needReview });
    log(`${username} 交卷：客观题 ${result.objectiveScore} 分，${result.needReview ? '简答题待阅卷' : '已出总分 ' + result.totalScore}`);
  }

  const pending = await api('/grading/pending?page=1&size=50', { token: teacherToken });
  log(`待阅卷答卷 ${pending.total} 份`);
  for (const row of pending.records) {
    const sheet = await api(`/grading/record/${row.recordId}`, { token: teacherToken });
    const items = sheet.items
      .filter(item => item.reviewStatus === 1)
      .map(item => ({
        answerItemId: item.answerItemId,
        score: Number(item.fullScore) * 0.8,
        comment: '要点基本齐全，表述可再精炼。',
      }));
    if (!items.length) continue;
    const graded = await api('/grading/submit', {
      method: 'POST', token: teacherToken, body: { recordId: row.recordId, items, finish: true },
    });
    log(`阅卷 record=${graded.recordId}：主观题 ${graded.subjectiveScore}，总分 ${graded.totalScore}，剩 ${graded.remainingCount} 题待阅`);
  }

  await api(`/exams/${EXAM_ID}/publish-score`, { method: 'POST', token: teacherToken });
  log('成绩已发布');

  const scores = await api(`/scores?page=1&size=30&examId=${EXAM_ID}`, { token: teacherToken });
  for (const row of scores.records) {
    log(`成绩单 ${row.username} ${row.studentName}：客观 ${row.objectiveScore} + 主观 ${row.subjectiveScore} = ${row.totalScore}（${row.statusName}）`);
  }

  const studentToken = (await api('/auth/login', { method: 'POST', body: { username: students[0], password: PASSWORD } })).token;
  const myScores = await api('/scores/my?page=1&size=10', { token: studentToken });
  const analysis = await api('/stats/my/analysis', { token: studentToken });
  log(`学生端：${myScores.total} 条成绩记录，趋势 ${analysis.trend.length} 点，错题 ${analysis.wrongCount} 道`);
  if (!analysis.trend.length) fail('学生趋势为空，成绩明细链路有问题');

  const examStat = await api(`/stats/exam/${EXAM_ID}`, { token: teacherToken });
  log(`统计：应交 ${examStat.expectedCount} 实交 ${examStat.submitCount}，平均 ${examStat.avgScore}，及格率 ${examStat.passRate}%`);
  log('  分数段：' + (examStat.scoreSections || []).map(s => `${s.label}:${s.count}`).join('  '));
  const worst = [...(examStat.questionStats || [])].sort((a, b) => a.accuracy - b.accuracy)[0];
  if (worst) log(`  最难题目 #${worst.questionId} 正确率 ${worst.accuracy}%：${worst.brief}`);

  const overview = await api('/stats/overview', { token: teacherToken });
  log(`看板：考试 ${overview.examCount} 场 / 试卷 ${overview.paperCount} 套 / 题目 ${overview.questionCount} 道 / 已交 ${overview.attemptCount} 份`);

  console.log('\n完整链路通过：登录 → 答案键 → 开考 → 自动保存 → 交卷判分 → 阅卷 → 发布 → 学生查分 → 统计。');
}

main().catch(error => fail(error.stack || String(error)));
