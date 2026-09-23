<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { attemptApi } from '../../api'

const route = useRoute()
const router = useRouter()

const examId = Number(route.params.examId)

const loading = ref(true)
const enterFailed = ref(false)
const paper = ref(null)
const recordId = ref(null)
const questions = ref([])
const answers = reactive({})
const current = ref(0)
const remaining = ref(0)
const switchCount = ref(0)
const switchLimit = ref(0)
const resumed = ref(false)
const saveState = ref('idle')
const blocked = ref(false)
const submitting = ref(false)

let secondsTimer = null
let fullTimer = null
let debounceTimer = null
let inFlight = false
const changed = new Set()
const SEP = String.fromCharCode(1)

const question = computed(() => questions.value[current.value] || {})
const answeredCount = computed(() => questions.value.filter(isAnswered).length)
const unansweredCount = computed(() => questions.value.length - answeredCount.value)
const clock = computed(() => formatClock(remaining.value))
const clockTight = computed(() => remaining.value > 0 && remaining.value <= 300)

function formatClock(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0))
  const mm = String(Math.floor(total / 60)).padStart(2, '0')
  const ss = String(total % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

/** 请求层已经提示过原因：普通 Error 来自后端业务码，axios 错误才是网络问题 */
function isBizError(err) {
  return !!err && err.isAxiosError !== true
}

function valueOf(q) {
  return answers[q.paperQuestionId] || []
}

function keyOf(value) {
  return (value || []).map((item) => (item == null ? '' : String(item).trim())).join(SEP)
}

function blankTotal(q) {
  return Math.max(1, Number(q.blankCount) || 1)
}

function isAnswered(q) {
  return valueOf(q).some((item) => item != null && String(item).trim() !== '')
}

function singleValue(q) {
  const first = valueOf(q)[0]
  return first == null ? '' : String(first)
}

function blankValue(q, index) {
  const item = valueOf(q)[index]
  return item == null ? '' : String(item)
}

function optionList(q) {
  if (q.qType === 3) {
    return [{ key: 'T', text: '正确' }, { key: 'F', text: '错误' }]
  }
  return q.options || []
}

function writeAnswer(q, value) {
  answers[q.paperQuestionId] = value
  changed.add(q.paperQuestionId)
  scheduleSave()
}

function setSingle(q, key) {
  if (key) writeAnswer(q, [key])
}

function setMulti(q, keys) {
  writeAnswer(q, [...keys].sort())
}

function setBlank(q, index, text) {
  const list = valueOf(q).slice()
  while (list.length < blankTotal(q)) list.push('')
  list[index] = text
  writeAnswer(q, list)
}

function clearAnswer(q) {
  writeAnswer(q, q.qType === 4 ? new Array(blankTotal(q)).fill('') : [])
}

function restore(q, saved) {
  const list = Array.isArray(saved) ? saved.map((item) => (item == null ? '' : String(item))) : []
  if (q.qType === 4) {
    const blanks = []
    for (let i = 0; i < blankTotal(q); i++) blanks.push(list[i] || '')
    return blanks
  }
  if (q.qType === 2) return list.slice().sort()
  return list.length ? [list[0]] : []
}

function allIds() {
  return questions.value.map((q) => q.paperQuestionId)
}

async function sendSave(ids) {
  const targets = [...new Set(ids)]
  if (!targets.length || !recordId.value) return
  const snapshot = targets.map((id) => [id, keyOf(answers[id])])
  const res = await attemptApi.save(recordId.value, targets.map((id) => ({
    paperQuestionId: id,
    answer: (answers[id] || []).map((item) => (item == null ? '' : String(item)))
  })))
  if (res && res.remainingSeconds != null) {
    remaining.value = Math.max(0, Number(res.remainingSeconds))
  }
  if (res && res.examClosed) {
    // 教师中途结束考试：后端已把这份卷交掉，这里停止计时并跳到结果页
    blocked.value = true
    stopTimers()
    ElMessage.warning(res.message || '本场考试已结束，作答已自动提交')
    goResult()
    return
  }
  snapshot.forEach(([id, key]) => {
    if (keyOf(answers[id]) === key) changed.delete(id)
  })
}

async function persist(ids) {
  if (!recordId.value || blocked.value || submitting.value) return
  if (inFlight) {
    // 上一次保存请求还在飞：排一次稍后的重试，别把最后一次编辑静默丢掉
    window.clearTimeout(debounceTimer)
    debounceTimer = window.setTimeout(() => persist(allIds()), 900)
    return
  }
  inFlight = true
  saveState.value = 'saving'
  try {
    await sendSave(ids)
    saveState.value = 'idle'
  } catch (err) {
    saveState.value = 'retry'
    if (isBizError(err) && !submitting.value) {
      blocked.value = true
      stopTimers()
    }
  } finally {
    inFlight = false
  }
}

function scheduleSave() {
  if (blocked.value || submitting.value) return
  window.clearTimeout(debounceTimer)
  debounceTimer = window.setTimeout(() => {
    if (changed.size) persist([...changed])
  }, 600)
}

function flushNow() {
  window.clearTimeout(debounceTimer)
  if (changed.size) persist([...changed])
}

function tick() {
  if (blocked.value || submitting.value) return
  if (remaining.value <= 0) {
    finish()
    return
  }
  remaining.value -= 1
  if (remaining.value === 0) finish()
}

function startTimers() {
  stopTimers()
  secondsTimer = window.setInterval(tick, 1000)
  fullTimer = window.setInterval(() => {
    if (!blocked.value && !submitting.value) persist(allIds())
  }, 30000)
}

function stopTimers() {
  if (secondsTimer) {
    window.clearInterval(secondsTimer)
    secondsTimer = null
  }
  if (fullTimer) {
    window.clearInterval(fullTimer)
    fullTimer = null
  }
  window.clearTimeout(debounceTimer)
}

function goResult() {
  if (!recordId.value) {
    router.replace('/student/exams')
    return
  }
  router.replace({ name: 'studentResult', params: { recordId: recordId.value } })
}

async function finish() {
  if (submitting.value || !recordId.value) return
  submitting.value = true
  stopTimers()
  try {
    await sendSave(allIds())
    saveState.value = 'idle'
  } catch {
    saveState.value = 'retry'
  }
  try {
    await attemptApi.submit(recordId.value)
  } catch {
    /* 已交卷或已被强制收卷，成绩页给出真实状态 */
  }
  goResult()
}

async function submitManually() {
  flushNow()
  const left = unansweredCount.value
  const text = left
    ? `还有 ${left} 题未作答。交卷后不能再修改答案。`
    : '全部题目都已作答。交卷后不能再修改答案。'
  try {
    await ElMessageBox.confirm(text, '确认交卷', {
      confirmButtonText: '交卷',
      cancelButtonText: '再检查一下',
      type: 'warning'
    })
  } catch {
    return
  }
  await finish()
}

function go(step) {
  const next = Math.min(Math.max(step, 0), questions.value.length - 1)
  if (next === current.value) return
  flushNow()
  current.value = next
}

async function reportSwitch() {
  if (!recordId.value || blocked.value || submitting.value) return
  try {
    const res = await attemptApi.reportSwitch(recordId.value)
    if (res && res.switchCount != null) switchCount.value = Number(res.switchCount)
    if (res && res.forced) {
      // 后端已在这一步真的把卷子交了，这里只负责提示与跳转
      blocked.value = true
      stopTimers()
      ElMessage.warning(res.message || '离开答题页次数达到上限，系统已强制交卷')
      goResult()
    }
  } catch (err) {
    if (isBizError(err)) {
      blocked.value = true
      stopTimers()
      goResult()
    }
  }
}

function flushOnLeave() {
  if (!blocked.value && !submitting.value && changed.size) persist(allIds())
}

// 只有「学生真的看过这页」之后离开才算切屏：后台标签页打开、页面还没渲染完就
// 收到 visibilitychange 的情况不该记一次，否则会白白把答卷交掉。
let hadVisible = document.visibilityState === 'visible'

function onVisibility() {
  if (document.hidden) {
    flushNow()
    if (!hadVisible) return
    hadVisible = false
    reportSwitch()
    return
  }
  hadVisible = true
}

onMounted(async () => {
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('pagehide', flushOnLeave)
  try {
    const data = await attemptApi.enter(examId)
    if (!data || !Array.isArray(data.questions) || !data.questions.length) {
      enterFailed.value = true
      return
    }
    paper.value = data
    recordId.value = data.recordId
    questions.value = data.questions
    data.questions.forEach((q) => {
      answers[q.paperQuestionId] = restore(q, data.answers ? data.answers[q.paperQuestionId] : null)
    })
    remaining.value = Math.max(0, Math.floor(Number(data.remainingSeconds) || 0))
    switchCount.value = Number(data.switchCount) || 0
    switchLimit.value = Number(data.switchLimit) || 0
    resumed.value = data.resumed === true
    if (data.submitted === true) {
      blocked.value = true
      goResult()
      return
    }
    startTimers()
    if (remaining.value <= 0) finish()
  } catch {
    enterFailed.value = true
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  stopTimers()
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('pagehide', flushOnLeave)
})
</script>

<template>
  <div class="take">
    <header class="take-bar">
      <div class="take-bar-main">
        <h1 class="take-title">{{ paper ? paper.examTitle : '在线答题' }}</h1>
        <span v-if="paper" class="take-meta num">
          第 {{ paper.attemptNo }} 次作答 · 共 {{ questions.length }} 题 · 满分 {{ paper.totalScore == null ? '--' : paper.totalScore }} 分
        </span>
      </div>
      <div class="take-bar-side">
        <template v-if="paper && !loading && !enterFailed">
          <span class="take-clock num" :class="{ tight: clockTight }">剩余 {{ clock }}</span>
          <span class="take-left">未答 <b class="num">{{ unansweredCount }}</b> 题</span>
          <span v-if="saveState === 'retry'" class="tag tag-warn">暂未存上，稍后自动重试</span>
          <el-button v-if="!blocked" type="primary" :loading="submitting" @click="submitManually">交卷</el-button>
        </template>
      </div>
    </header>

    <main class="take-body">
      <section v-if="loading" class="card take-state">
        <el-skeleton :rows="5" animated />
      </section>

      <section v-else-if="enterFailed" class="card take-state">
        <h2 class="card-title">没能进入这场考试</h2>
        <p class="card-hint">返回考试列表查看当前状态，再决定下一步。</p>
        <el-button type="primary" @click="router.replace('/student/exams')">返回我的考试</el-button>
      </section>

      <section v-else-if="blocked" class="card take-state">
        <h2 class="card-title">这份答卷的状态已经变化</h2>
        <p class="card-hint">计时与交卷都由服务端判定，可以先查看这份答卷的当前结果。</p>
        <div class="state-actions">
          <el-button type="primary" @click="goResult">查看本次答卷</el-button>
          <el-button @click="router.replace('/student/exams')">返回我的考试</el-button>
        </div>
      </section>

      <template v-else>
        <div class="take-main">
          <p v-if="resumed" class="resume-tip">断点续考：已恢复到本次作答保存过的进度。</p>
          <article class="card q-card">
            <div class="q-head">
              <span class="tag">{{ question.qTypeName }}</span>
              <span class="q-no num">第 {{ current + 1 }} / {{ questions.length }} 题</span>
              <span class="q-score num">{{ question.score }} 分</span>
            </div>

            <p class="q-content">{{ question.content }}</p>

            <el-radio-group
              v-if="question.qType === 1 || question.qType === 3"
              class="opt-list"
              :model-value="singleValue(question)"
              @change="setSingle(question, $event)"
            >
              <el-radio v-for="opt in optionList(question)" :key="opt.key" :value="opt.key">
                <span v-if="question.qType !== 3" class="opt-key num">{{ opt.key }}</span>
                <span>{{ opt.text }}</span>
              </el-radio>
            </el-radio-group>

            <el-checkbox-group
              v-else-if="question.qType === 2"
              class="opt-list"
              :model-value="valueOf(question)"
              @change="setMulti(question, $event)"
            >
              <el-checkbox v-for="opt in optionList(question)" :key="opt.key" :value="opt.key">
                <span class="opt-key num">{{ opt.key }}</span>
                <span>{{ opt.text }}</span>
              </el-checkbox>
            </el-checkbox-group>

            <div v-else-if="question.qType === 4" class="blank-list">
              <div v-for="n in blankTotal(question)" :key="n" class="blank-row">
                <span class="blank-label num">第 {{ n }} 空</span>
                <el-input
                  :model-value="blankValue(question, n - 1)"
                  placeholder="填写这一空的答案"
                  @input="setBlank(question, n - 1, $event)"
                />
              </div>
            </div>

            <el-input
              v-else
              type="textarea"
              :rows="8"
              resize="none"
              placeholder="写下你的作答，内容会自动保存"
              :model-value="singleValue(question)"
              @input="writeAnswer(question, [$event])"
            />

            <div class="q-foot">
              <el-button :disabled="current === 0" @click="go(current - 1)">上一题</el-button>
              <el-button v-if="current < questions.length - 1" type="primary" @click="go(current + 1)">下一题</el-button>
              <el-button v-else type="primary" @click="submitManually">交卷</el-button>
              <el-button text :disabled="!isAnswered(question)" @click="clearAnswer(question)">清除本题</el-button>
            </div>
          </article>
        </div>

        <aside class="sheet">
          <section class="card">
            <div class="card-head">
              <div>
                <h2 class="card-title">答题卡</h2>
                <p class="card-hint">点题号可直接跳到该题</p>
              </div>
              <span class="tag num">{{ answeredCount }}/{{ questions.length }}</span>
            </div>
            <div class="pills">
              <button
                v-for="(q, i) in questions"
                :key="q.paperQuestionId"
                type="button"
                class="pill num"
                :class="{ done: isAnswered(q), here: i === current }"
                @click="go(i)"
              >
                {{ i + 1 }}
              </button>
            </div>
            <div class="legend">
              <span><i class="dot dot-done" />已答</span>
              <span><i class="dot" />未答</span>
              <span><i class="dot dot-here" />当前</span>
            </div>
          </section>

          <section class="card sheet-note">
            <p>离开答题页会被记为一次切屏，已切屏 <b class="num">{{ switchCount }}</b> 次<template v-if="switchLimit">，上限 {{ switchLimit }} 次</template>。</p>
            <p>时间用完由系统自动交卷，得分与是否及格都以服务端判定为准。</p>
          </section>
        </aside>
      </template>
    </main>
  </div>
</template>

<style scoped>
.take {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.take-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
  padding: 10px 26px;
  background: rgba(255, 255, 255, .86);
  backdrop-filter: saturate(180%) blur(20px);
  border-bottom: 1px solid var(--line-soft);
}

.take-bar-main { min-width: 0; }

.take-title {
  font-size: 16px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60vw;
}

.take-meta { font-size: 12px; color: var(--ink-3); }

.take-bar-side {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.take-clock { font-size: 16px; font-weight: 600; letter-spacing: -.01em; }
.take-clock.tight { color: var(--warn); }
.take-left { font-size: 13px; color: var(--ink-3); }
.take-left b { color: var(--ink); font-weight: 600; }

.take-body {
  flex: 1;
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: 26px 26px 70px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 306px;
  gap: 20px;
  align-items: start;
}

.take-state { grid-column: 1 / -1; }

.state-actions { display: flex; gap: 10px; margin-top: 18px; }

.resume-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--ink-3);
}

.q-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.q-no { font-size: 13px; color: var(--ink-3); }
.q-score { margin-left: auto; font-size: 13px; color: var(--ink-3); }

.q-content {
  margin: 18px 0 22px;
  font-size: 16px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.opt-list { display: grid; gap: 10px; width: 100%; }

.opt-list :deep(.el-radio),
.opt-list :deep(.el-checkbox) {
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: 12px 14px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-sm);
  background: var(--paper);
  transition: border-color .18s var(--ease), background .18s var(--ease);
}

.opt-list :deep(.el-radio:hover),
.opt-list :deep(.el-checkbox:hover) { border-color: var(--line); background: var(--paper-sunk); }

.opt-list :deep(.el-radio.is-checked),
.opt-list :deep(.el-checkbox.is-checked) { border-color: var(--ink); }

.opt-list :deep(.el-radio__label),
.opt-list :deep(.el-checkbox__label) {
  display: flex;
  align-items: baseline;
  gap: 8px;
  white-space: normal;
  line-height: 1.7;
  font-weight: 400;
  color: var(--ink);
}

.opt-key { font-weight: 600; color: var(--ink-3); }

.blank-list { display: grid; gap: 14px; }

.blank-row { display: grid; grid-template-columns: 74px minmax(0, 1fr); align-items: center; gap: 12px; }

.blank-label { font-size: 13px; color: var(--ink-3); }

.q-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 26px;
  padding-top: 18px;
  border-top: 1px solid var(--line-soft);
}

