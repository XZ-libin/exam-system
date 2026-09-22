<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { attemptApi } from '../../api'

const route = useRoute()
const router = useRouter()

const recordId = Number(route.params.recordId)
const loading = ref(true)
const failed = ref(false)
const detail = ref(null)
const onlyWrong = ref(false)

const items = computed(() => (detail.value && detail.value.items) || [])
const wrongItems = computed(() => items.value.filter((item) => item.correctFlag === 0 || item.correctFlag === 2))
const published = computed(() => Number(detail.value && detail.value.scorePublished) === 1)
const waitingReview = computed(() => Number(detail.value && detail.value.status) === 1)
const statusClass = computed(() => {
  const status = Number(detail.value && detail.value.status)
  if (status === 1) return 'tag-warn'
  if (status === 2) return 'tag-ok'
  if (status === 3) return 'tag-quiet'
  return ''
})

const scoreCard = computed(() => {
  const data = detail.value || {}
  return {
    total: data.totalScore == null ? '--' : data.totalScore,
    full: data.fullScore == null ? '--' : data.fullScore,
    pass: data.passScore == null ? '--' : data.passScore,
    used: formatClock(data.usedSeconds),
    switchCount: data.switchCount == null ? '--' : data.switchCount
  }
})

function formatClock(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0))
  const mm = String(Math.floor(total / 60)).padStart(2, '0')
  const ss = String(total % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

function dayText(value) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '--'
}

function list(values) {
  return (values || []).map((item) => (item == null ? '' : String(item))).filter((item) => item !== '')
}

function optionText(item, key) {
  const hit = (item.options || []).find((opt) => String(opt.key) === String(key))
  return hit ? `${hit.key}. ${hit.text}` : String(key)
}

/** 把提交用的字符串答案翻译成人能读的内容 */
function answerLines(item, values) {
  const raw = list(values)
  if (!raw.length) return []
  if (item.qType === 1 || item.qType === 2) return raw.map((key) => optionText(item, key))
  if (item.qType === 3) return raw.map((key) => (String(key).toUpperCase() === 'T' ? '正确' : '错误'))
  if (item.qType === 4) return raw.map((text) => text.split('|').map((alt) => alt.trim()).filter(Boolean).join(' 或 '))
  return raw
}

function flagText(item) {
  if (item.reviewStatus === 1) return '待批改'
  if (item.correctFlag === 1) return '答对'
  if (item.correctFlag === 2) return '部分正确'
  if (item.correctFlag === 0) return '答错'
  return item.correctFlagName || '未判定'
}

function flagClass(item) {
  if (item.reviewStatus === 1) return 'tag tag-warn'
  if (item.correctFlag === 1) return 'tag tag-ok'
  if (item.correctFlag === 2) return 'tag tag-warn'
  if (item.correctFlag === 0) return 'tag tag-danger'
  return 'tag tag-quiet'
}

function itemScoreText(item) {
  if (item.reviewStatus === 1 || item.score == null) return '待教师批改'
  return `${item.score} / ${item.fullScore == null ? '--' : item.fullScore} 分`
}

