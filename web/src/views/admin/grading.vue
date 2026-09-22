<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { gradingApi } from '../../api'

const listLoading = ref(false)
const list = ref([])
const total = ref(0)
const filter = reactive({ page: 1, size: 10, keyword: '' })

const currentId = ref(null)
const detail = ref(null)
const detailLoading = ref(false)
const submitting = ref(false)
const drafts = reactive({})

const pendingItems = computed(() => (detail.value?.items || []).filter((it) => it.reviewStatus === 1))

const subjectiveNow = computed(() => {
  const items = detail.value?.items || []
  return items.reduce((sum, it) => {
    if (it.reviewStatus === 1) {
      const draft = drafts[it.answerItemId]
      return sum + Number(draft && draft.score ? draft.score : 0)
    }
    if (it.reviewStatus === 2) {
      return sum + Number(it.score || 0)
    }
    // 客观题的分值已经含在 objectiveScore 里，这里不能重复累加
    return sum
  }, 0)
})

const objectiveNow = computed(() => Number(detail.value?.objectiveScore || 0))
const totalNow = computed(() => objectiveNow.value + subjectiveNow.value)

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

function dateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

function trim(value) {
  return value === null || value === undefined || value === '' ? '—' : num(value, 2)
}

async function loadList(keepSelection = true) {
  listLoading.value = true
  try {
    const data = await gradingApi.pending({
      page: filter.page,
      size: filter.size,
      keyword: filter.keyword || undefined
    })
    list.value = data.records || []
    total.value = data.total || 0
    if (!keepSelection) {
      return
    }
    if (currentId.value && !list.value.some((row) => row.recordId === currentId.value)) {
      currentId.value = null
      detail.value = null
    }
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    listLoading.value = false
  }
}

function search() {
  filter.page = 1
  loadList()
}

function draftOf(item) {
  return drafts[item.answerItemId] || { score: 0, comment: '' }
}

function ensureDraft(item) {
  if (!drafts[item.answerItemId]) {
    drafts[item.answerItemId] = { score: Number(item.score || 0), comment: item.reviewComment || '' }
  }
  return drafts[item.answerItemId]
}

function setScore(item, value) {
  ensureDraft(item).score = Number(value || 0)
}

function setComment(item, value) {
  ensureDraft(item).comment = value
}

function resetDrafts() {
  Object.keys(drafts).forEach((key) => delete drafts[key])
  ;(detail.value?.items || []).forEach((it) => {
    if (it.reviewStatus === 1) {
      drafts[it.answerItemId] = { score: Number(it.score || 0), comment: it.reviewComment || '' }
    }
  })
}

async function openRecord(row) {
  if (row.recordId === currentId.value) {
    return
  }
  detailLoading.value = true
  try {
    const data = await gradingApi.detail(row.recordId)
    data.items = (data.items || []).map((it) => ({
      ...it,
      qType: it.qType ?? it.qtype,
      qTypeName: it.qTypeName ?? it.qtypeName
    }))
    detail.value = data
    currentId.value = row.recordId
    resetDrafts()
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    detailLoading.value = false
  }
}

function giveFullScore(item) {
  setScore(item, item.fullScore)
}

const isChoice = (item) => [1, 2, 3].includes(Number(item.qType))
const isEssay = (item) => Number(item.qType) === 5

function pickedLines(item) {
  const picked = item.userAnswer || []
  const options = item.options || []
  return picked.map((value) => {
    const key = String(value).trim()
    const hit = options.find((o) => o.key === key)
    return hit ? `${key}. ${hit.text}` : key
  })
}

function blankLines(item) {
  return (item.userAnswer || []).map((value, index) => ({ index: index + 1, text: value || '' }))
}

function answerIsEmpty(item) {
  return !(item.userAnswer || []).some((value) => String(value || '').trim())
}

function flagTag(item) {
  if (item.correctFlag === 1) return 'tag-ok'
  if (item.correctFlag === 2) return 'tag-warn'
  return 'tag-danger'
}

async function submitGrading() {
  if (!detail.value || submitting.value) {
    return
  }
  const index = list.value.findIndex((row) => row.recordId === currentId.value)
  const body = {
    recordId: detail.value.recordId,
    finish: true,
    items: pendingItems.value.map((it) => ({
      answerItemId: it.answerItemId,
      score: Number(ensureDraft(it).score || 0),
      comment: (ensureDraft(it).comment || '').trim() || null
    }))
  }
  submitting.value = true
  try {
    const result = await gradingApi.submit(body)
    ElMessage.success(`已批改 ${num(result.gradedCount, 0)} 题，总分 ${trim(result.totalScore)} 分`)
    await loadList(false)
    currentId.value = null
    detail.value = null
    const next = list.value.length ? list.value[Math.min(index, list.value.length - 1)] : null
    if (next) {
      await openRecord(next)
    } else {
      ElMessage.success('待阅卷答卷已全部完成')
    }
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    submitting.value = false
  }
}

