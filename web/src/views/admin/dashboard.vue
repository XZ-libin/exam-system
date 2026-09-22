<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { gradingApi, statApi } from '../../api'
import MiniChart from '../../components/mini-chart.vue'

const router = useRouter()

const overview = ref({})
const loading = ref(true)
const pending = ref(null)

function dash(value, suffix = '') {
  if (value === null || value === undefined || value === '') return '—'
  return `${value}${suffix}`
}

const metrics = computed(() => [
  {
    label: '考试场次',
    value: dash(overview.value.examCount),
    unit: '场',
    foot: `${dash(overview.value.accountCount)} 个账号在册`
  },
  { label: '试卷总数', value: dash(overview.value.paperCount), unit: '套', foot: '' },
  { label: '题库题量', value: dash(overview.value.questionCount), unit: '道', foot: '' },
  {
    label: '已交份数',
    value: dash(overview.value.attemptCount),
    unit: '份',
    foot: `平均分 ${dash(overview.value.avgScore)} · 及格率 ${dash(overview.value.passRate, '%')}`
  }
])

const axisCategory = {
  axisLine: { lineStyle: { color: '#d2d2d7' } },
  axisTick: { show: false },
  axisLabel: { color: '#6e6e73', fontSize: 12 }
}
const axisValue = {
  minInterval: 1,
  splitLine: { lineStyle: { color: '#e8e8ed' } },
  axisLabel: { color: '#a1a1a6', fontSize: 12 }
}

const sections = computed(() => overview.value.scoreSections || [])
const hasSections = computed(() => sections.value.some((item) => Number(item.count) > 0))

const sectionOption = computed(() => ({
  grid: { left: 8, right: 16, top: 28, bottom: 4, containLabel: true },
  xAxis: { type: 'category', data: sections.value.map((item) => item.label), ...axisCategory },
  yAxis: { type: 'value', ...axisValue },
  series: [
    {
      type: 'bar',
      data: sections.value.map((item) => Number(item.count) || 0),
      barMaxWidth: 44,
      itemStyle: { color: '#1d1d1f', borderRadius: [8, 8, 0, 0] },
      label: { show: true, position: 'top', color: '#6e6e73', fontSize: 12 }
    }
  ]
}))

const typeItems = computed(() => ((overview.value.typeDifficulty || {}).questionTypes || []).slice().reverse())
const hasTypes = computed(() => typeItems.value.some((item) => Number(item.count) > 0))

const typeOption = computed(() => ({
  grid: { left: 8, right: 32, top: 12, bottom: 4, containLabel: true },
  xAxis: { type: 'value', ...axisValue },
  yAxis: { type: 'category', data: typeItems.value.map((item) => item.qTypeName), ...axisCategory },
  series: [
    {
      type: 'bar',
      data: typeItems.value.map((item) => Number(item.count) || 0),
      barMaxWidth: 14,
      itemStyle: { color: '#86868b', borderRadius: 7 },
      label: { show: true, position: 'right', color: '#6e6e73', fontSize: 12 }
    }
  ]
}))

const recentExams = computed(() => (overview.value.recentExams || []).slice(0, 5))

function timeText(value) {
  if (!value) return '未安排'
  const text = String(value).replace('T', ' ')
  return text.length > 16 ? text.slice(0, 16) : text
}

function openStatistics(exam) {
  router.push({ path: '/admin/statistics', query: { examId: exam.examId } })
}

onMounted(async () => {
  loading.value = true
  try {
    overview.value = (await statApi.overview()) || {}
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    loading.value = false
  }
  try {
    const res = await gradingApi.pendingCount()
    pending.value = Number((res || {}).count) || 0
  } catch {
    pending.value = null
  }
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">工作台</h1>
        <p class="page-sub">题库、试卷与考试的当前情况，可直接跳到常做的三件事。</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" @click="router.push({ path: '/admin/papers', query: { create: '1' } })">
          新建试卷
        </el-button>
        <el-button @click="router.push('/admin/exams')">发布考试</el-button>
        <el-button @click="router.push('/admin/grading')">去阅卷</el-button>
        <span v-if="pending" class="tag tag-warn">{{ pending }} 份待阅</span>
      </div>
    </div>

    <div class="grid grid-4" v-loading="loading">
      <div v-for="item in metrics" :key="item.label" class="card metric-card">
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value num">
          {{ item.value }}<span v-if="item.unit" class="metric-unit">{{ item.unit }}</span>
        </div>
        <div v-if="item.foot" class="metric-foot num">{{ item.foot }}</div>
      </div>
    </div>

    <div class="grid grid-2 block">
      <section class="card">
        <div class="card-head">
          <div>
            <h2 class="card-title">分数段分布</h2>
            <p class="card-hint">全部已出分答卷按百分率分档</p>
          </div>
        </div>
        <div v-loading="loading" class="chart-box">
          <MiniChart v-if="hasSections" :option="sectionOption" height="286px" />
          <el-empty v-else-if="!loading" description="还没有可统计的考试成绩" :image-size="72" />
        </div>
      </section>

      <section class="card">
        <div class="card-head">
          <div>
            <h2 class="card-title">题型分布</h2>
            <p class="card-hint">题库里各题型的可用题量</p>
          </div>
        </div>
        <div v-loading="loading" class="chart-box">
          <MiniChart v-if="hasTypes" :option="typeOption" height="286px" />
          <el-empty v-else-if="!loading" description="题库还没有题目" :image-size="72" />
        </div>
      </section>
    </div>

    <section class="card block">
      <div class="card-head">
        <div>
          <h2 class="card-title">最近的考试</h2>
          <p class="card-hint">点开可查看该场考试的成绩分析</p>
        </div>
        <el-button text @click="router.push({ path: '/admin/statistics' })">全部统计</el-button>
      </div>
      <div v-loading="loading">
        <div
          v-for="exam in recentExams"
          :key="exam.examId"
          class="list-row exam-row"
          @click="openStatistics(exam)"
        >
          <div class="list-row-main">
            <div class="list-row-title">{{ exam.title }}</div>
            <div class="list-row-meta">结束于 {{ timeText(exam.endTime) }}</div>
          </div>
          <div class="exam-tail">
            <span class="num exam-submit">交卷 {{ exam.submitCount }}</span>
            <span class="tag" :class="exam.avgRate === null || exam.avgRate === undefined ? 'tag-quiet' : 'tag-ok'">
              {{ exam.avgRate === null || exam.avgRate === undefined ? '成绩未发布' : `平均正确率 ${exam.avgRate}%` }}
            </span>
          </div>
        </div>
        <el-empty v-if="!loading && !recentExams.length" description="还没有创建考试" :image-size="72" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.metric-card {
  display: flex;
  flex-direction: column;
}

.metric-foot {
  margin-top: auto;
  padding-top: 10px;
}

.block {
  margin-top: 20px;
}

.chart-box {
  min-height: 286px;
}

.exam-row {
  cursor: pointer;
  border-radius: var(--radius-sm);
  padding-inline: 8px;
  margin-inline: -8px;
  transition: background .18s var(--ease);
}

.exam-row:hover {
  background: var(--paper-sunk);
}

.exam-tail {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.exam-submit {
  font-size: 13px;
  color: var(--ink-3);
}
</style>