/** 一次算好每题的展示文本，模板里不再重复推导 */
const rows = computed(() => {
  const showAnswer = published.value
  const list2 = onlyWrong.value ? wrongItems.value : items.value
  return list2.map((item) => ({
    item,
    mine: answerLines(item, item.userAnswer),
    right: showAnswer ? answerLines(item, item.standardAnswer) : [],
    analysis: showAnswer ? item.analysis : '',
    score: itemScoreText(item)
  }))
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    const data = await attemptApi.result(recordId)
    if (!data || !data.recordId) {
      failed.value = true
      return
    }
    detail.value = data
  } catch {
    failed.value = true
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
        <h1 class="page-title">{{ detail ? detail.examTitle : '成绩明细' }}</h1>
        <p v-if="detail" class="page-sub">
          {{ detail.paperTitle }} ·
          {{ [detail.studentName, detail.className].filter(Boolean).join(' · ') }} ·
          交卷时间 {{ dayText(detail.submitTime) }}
          <span class="tag" :class="statusClass">{{ detail.statusName }}</span>
        </p>
      </div>
      <div class="page-actions">
        <el-button text :loading="loading" @click="load">刷新</el-button>
        <el-button @click="router.replace('/student/scores')">我的成绩</el-button>
      </div>
    </header>

    <el-skeleton v-if="loading" :rows="8" animated />

    <section v-else-if="failed" class="card">
      <h2 class="card-title">暂时没能取到这份答卷</h2>
      <p class="card-hint">可以返回成绩列表，重新选择一份答卷查看。</p>
      <el-button type="primary" @click="router.replace('/student/scores')">去我的成绩</el-button>
    </section>

    <template v-else>
      <p v-if="waitingReview" class="notice notice-warn">
        本次试卷包含简答题，正在等待教师批改，批改完成并发布成绩后才能看到总分。
      </p>
      <p v-else-if="!published" class="notice">本场考试尚未发布成绩，暂时看不到总分与标准答案。</p>

      <section class="card">
        <div class="metrics">
          <div>
            <div class="metric-label">总分</div>
            <div class="metric-value num">
              {{ scoreCard.total }}<span class="metric-unit">/ {{ scoreCard.full }} 分</span>
            </div>
            <div class="metric-foot">
              <span v-if="detail.passFlag === 1" class="tag tag-ok">及格</span>
              <span v-else-if="detail.passFlag === 0" class="tag tag-danger">不及格</span>
              <span v-else>{{ published ? '暂未判定' : '成绩未发布' }}</span>
            </div>
          </div>
          <div>
            <div class="metric-label">及格线</div>
            <div class="metric-value num">{{ scoreCard.pass }}<span class="metric-unit">分</span></div>
            <div class="metric-foot">客观题 {{ detail.objectiveScore == null ? '--' : detail.objectiveScore }} 分 · 主观题 {{ detail.subjectiveScore == null ? '--' : detail.subjectiveScore }} 分</div>
          </div>
          <div>
            <div class="metric-label">用时</div>
            <div class="metric-value num">{{ scoreCard.used }}</div>
            <div class="metric-foot">从开考到交卷</div>
          </div>
          <div>
            <div class="metric-label">切屏</div>
            <div class="metric-value num">{{ scoreCard.switchCount }}<span class="metric-unit">次</span></div>
            <div class="metric-foot">答题期间离开页面即计一次</div>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="card-head">
          <div>
            <h2 class="card-title">逐题明细</h2>
            <p class="card-hint">共 {{ items.length }} 题，错题 {{ wrongItems.length }} 题（含部分正确）。</p>
          </div>
          <el-button v-if="wrongItems.length" text @click="onlyWrong = !onlyWrong">
            {{ onlyWrong ? '显示全部题目' : '只看错题' }}
          </el-button>
        </div>

        <el-empty v-if="!rows.length" :description="onlyWrong ? '这份答卷没有错题' : '这份试卷没有题目记录'" />

        <article v-for="row in rows" :key="row.item.answerItemId" class="item">
          <div class="item-head">
            <span class="item-no num">{{ row.item.sortNo }}</span>
            <span class="tag">{{ row.item.qTypeName }}</span>
            <span :class="flagClass(row.item)">{{ flagText(row.item) }}</span>
            <span class="item-score num">{{ row.score }}</span>
          </div>
          <p class="item-content">{{ row.item.content }}</p>

          <div class="qa">
            <div class="qa-row">
              <span class="qa-label">我的答案</span>
              <div class="qa-value">
                <p v-for="(line, idx) in row.mine" :key="idx" class="qa-line">
                  <span v-if="row.item.qType === 4 && row.mine.length > 1" class="num qa-idx">第 {{ idx + 1 }} 空</span>
                  {{ line }}
                </p>
                <p v-if="!row.mine.length" class="qa-line empty">未作答</p>
              </div>
            </div>

            <div v-if="row.right.length" class="qa-row">
              <span class="qa-label">正确答案</span>
              <div class="qa-value">
                <p v-for="(line, idx) in row.right" :key="idx" class="qa-line">
                  <span v-if="row.item.qType === 4 && row.right.length > 1" class="num qa-idx">第 {{ idx + 1 }} 空</span>
                  {{ line }}
                </p>
              </div>
            </div>

            <div v-if="row.analysis" class="qa-row">
              <span class="qa-label">解析</span>
              <div class="qa-value"><p class="qa-line">{{ row.analysis }}</p></div>
            </div>

            <div v-if="row.item.reviewComment" class="qa-row">
              <span class="qa-label">教师评语</span>
              <div class="qa-value"><p class="qa-line">{{ row.item.reviewComment }}</p></div>
            </div>
          </div>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.notice {
  margin: 0 0 18px;
  padding: 12px 16px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
  background: var(--paper-sunk);
  font-size: 13px;
  color: var(--ink-2);
}

.notice-warn {
  background: var(--warn-soft);
  border-color: var(--warn-soft);
  color: var(--warn);
}

.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px;
}

@media (max-width: 900px) {
  .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

.item {
  padding: 20px 0;
  border-top: 1px solid var(--line-soft);
}

.item-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.item-no {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}

.item-score {
  margin-left: auto;
  font-size: 13px;
  color: var(--ink-3);
}

.item-content {
  margin: 10px 0 14px;
  font-size: 15px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.qa { display: grid; gap: 10px; }

.qa-row {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.qa-label {
  font-size: 12px;
  letter-spacing: .04em;
  color: var(--ink-4);
  padding-top: 2px;
}

.qa-value { min-width: 0; }

.qa-line {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--ink);
  word-break: break-word;
  white-space: pre-wrap;
}

.qa-line + .qa-line { margin-top: 4px; }
.qa-line.empty { color: var(--ink-4); }
.qa-idx { margin-right: 6px; font-size: 12px; color: var(--ink-4); }

@media (max-width: 700px) {
  .qa-row { grid-template-columns: 1fr; gap: 4px; }
}
</style>