onMounted(() => loadList())
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">主观题阅卷</h1>
        <p class="page-sub">客观题由系统判分，这里只处理需要人工给分的题目；提交合分后成绩才会进入成绩表。</p>
      </div>
      <div class="page-actions">
        <span class="tag tag-quiet">待阅卷 {{ num(total, 0) }} 份</span>
      </div>
    </div>

    <div class="board">
      <aside class="card queue">
        <div class="card-head">
          <div>
            <h2 class="card-title">待阅清单</h2>
            <p class="card-hint">按交卷顺序排列</p>
          </div>
        </div>
        <el-input v-model.trim="filter.keyword" clearable placeholder="搜索学生姓名或学号"
                  @keyup.enter="search" @clear="search" />
        <div v-loading="listLoading" class="queue-list">
          <button v-for="row in list" :key="row.recordId" type="button" class="nav-item pick"
                  :class="{ active: row.recordId === currentId }" @click="openRecord(row)">
            <span class="pick-main">
              <span class="pick-name">{{ row.studentName }}</span>
              <span class="pick-meta">{{ row.examTitle }} · {{ dateTime(row.submitTime) }}</span>
            </span>
            <span class="pick-count" :class="{ active: row.recordId === currentId }">{{ num(row.pendingCount, 0) }}</span>
          </button>
          <el-empty v-if="!listLoading && !list.length" description="暂无待阅卷答卷" :image-size="56" />
        </div>
        <el-pagination v-model:current-page="filter.page" v-model:page-size="filter.size" background small
                       :total="total" layout="prev, pager, next" @current-change="() => loadList()" />
      </aside>

      <main class="card panel" v-loading="detailLoading">
        <template v-if="detail">
          <div class="panel-head">
            <div class="grid grid-4 metrics">
              <div>
                <div class="metric-label">学生</div>
                <div class="metric-value name">{{ detail.studentName }}</div>
                <div class="metric-foot">{{ detail.username }} · {{ detail.className || '未分班' }}</div>
              </div>
              <div>
                <div class="metric-label">考试</div>
                <div class="metric-value name">{{ detail.examTitle }}</div>
                <div class="metric-foot">交卷于 {{ dateTime(detail.submitTime) }}</div>
              </div>
              <div>
                <div class="metric-label">客观题分</div>
                <div class="metric-value num">{{ num(objectiveNow) }}</div>
                <div class="metric-foot">系统判分，不可人工修改</div>
              </div>
              <div>
                <div class="metric-label">当前合计</div>
                <div class="metric-value num">{{ num(totalNow) }}</div>
                <div class="metric-foot">满分 {{ trim(detail.fullScore) }}</div>
              </div>
            </div>
          </div>

          <article v-for="item in detail.items" :key="item.answerItemId" class="q">
            <div class="q-head">
              <div>
                <span class="q-no">第 {{ item.sortNo }} 题</span>
                <span class="q-type">{{ item.qTypeName }}</span>
              </div>
              <span class="q-score">本题 {{ trim(item.fullScore) }} 分</span>
            </div>
            <p class="q-content">{{ item.content }}</p>

            <div class="q-block">
              <div class="q-label">学生答案</div>
              <p v-if="answerIsEmpty(item)" class="q-blank">未作答</p>
              <template v-else-if="isEssay(item)">
                <p v-for="(text, i) in item.userAnswer" :key="i" class="q-essay">{{ text }}</p>
              </template>
              <ul v-else-if="isChoice(item)" class="q-lines">
                <li v-for="line in pickedLines(item)" :key="line">{{ line }}</li>
              </ul>
              <ul v-else class="q-lines">
                <li v-for="b in blankLines(item)" :key="b.index">
                  第 {{ b.index }} 空：{{ b.text || '未作答' }}
                </li>
              </ul>
            </div>

            <div class="q-block">
              <div class="q-label">参考答案与解析</div>
              <p class="q-ref">{{ (item.standardAnswer || []).join('　') || '—' }}</p>
              <p v-if="item.analysis" class="q-ref">{{ item.analysis }}</p>
            </div>

            <div v-if="item.reviewStatus === 1" class="q-grade">
              <div class="q-grade-row">
                <span class="q-label">给分</span>
                <el-input-number :model-value="draftOf(item).score" @update:model-value="(v) => setScore(item, v)"
                                 :min="0" :max="Number(item.fullScore || 0)" :step="0.5"
                                 :precision="1" controls-position="right" />
                <el-button size="small" @click="giveFullScore(item)">给满分</el-button>
                <span class="q-unit">得分 {{ trim(draftOf(item).score) }}</span>
              </div>
              <el-input :model-value="draftOf(item).comment" @update:model-value="(v) => setComment(item, v)"
                        maxlength="200" show-word-limit type="textarea" :rows="2"
                        placeholder="评语（选填，成绩发布后学生可见）" class="q-comment" />
            </div>
            <div v-else class="q-auto">
              <span class="tag" :class="flagTag(item)">{{ item.correctFlagName || '系统已判分' }}</span>
              <span class="q-auto-score num">得分 {{ trim(item.score) }}</span>
              <span v-if="item.reviewComment" class="q-auto-comment">评语：{{ item.reviewComment }}</span>
            </div>
          </article>

          <div class="panel-foot">
            <div class="panel-foot-text">
              本次需人工批改 {{ num(pendingItems.length, 0) }} 题 · 合计
              <span class="num">{{ num(totalNow) }}</span> 分
            </div>
            <el-button type="primary" :loading="submitting" :disabled="!pendingItems.length" @click="submitGrading">
              {{ pendingItems.length ? '提交并合分' : '本份已无待阅题目' }}
            </el-button>
          </div>
        </template>

        <el-empty v-else description="从左侧清单选择一份答卷开始阅卷" :image-size="70" />
      </main>
    </div>
  </div>
