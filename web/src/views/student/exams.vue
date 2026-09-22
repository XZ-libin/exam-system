<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { attemptApi } from '../../api'

const router = useRouter()

const loading = ref(true)
const exams = ref([])

/** 进行中的排前面，与后端返回顺序保持一致 */
const openList = computed(() => exams.value.filter((exam) => exam.state !== 3))
const closedList = computed(() => exams.value.filter((exam) => exam.state === 3))

const stateText = { 1: '未开始', 2: '进行中', 3: '已结束' }
const statusText = { 0: '作答中', 1: '待阅卷', 2: '已完成', 3: '超时强制交卷' }

function stateTag(state) {
  if (state === 2) return 'tag tag-ok'
  if (state === 3) return 'tag tag-quiet'
  return 'tag'
}

function attemptsLeft(exam) {
  return exam.maxAttempts == null || Number(exam.usedAttempts || 0) < Number(exam.maxAttempts)
}

function metaOf(exam) {
  const bits = []
  if (exam.state === 1 && exam.startTime) {
    bits.push(`${dayjs(exam.startTime).format('MM-DD HH:mm')} 开考`)
  } else if (exam.endTime) {
    bits.push(`${dayjs(exam.endTime).format('MM-DD HH:mm')} ${exam.state === 3 ? '结束' : '交卷截止'}`)
  }
  if (exam.durationMinutes) bits.push(`限时 ${exam.durationMinutes} 分钟`)
  if (exam.questionCount) bits.push(`${exam.questionCount} 题`)
  if (exam.totalScore != null) bits.push(`满分 ${exam.totalScore} 分`)
  if (exam.maxAttempts != null && Number(exam.usedAttempts || 0) > 0) {
    bits.push(`已作答 ${exam.usedAttempts}/${exam.maxAttempts} 次`)
  }
  return bits.filter(Boolean).join(' · ')
}

function actionsOf(exam) {
  const list = []
  if (exam.resumableRecordId) {
    list.push({ key: 'resume', label: '继续作答', primary: true, run: () => take(exam) })
  } else if (exam.state === 2 && attemptsLeft(exam)) {
    list.push({ key: 'enter', label: '进入考试', primary: true, run: () => take(exam) })
  }
  if (exam.lastRecordId && Number(exam.scorePublished) === 1) {
    list.push({
      key: 'result',
      label: '查看成绩',
      run: () => router.push({ name: 'studentResult', params: { recordId: exam.lastRecordId } })
    })
  }
  return list
}

function hintOf(exam) {
  if (exam.state === 2 && !exam.resumableRecordId && !attemptsLeft(exam)) return '作答次数已用完，成绩见明细'
  if (exam.state === 1) return '到达开始时间后可进入'
  return ''
}

function lastText(exam) {
  if (!exam.lastRecordId) return '未参加这场考试'
  const label = statusText[exam.lastStatus] ? `（${statusText[exam.lastStatus]}）` : ''
  if (Number(exam.scorePublished) === 1 && exam.lastScore != null) {
    return `上次作答${label}：得分 ${exam.lastScore} 分`
  }
  return `上次作答${label}：成绩尚未发布`
}

function take(exam) {
  router.push({ name: 'studentTake', params: { examId: exam.id } })
}

async function load() {
  loading.value = true
  try {
    const data = await attemptApi.myExams()
    exams.value = Array.isArray(data) ? data : []
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1 class="page-title">我的考试</h1>
        <p class="page-sub">只列出你被安排的场次，进入后计时以服务端为准。</p>
      </div>
      <div class="page-actions">
        <el-button text :loading="loading" @click="load">刷新</el-button>
        <el-button @click="router.push('/student/scores')">我的成绩</el-button>
      </div>
    </header>

    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else>
      <section class="card">
        <div class="card-head">
          <div>
            <h2 class="card-title">可参加</h2>
            <p class="card-hint">答题过程中离开页面会被记为一次切屏，时间用完会自动交卷。</p>
          </div>
          <span class="tag">{{ openList.length }} 场</span>
        </div>

        <el-empty v-if="!openList.length" description="当前没有可以参加的考试" />
        <div v-for="exam in openList" :key="exam.id" class="list-row">
          <div class="list-row-main">
            <div class="list-row-title">
              {{ exam.title }}
              <span :class="stateTag(exam.state)">{{ stateText[exam.state] }}</span>
            </div>
            <div class="list-row-meta">{{ metaOf(exam) }}</div>
            <div v-if="exam.description" class="list-row-meta">{{ exam.description }}</div>
            <div v-if="hintOf(exam) && !actionsOf(exam).length" class="list-row-meta hint">{{ hintOf(exam) }}</div>
          </div>
          <div class="row-actions">
            <span v-if="exam.lastRecordId && Number(exam.scorePublished) !== 1" class="tag tag-quiet">成绩未发布</span>
            <el-button
              v-for="action in actionsOf(exam)"
              :key="action.key"
              :type="action.primary ? 'primary' : 'default'"
              @click="action.run()"
            >
              {{ action.label }}
            </el-button>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="card-head">
          <div>
            <h2 class="card-title">已结束</h2>
            <p class="card-hint">近 60 天内结束的场次，更早的记录去「我的成绩」查看。</p>
          </div>
          <span class="tag">{{ closedList.length }} 场</span>
        </div>

        <el-empty v-if="!closedList.length" description="还没有已结束的考试" />
        <div v-for="exam in closedList" :key="exam.id" class="list-row">
          <div class="list-row-main">
            <div class="list-row-title">
              {{ exam.title }}
              <span class="tag tag-quiet">已结束</span>
            </div>
            <div class="list-row-meta">{{ metaOf(exam) }}</div>
            <div class="list-row-meta">{{ lastText(exam) }}</div>
          </div>
          <div class="row-actions">
            <el-button
              v-for="action in actionsOf(exam)"
              :key="action.key"
              :type="action.primary ? 'primary' : 'default'"
              @click="action.run()"
            >
              {{ action.label }}
            </el-button>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.row-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.hint {
  color: var(--ink-4);
}

.list-row-title .tag {
  margin-left: 6px;
  vertical-align: 2px;
}
</style>
