<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { statApi } from '../../api'
import MiniChart from '../../components/mini-chart.vue'

const router = useRouter()

const loading = ref(true)
const data = ref(null)

/** mini-chart 的统一基调，tooltip 需要整块覆盖，这里带上同样的样式 */
const tip = {
  backgroundColor: 'rgba(255,255,255,.94)',
  borderColor: '#d2d2d7',
  borderWidth: 1,
  textStyle: { color: '#1d1d1f', fontSize: 12 },
  extraCssText: 'border-radius:12px;box-shadow:0 8px 24px rgba(0,0,0,.08);'
}

const trend = computed(() => (data.value && data.value.trend) || [])
const types = computed(() => (data.value && data.value.typeAccuracy) || [])
const radarRows = computed(() => ((data.value && data.value.difficultyRadar) || [])
  .filter((row) => Number(row.answerCount) > 0))
const hasTrend = computed(() => trend.value.length > 0)
const hasTypes = computed(() => types.value.length > 0)
const hasRadar = computed(() => radarRows.value.length > 0)
const typeHeight = computed(() => `${Math.min(340, Math.max(220, types.value.length * 44))}px`)

const metrics = computed(() => {
  const source = data.value || {}
  return {
    examCount: source.examCount == null ? 0 : source.examCount,
    submitCount: source.submitCount == null ? 0 : source.submitCount,
    avgRate: source.avgRate == null ? 0 : source.avgRate,
    passCount: source.passCount == null ? 0 : source.passCount,
    wrongCount: source.wrongCount == null ? 0 : source.wrongCount
  }
})

const trendOption = computed(() => ({
  tooltip: {
    ...tip,
    trigger: 'axis',
    formatter: (points) => {
      const row = trend.value[points[0].dataIndex] || {}
      const score = row.score == null ? '待批改' : `${row.score} 分`
      return `${row.examTitle || ''}<br/>${row.date || ''} · ${row.rate}% · ${score}`
    }
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trend.value.map((row) => (row.date || '').slice(5)),
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#d2d2d7' } },
    axisLabel: { color: '#a1a1a6', fontSize: 11 }
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 100,
    interval: 25,
    axisLabel: { formatter: '{value}%', color: '#a1a1a6', fontSize: 11 },
    splitLine: { lineStyle: { color: '#e8e8ed' } }
  },
  series: [{
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 7,
    showSymbol: trend.value.length <= 14,
    lineStyle: { width: 2, color: '#1d1d1f' },
    itemStyle: { color: '#1d1d1f' },
    areaStyle: { color: 'rgba(29,29,31,.06)' },
    data: trend.value.map((row) => Number(row.rate) || 0)
  }]
}))

const typeOption = computed(() => {
  const list = [...types.value].reverse()
  return {
    tooltip: {
      ...tip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (points) => {
        const row = list[points[0].dataIndex] || {}
        return `${row.qTypeName || ''}<br/>做了 ${row.answerCount} 题 · 正确率 ${row.accuracy}%`
      }
    },
    grid: { left: 8, right: 46, top: 10, bottom: 4, containLabel: true },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { formatter: '{value}%', color: '#a1a1a6', fontSize: 11 },
      splitLine: { lineStyle: { color: '#e8e8ed' } }
    },
    yAxis: {
      type: 'category',
      data: list.map((row) => row.qTypeName),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#d2d2d7' } },
      axisLabel: { color: '#6e6e73', fontSize: 12 }
    },
    series: [{
      type: 'bar',
      barWidth: 12,
      itemStyle: { color: '#1d1d1f', borderRadius: [0, 6, 6, 0] },
      label: { show: true, position: 'right', formatter: '{c}%', color: '#6e6e73', fontSize: 11 },
      data: list.map((row) => Number(row.accuracy) || 0)
    }]
  }
})

const radarOption = computed(() => ({
  tooltip: { ...tip },
  radar: {
    indicator: radarRows.value.map((row) => ({ name: row.label, max: 100 })),
    radius: '62%',
    center: ['50%', '54%'],
    axisName: { color: '#6e6e73', fontSize: 11 },
    axisLine: { lineStyle: { color: '#e8e8ed' } },
    splitLine: { lineStyle: { color: '#e8e8ed' } },
    splitArea: { show: false }
  },
  series: [{
    type: 'radar',
    symbol: 'circle',
    symbolSize: 5,
    lineStyle: { width: 2, color: '#1d1d1f' },
    itemStyle: { color: '#1d1d1f' },
    areaStyle: { color: 'rgba(29,29,31,.08)' },
    data: [{
      name: '正确率',
      value: radarRows.value.map((row) => Number(row.accuracy) || 0)
    }]
  }]
}))

async function load() {
  loading.value = true
  try {
    data.value = await statApi.myAnalysis()
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
        <h1 class="page-title">学习分析</h1>
        <p class="page-sub">统计口径是已发布成绩的场次与已判分的题目，未出分的场次不计入。</p>
      </div>
      <div class="page-actions">
        <el-button text :loading="loading" @click="load">刷新</el-button>
        <el-button @click="router.push('/student/scores')">我的成绩</el-button>
      </div>
    </header>

    <el-skeleton v-if="loading" :rows="8" animated />

    <template v-else>
      <section class="card metrics">
        <div>
          <div class="metric-label">交卷次数</div>
          <div class="metric-value num">{{ metrics.submitCount }}<span class="metric-unit">次</span></div>
          <div class="metric-foot">共 {{ metrics.examCount }} 场考试</div>
        </div>
        <div>
          <div class="metric-label">平均得分率</div>
          <div class="metric-value num">{{ metrics.avgRate }}<span class="metric-unit">%</span></div>
          <div class="metric-foot">按已出分的答卷平均</div>
        </div>
        <div>
          <div class="metric-label">及格场次</div>
          <div class="metric-value num">{{ metrics.passCount }}<span class="metric-unit">场</span></div>
          <div class="metric-foot">达到试卷及格线的次数</div>
        </div>
        <div>
          <div class="metric-label">错题数</div>
          <div class="metric-value num">{{ metrics.wrongCount }}<span class="metric-unit">题</span></div>
          <div class="metric-foot">在成绩明细里点「只看错题」回顾</div>
        </div>
      </section>

      <section class="card">
        <div class="card-head">
          <div>
            <h2 class="card-title">成绩趋势</h2>
            <p class="card-hint">每次交卷的得分率变化</p>
          </div>
        </div>
        <MiniChart v-if="hasTrend" :option="trendOption" height="260px" />
        <el-empty v-else description="还没有已发布成绩的作答记录" />
      </section>

      <div class="grid grid-2">
        <section class="card">
          <div class="card-head">
            <div>
              <h2 class="card-title">题型正确率</h2>
              <p class="card-hint">按题型汇总已判分的题目</p>
            </div>
          </div>
          <MiniChart v-if="hasTypes" :option="typeOption" :height="typeHeight" />
          <el-empty v-else description="还没有可统计的题目" />
        </section>

        <section class="card">
          <div class="card-head">
            <div>
              <h2 class="card-title">难度分布</h2>
              <p class="card-hint">各难度题目的正确率</p>
            </div>
          </div>
          <MiniChart v-if="hasRadar" :option="radarOption" height="300px" />
          <el-empty v-else description="还没有可统计的难度数据" />
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px;
}

.grid-2 { margin-top: 20px; }

@media (max-width: 1080px) {
  .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
}
</style>