</template>

<style scoped>
.board {
  display: grid;
  grid-template-columns: 312px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
.board > .card { margin-top: 0; }
@media (max-width: 1024px) {
  .board { grid-template-columns: minmax(0, 1fr); }
}

.queue { padding: 20px; }
.queue-list { margin-top: 14px; min-height: 220px; }
.pick {
  width: 100%;
  border: 0;
  font: inherit;
  text-align: left;
  cursor: pointer;
  align-items: center;
  justify-content: space-between;
}
.pick-main { min-width: 0; display: grid; }
.pick-name { font-size: 14px; font-weight: 500; }
.pick-meta {
  font-size: 12px;
  opacity: .72;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pick-count {
  flex: none;
  min-width: 24px;
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--line-soft);
  color: var(--ink-2);
  font-size: 12px;
  font-weight: 600;
  text-align: center;
}
.pick-count.active { background: rgba(255, 255, 255, .22); color: #fff; }

.panel { position: relative; min-height: 420px; }
.panel-head {
  padding-bottom: 20px;
  border-bottom: 1px solid var(--line-soft);
}
.metrics .name { font-size: 20px; }

.q { padding: 22px 0; border-bottom: 1px solid var(--line-soft); }
.q:last-of-type { border-bottom: 0; }
.q-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.q-no { font-size: 15px; font-weight: 600; color: var(--ink); }
.q-type { margin-left: 8px; font-size: 12px; color: var(--ink-3); }
.q-score { font-size: 12px; color: var(--ink-4); }
.q-content { margin: 10px 0 0; color: var(--ink); line-height: 1.7; white-space: pre-wrap; }

.q-block { margin-top: 14px; }
.q-label { font-size: 12px; letter-spacing: .06em; color: var(--ink-4); margin-bottom: 4px; }
.q-lines { margin: 0; padding-left: 18px; color: var(--ink-2); }
.q-lines li { margin-top: 2px; }
.q-essay {
  margin: 0;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  background: var(--paper-sunk);
  color: var(--ink);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
.q-blank { margin: 0; color: var(--ink-4); }
.q-ref { margin: 0; color: var(--ink-3); line-height: 1.75; white-space: pre-wrap; word-break: break-word; }

.q-grade { margin-top: 16px; }
.q-grade-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.q-grade-row .q-label { margin: 0; }
.q-unit { font-size: 13px; color: var(--ink-3); }
.q-comment { margin-top: 10px; }

.q-auto { display: flex; align-items: center; gap: 10px; margin-top: 16px; flex-wrap: wrap; }
.q-auto-score { font-size: 13px; color: var(--ink-2); }
.q-auto-comment { font-size: 13px; color: var(--ink-3); }

.panel-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--line-soft);
}
.panel-foot-text { font-size: 13px; color: var(--ink-3); }
</style>
