<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { examApi, statApi } from '../../api'
import MiniChart from '../../components/mini-chart.vue'

const route = useRoute()

const tab = ref('exam')

const exams = ref([])
const examId = ref(null)
const stats = ref(null)
const statsLoading = ref(false)

const overview = ref(null)
const overviewLoading = ref(false)

const questionDialog = ref(false)
const questionLoading = ref(false)
const questionDetail = ref(null)

function num(value, digits = 1) {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  const n = Number(value)
  if (Number.isNaN(n)) {
    return '—'
  }
  const factor = 10 ** digits
  return String(Math.round(n * factor) / factor)
}

function rate(value) {
  return value === null || value === undefined ? '—' : `${num(value)}%`
}

async function loadExams() {
  try {
    const data = await examApi.page({ page: 1, size: 100 })
    exams.value = data.records || []
    const fromQuery = route.query.examId ? Number(route.query.examId) : null
    if (fromQuery && exams.value.some((e) => e.id === fromQuery)) {
      examId.value = fromQuery
      return
    }
    const withScore = exams.value.find((e) => e.avgScore !== null && e.avgScore !== undefined)
    examId.value = withScore ? withScore.id : (exams.value[0] ? exams.value[0].id : null)
  } catch {
    /* 提示已由请求层给出 */
  }
}

async function loadStats() {
  if (!examId.value) {
    stats.value = null
    return
  }
  statsLoading.value = true
  try {
    stats.value = await statApi.exam(examId.value)
  } catch {
    stats.value = null
  } finally {
    statsLoading.value = false
  }
}

async function loadOverview() {
  if (overview.value || overviewLoading.value) {
    return
  }
  overviewLoading.value = true
  try {
    overview.value = await statApi.overview()
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    overviewLoading.value = false
  }
}

watch(examId, loadStats)
watch(tab, (value) => {
  if (value === 'overview') {
    loadOverview()
  }
})

const sections = computed(() => (stats.value?.scoreSections || []))
const questionStats = computed(() => [...(stats.value?.questionStats || [])].sort((a, b) => Number(a.accuracy) - Number(b.accuracy)))
const classStats = computed(() => stats.value?.classStats || [])

const hasSections = computed(() => sections.value.some((s) => Number(s.count) > 0))
const hasQuestions = computed(() => questionStats.value.length > 0)
const hasClasses = computed(() => classStats.value.length > 0)

const sectionOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
  xAxis: {
    type: 'category',
    data: sections.value.map((s) => s.label),
    axisLine: { lineStyle: { color: '#e8e8ed' } },
    axisTick: { show: false },
    axisLabel: { color: '#6e6e73' }
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    splitLine: { lineStyle: { color: '#e8e8ed' } },
    axisLabel: { color: '#a1a1a6' }
  },
  series: [{
    type: 'bar',
    name: '人数',
    data: sections.value.map((s) => Number(s.count || 0)),
    barMaxWidth: 42,
    itemStyle: { color: '#1d1d1f', borderRadius: [6, 6, 0, 0] }
  }]
}))

const accuracyOption = computed(() => {
  const list = questionStats.value
  const worst = new Set(list.slice(0, 3).map((q) => q.paperQuestionId))
  return {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const row = list[params.dataIndex]
        if (!row) {
          return ''
        }
        return `第 ${row.paperQuestionId} 题 · ${row.qTypeName}<br/>正确率 ${num(row.accuracy)}%<br/>`
          + `作答 ${num(row.answerCount, 0)} 人 · 答对 ${num(row.correctCount, 0)} 人<br/>`
          + `鉴别度 ${row.discrimination === null || row.discrimination === undefined ? '—' : num(row.discrimination, 2)}`
      }
    },
    grid: { left: 8, right: 28, top: 12, bottom: 8, containLabel: true },
    xAxis: {
      type: 'value',
      max: 100,
      splitLine: { lineStyle: { color: '#e8e8ed' } },
      axisLabel: { formatter: '{value}%', color: '#a1a1a6' }
    },
    yAxis: {
      type: 'category',
      data: list.map((q) => `第 ${q.paperQuestionId} 题`),
      axisLine: { lineStyle: { color: '#e8e8ed' } },
      axisTick: { show: false },
      axisLabel: { color: '#6e6e73' }
    },
    series: [{
      type: 'bar',
      data: list.map((q) => ({
        value: Number(q.accuracy || 0),
        itemStyle: {
          color: worst.has(q.paperQuestionId) ? '#1d1d1f' : '#c7c7cc',
          borderRadius: [0, 5, 5, 0]
        }
      })),
      barMaxWidth: 14
    }]
  }
})

const classOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    formatter: (params) => {
      const row = classStats.value[params[0]?.dataIndex]
      if (!row) {
        return ''
      }
      return `${row.className}<br/>人数 ${num(row.count, 0)}<br/>平均分 ${num(row.avgScore)}<br/>及格率 ${num(row.passRate)}%`
    }
  },
  legend: { top: 0, right: 0, itemWidth: 10, itemHeight: 10, icon: 'circle', textStyle: { color: '#6e6e73', fontSize: 12 } },
  grid: { left: 8, right: 16, top: 34, bottom: 8, containLabel: true },
  xAxis: {
    type: 'category',
    data: classStats.value.map((c) => c.className || '未分班'),
    axisLine: { lineStyle: { color: '#e8e8ed' } },
    axisTick: { show: false },
    axisLabel: { color: '#6e6e73' }
  },
  yAxis: [
    { type: 'value', name: '平均分', nameTextStyle: { color: '#a1a1a6', fontSize: 11 }, splitLine: { lineStyle: { color: '#e8e8ed' } }, axisLabel: { color: '#a1a1a6' } },
    { type: 'value', name: '及格率', max: 100, nameTextStyle: { color: '#a1a1a6', fontSize: 11 }, splitLine: { show: false }, axisLabel: { formatter: '{value}%', color: '#a1a1a6' } }
  ],
  series: [
    {
      name: '平均分',
      type: 'bar',
      data: classStats.value.map((c) => Number(c.avgScore || 0)),
      barMaxWidth: 34,
      itemStyle: { color: '#1d1d1f', borderRadius: [6, 6, 0, 0] }
    },
    {
      name: '及格率',
      type: 'line',
      yAxisIndex: 1,
      data: classStats.value.map((c) => Number(c.passRate || 0)),
      symbolSize: 7,
      lineStyle: { color: '#86868b', width: 2 },
      itemStyle: { color: '#86868b' }
    }
  ]
}))

const overviewSections = computed(() => overview.value?.scoreSections || [])
const hasOverviewSections = computed(() => overviewSections.value.some((s) => Number(s.count) > 0))
const typeSlices = computed(() => (overview.value?.typeDifficulty?.questionTypes || [])
  .map((t) => ({ name: t.qTypeName, value: Number(t.count || 0) }))
  .filter((t) => t.value > 0))

const overviewSectionOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
  xAxis: {
    type: 'category',
    data: overviewSections.value.map((s) => s.label),
    axisLine: { lineStyle: { color: '#e8e8ed' } },
    axisTick: { show: false },
    axisLabel: { color: '#6e6e73' }
  },
  yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#e8e8ed' } }, axisLabel: { color: '#a1a1a6' } },
  series: [{
    type: 'bar',
    name: '答卷数',
    data: overviewSections.value.map((s) => Number(s.count || 0)),
    barMaxWidth: 42,
    itemStyle: { color: '#48484a', borderRadius: [6, 6, 0, 0] }
  }]
}))

const typePieOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}：{c} 题（{d}%）' },
  legend: { bottom: 0, itemWidth: 9, itemHeight: 9, icon: 'circle', textStyle: { color: '#6e6e73', fontSize: 12 } },
  series: [{
    type: 'pie',
    radius: ['46%', '68%'],
    center: ['50%', '44%'],
    avoidLabelOverlap: true,
    itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
    label: { show: false },
    color: ['#1d1d1f', '#48484a', '#86868b', '#aeaeb2', '#c7c7cc'],
    data: typeSlices.value
  }]
}))