.sheet {
  display: grid;
  gap: 16px;
  position: sticky;
  top: 78px;
}

.pills {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(34px, 1fr));
  gap: 8px;
}

.pill {
  height: 34px;
  border-radius: 10px;
  border: 1px solid var(--line-soft);
  background: var(--paper-sunk);
  color: var(--ink-3);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: transform .16s var(--ease), background .18s var(--ease), border-color .18s var(--ease);
}

.pill:hover { transform: translateY(-1px); border-color: var(--line); }
.pill.done { background: var(--ink); border-color: var(--ink); color: #fff; }
.pill.here { box-shadow: 0 0 0 2px var(--ink) inset; color: var(--ink); }
.pill.done.here { box-shadow: 0 0 0 2px rgba(255, 255, 255, .92) inset; }

.legend { display: flex; gap: 14px; margin-top: 16px; font-size: 12px; color: var(--ink-3); }
.legend span { display: inline-flex; align-items: center; gap: 6px; }
.dot { width: 9px; height: 9px; border-radius: 50%; background: var(--line); }
.dot-done { background: var(--ink); }
.dot-here { background: transparent; box-shadow: 0 0 0 2px var(--ink) inset; }

.sheet-note p { margin: 0; font-size: 12px; line-height: 1.7; color: var(--ink-3); }
.sheet-note p + p { margin-top: 8px; }

@media (max-width: 980px) {
  .take-body { grid-template-columns: 1fr; padding: 18px 16px 56px; }
  .sheet { position: static; order: -1; }
  .take-title { max-width: 100%; }
}
</style>