async function openQuestion(row) {
  if (!row || !row.questionId) {
    return
  }
  questionDialog.value = true
  questionLoading.value = true
  questionDetail.value = null
  try {
    questionDetail.value = await statApi.question(row.questionId)
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    questionLoading.value = false
  }
}

function focusExam(examIdValue) {
  tab.value = 'exam'
  if (exams.value.some((e) => e.id === examIdValue)) {
    if (examId.value === examIdValue) {
      loadStats()
    } else {
      examId.value = examIdValue
    }
  }
}

function accuracyTag(value) {
  const n = Number(value)
  if (Number.isNaN(n)) {
    return 'tag-quiet'
  }
  if (n >= 80) return 'tag-ok'
  if (n >= 50) return 'tag'
  return 'tag-danger'
}

onMounted(loadExams)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">统计分析</h1>
        <p class="page-sub">看单场考试的分数分布、每题正确率与班级差异，也可以看整体的题库与成绩画像。</p>
      </div>
      <div class="page-actions">
        <el-select v-if="tab === 'exam'" v-model="examId" class="picker" filterable placeholder="选择考试" :loading="statsLoading">
          <el-option v-for="e in exams" :key="e.id" :label="e.title" :value="e.id" />
        </el-select>
      </div>
    </div>

    <el-tabs v-model="tab" class="tabs">
      <el-tab-pane label="单场分析" name="exam" />
      <el-tab-pane label="总体看板" name="overview" />
    </el-tabs>

    <div v-if="tab === 'exam'" v-loading="statsLoading" class="pane">
      <template v-if="stats">
        <section class="card">
          <div class="card-head">
            <div>
              <h2 class="card-title">{{ stats.title }}</h2>
              <p class="card-hint">试卷 {{ stats.paperTitle }} · 满分 {{ num(stats.totalScore, 0) }} 分 · 及格线 {{ num(stats.passScore) }} 分</p>
            </div>
          </div>
          <div class="grid grid-4 metrics">
            <div>
              <div class="metric-label">应交 / 实交</div>
              <div class="metric-value num">{{ num(stats.expectedCount, 0) }}
                <span class="metric-unit">/ {{ num(stats.submitCount, 0) }}</span>
              </div>
              <div class="metric-foot">参与作答 {{ num(stats.joinCount, 0) }} 人</div>
            </div>
            <div>
              <div class="metric-label">平均分</div>
              <div class="metric-value num">{{ num(stats.avgScore) }}</div>
              <div class="metric-foot">平均得分率 {{ rate(stats.avgRate) }}</div>
            </div>
            <div>
              <div class="metric-label">最高 / 最低</div>
              <div class="metric-value num">{{ num(stats.maxScore) }}
                <span class="metric-unit">/ {{ num(stats.minScore) }}</span>
              </div>
              <div class="metric-foot">按得分率分档统计</div>
            </div>
            <div>
              <div class="metric-label">及格率</div>
              <div class="metric-value num">{{ num(stats.passRate) }}<span class="metric-unit">%</span></div>
              <div class="metric-foot">达到及格线的比例</div>
            </div>
          </div>
        </section>

        <div class="grid grid-2 charts">
          <section class="card">
            <div class="card-head">
              <div>
                <h2 class="card-title">分数段分布</h2>
                <p class="card-hint">得分率 = 卷面得分 ÷ 卷面满分，每 10 分一档</p>
              </div>
            </div>
            <MiniChart v-if="hasSections" :option="sectionOption" height="260px" />
            <el-empty v-else description="暂无分数数据，成绩统计需要已交卷的答卷" :image-size="56" />
          </section>

          <section class="card">
            <div class="card-head">
              <div>
                <h2 class="card-title">班级对比</h2>
                <p class="card-hint">各班平均分与及格率并列对比</p>
              </div>
            </div>
            <MiniChart v-if="hasClasses" :option="classOption" height="260px" />
            <el-empty v-else description="暂无班级数据" :image-size="56" />
          </section>
        </div>

        <section class="card">
          <div class="card-head">
            <div>
              <h2 class="card-title">每题正确率</h2>
              <p class="card-hint">按正确率从低到高排列，最深的三题是本场的薄弱点；悬停可看鉴别度</p>
            </div>
          </div>
          <MiniChart v-if="hasQuestions" :option="accuracyOption" :height="`${Math.max(220, questionStats.length * 22 + 40)}px`" />
          <el-empty v-else description="暂无逐题统计" :image-size="56" />

          <el-table v-if="hasQuestions" :data="questionStats" class="qtable" @row-click="openQuestion">
            <el-table-column prop="paperQuestionId" label="题号" width="72">
              <template #default="{ row }"><span class="num">{{ row.paperQuestionId }}</span></template>
            </el-table-column>
            <el-table-column prop="brief" label="摘要" min-width="240" show-overflow-tooltip />
            <el-table-column prop="qTypeName" label="题型" width="96" />
            <el-table-column label="难度" width="80">
              <template #default="{ row }"><span class="num">{{ num(row.difficulty, 0) }}</span> / 5</template>
            </el-table-column>
            <el-table-column label="作答" width="80">
              <template #default="{ row }"><span class="num">{{ num(row.answerCount, 0) }}</span></template>
            </el-table-column>
            <el-table-column label="正确" width="80">
              <template #default="{ row }"><span class="num">{{ num(row.correctCount, 0) }}</span></template>
            </el-table-column>
            <el-table-column label="正确率" width="100">
              <template #default="{ row }">
                <span class="tag" :class="accuracyTag(row.accuracy)">{{ num(row.accuracy) }}%</span>
              </template>
            </el-table-column>
            <el-table-column label="鉴别度" width="96">
              <template #default="{ row }">
                <span class="num">{{ row.discrimination === null || row.discrimination === undefined ? '—' : num(row.discrimination, 2) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <p v-if="hasQuestions" class="table-hint">点击任意一行可查看该题的正文、解析与跨卷统计。</p>
        </section>
      </template>

      <el-empty v-else-if="!statsLoading" description="选择一场考试后查看分析结果" :image-size="70" />
    </div>

    <div v-else v-loading="overviewLoading" class="pane">
      <section class="card">
        <div class="card-head">
          <div>
            <h2 class="card-title">整体画像</h2>
            <p class="card-hint">题库规模、答卷数量与全校平均分、及格率</p>
          </div>
        </div>
        <div class="grid grid-4 metrics">
          <div>
            <div class="metric-label">考试场次</div>
            <div class="metric-value num">{{ num(overview?.examCount, 0) }}</div>
            <div class="metric-foot">已创建 {{ num(overview?.paperCount, 0) }} 份试卷</div>
          </div>
          <div>
            <div class="metric-label">题库题量</div>
            <div class="metric-value num">{{ num(overview?.questionCount, 0) }}</div>
            <div class="metric-foot">账号 {{ num(overview?.accountCount, 0) }} 个</div>
          </div>
          <div>
            <div class="metric-label">已交答卷</div>
            <div class="metric-value num">{{ num(overview?.attemptCount, 0) }}</div>
            <div class="metric-foot">含全部场次的交卷记录</div>
          </div>
          <div>
            <div class="metric-label">平均分 / 及格率</div>
            <div class="metric-value num">{{ num(overview?.avgScore) }}
              <span class="metric-unit">{{ rate(overview?.passRate) }}</span>
            </div>
            <div class="metric-foot">按已发布成绩统计</div>
          </div>
        </div>
      </section>

      <div class="grid grid-2 charts">
        <section class="card">
          <div class="card-head">
            <div>
              <h2 class="card-title">分数段分布</h2>
              <p class="card-hint">全部已判分答卷的得分区间</p>
            </div>
          </div>
          <MiniChart v-if="hasOverviewSections" :option="overviewSectionOption" height="260px" />
          <el-empty v-else description="暂无成绩数据" :image-size="56" />
        </section>

        <section class="card">
          <div class="card-head">
            <div>
              <h2 class="card-title">题型分布</h2>
              <p class="card-hint">题库中各题型的数量占比</p>
            </div>
          </div>
          <MiniChart v-if="typeSlices.length" :option="typePieOption" height="260px" />
          <el-empty v-else description="题库还没有题目" :image-size="56" />
        </section>
      </div>

      <section class="card">
        <div class="card-head">
          <div>
            <h2 class="card-title">最近考试</h2>
            <p class="card-hint">点击任意一场可切到单场分析继续查看</p>
          </div>
        </div>
        <div v-if="(overview?.recentExams || []).length">
          <div v-for="item in overview.recentExams" :key="item.examId" class="list-row rowish"
               @click="focusExam(item.examId)">
            <div class="list-row-main">
              <div class="list-row-title">{{ item.title }}</div>
              <div class="list-row-meta">截止 {{ item.endTime || '—' }}</div>
            </div>
            <div class="list-row-meta num">已交 {{ num(item.submitCount, 0) }} 份 · 平均得分率 {{ rate(item.avgRate) }}</div>
          </div>
        </div>
        <el-empty v-else description="还没有考试记录" :image-size="56" />
      </section>
    </div>

    <el-dialog v-model="questionDialog" title="题目分析" width="560px">
      <div v-loading="questionLoading" class="dialog-body">
        <template v-if="questionDetail">
          <h3 class="dialog-title">{{ questionDetail.content }}</h3>
          <p class="dialog-meta">
            <span class="tag">{{ questionDetail.qTypeName }}</span>
            <span class="tag tag-quiet">难度 {{ num(questionDetail.difficulty, 0) }} / 5</span>
          </p>
          <div class="dialog-stats grid grid-4">
            <div>
              <div class="metric-label">作答数</div>
              <div class="metric-value small num">{{ num(questionDetail.answerCount, 0) }}</div>
            </div>
            <div>
              <div class="metric-label">答对数</div>
              <div class="metric-value small num">{{ num(questionDetail.correctCount, 0) }}</div>
            </div>
            <div>
              <div class="metric-label">正确率</div>
              <div class="metric-value small num">{{ num(questionDetail.accuracy) }}%</div>
            </div>
            <div>
              <div class="metric-label">被引用</div>
              <div class="metric-value small num">{{ num(questionDetail.paperCount, 0) }}<span class="metric-unit">卷</span></div>
            </div>
          </div>
          <div class="dialog-block">
            <div class="metric-label">解析</div>
            <p class="dialog-text">{{ questionDetail.analysis || '这道题还没有录入解析' }}</p>
          </div>
        </template>
        <el-empty v-else-if="!questionLoading" description="暂无题目数据" :image-size="56" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.picker { width: 240px; }
.tabs { margin-bottom: 6px; }
.pane { min-height: 240px; }
.charts {
  margin: 20px 0;
}
.charts > .card { margin-top: 0; }
.metric-value.small { font-size: 22px; }

.qtable { margin-top: 18px; }
.qtable :deep(.el-table__row) { cursor: pointer; }
.table-hint { margin: 10px 0 0; font-size: 12px; color: var(--ink-4); }

.rowish { cursor: pointer; }
.rowish:hover { background: var(--paper-sunk); }

.dialog-body { min-height: 200px; }
.dialog-title { font-size: 16px; line-height: 1.6; }
.dialog-meta { display: flex; gap: 8px; margin: 10px 0 18px; }
.dialog-stats { margin-bottom: 18px; }
.dialog-block {
  padding-top: 16px;
  border-top: 1px solid var(--line-soft);
}
.dialog-text { margin: 6px 0 0; color: var(--ink-3); line-height: 1.75; white-space: pre-wrap; }
</style>
