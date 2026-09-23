<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { attemptApi, examApi, scoreApi } from '../../api'
import http from '../../api/request'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const exams = ref([])
const filter = reactive({ page: 1, size: 10, examId: null, keyword: '', status: null, passFlag: null })

const exporting = ref(false)
const drawer = ref(false)
const detail = ref(null)
const detailLoading = ref(false)

const examName = computed(() => {
  const hit = exams.value.find((e) => e.id === filter.examId)
  return hit ? hit.title : '全部考试'
})

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

function dateText(value, withTime = true) {
  if (!value) {
    return '—'
  }
  const text = String(value).replace('T', ' ')
  return withTime ? text.slice(0, 16) : text.slice(0, 10)
}

function usedText(seconds) {
  if (seconds === null || seconds === undefined) {
    return '—'
  }
  const total1 = Math.max(0, Math.round(Number(seconds)))
  const m = Math.floor(total1 / 60)
  const s = total1 % 60
  return m > 0 ? `${m} 分 ${s} 秒` : `${s} 秒`
}

function resultOf(row) {
  if (row.status === 1) {
    return { text: '待阅卷', cls: 'tag-warn' }
  }
  if (row.scorePublished !== 1) {
    return { text: '未发布', cls: 'tag-quiet' }
  }
  if (row.passFlag === 1) {
    return { text: '及格', cls: 'tag-ok' }
  }
  if (row.passFlag === 0) {
    return { text: '不及格', cls: 'tag-danger' }
  }
  return { text: '—', cls: 'tag-quiet' }
}

function picked(value) {
  return value === '' || value === null || value === undefined ? undefined : value
}

async function load() {
  loading.value = true
  try {
    const data = await scoreApi.page({
      page: filter.page,
      size: filter.size,
      examId: picked(filter.examId),
      keyword: filter.keyword || undefined,
      status: picked(filter.status),
      passFlag: picked(filter.passFlag)
    })
    rows.value = data.records || []
    total.value = data.total || 0
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    loading.value = false
  }
}

async function loadExams() {
  try {
    const data = await examApi.page({ page: 1, size: 100 })
    exams.value = data.records || []
  } catch {
    /* 提示已由请求层给出 */
  }
}

function search() {
  filter.page = 1
  load()
}

function reset() {
  filter.page = 1
  filter.examId = null
  filter.keyword = ''
  filter.status = null
  filter.passFlag = null
  load()
}

async function openDetail(row) {
  drawer.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const data = await attemptApi.result(row.recordId)
    data.items = (data.items || []).map((it) => ({
      ...it,
      qTypeName: it.qTypeName ?? it.qtypeName
    }))
    detail.value = data
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    detailLoading.value = false
  }
}

function joinAnswer(list, emptyText = '未作答') {
  const items = (list || []).map((v) => String(v || '').trim()).filter(Boolean)
  return items.length ? items.join('　') : emptyText
}

function flagTag(item) {
  if (item.correctFlag === 1) return 'tag-ok'
  if (item.correctFlag === 2) return 'tag-warn'
  return 'tag-danger'
}

async function exportCsv() {
  if (exporting.value) {
    return
  }
  exporting.value = true
  try {
    const blob = await http.get('/scores/export', {
      params: filter.examId ? { examId: filter.examId } : {},
      responseType: 'blob'
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `成绩-${examName.value}-${dayjs().format('YYYYMMDD')}.csv`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    ElMessage.success(`已导出「${examName.value}」成绩`)
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  loadExams()
  load()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">成绩管理</h1>
        <p class="page-sub">汇总每场考试的答卷与得分，可按考试或学生检索后导出成绩表。</p>
      </div>
      <div class="page-actions">
        <el-button :loading="exporting" @click="exportCsv">导出 CSV</el-button>
      </div>
    </div>

    <section class="card">
      <div class="filters">
        <el-select v-model="filter.examId" class="w-exam" clearable filterable placeholder="全部考试" @change="search">
          <el-option v-for="e in exams" :key="e.id" :label="e.title" :value="e.id" />
        </el-select>
        <el-input v-model.trim="filter.keyword" class="w-kw" clearable placeholder="搜索学生姓名或学号"
                  @keyup.enter="search" @clear="search" />
        <el-select v-model="filter.status" class="w-st" clearable placeholder="全部状态" @change="search">
          <el-option label="作答中" :value="0" />
          <el-option label="待阅卷" :value="1" />
          <el-option label="已完成" :value="2" />
          <el-option label="超时强制交卷" :value="3" />
        </el-select>
        <el-select v-model="filter.passFlag" class="w-pass" clearable placeholder="及格与否" @change="search">
          <el-option label="及格" :value="1" />
          <el-option label="不及格" :value="0" />
        </el-select>
        <el-button text @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="recordId">
        <template #empty>
          <el-empty description="没有符合条件的成绩记录" :image-size="60" />
        </template>
        <el-table-column label="学生" min-width="110">
          <template #default="{ row }">
            <div class="cell-title">{{ row.studentName }}</div>
            <div class="cell-sub">{{ row.username }}</div>
          </template>
        </el-table-column>
        <el-table-column label="班级" min-width="88">
          <template #default="{ row }">{{ row.className || '—' }}</template>
        </el-table-column>
        <el-table-column label="考试" min-width="140">
          <template #default="{ row }">
            <div>{{ row.examTitle }}</div>
            <div class="cell-sub">{{ row.paperTitle }}</div>
          </template>
        </el-table-column>
        <el-table-column label="客观" width="68">
          <template #default="{ row }"><span class="num">{{ num(row.objectiveScore) }}</span></template>
        </el-table-column>
        <el-table-column label="主观" width="68">
          <template #default="{ row }"><span class="num">{{ num(row.subjectiveScore) }}</span></template>
        </el-table-column>
        <el-table-column label="总分" width="80">
          <template #default="{ row }">
            <div class="num cell-strong">{{ num(row.totalScore) }}</div>
            <div class="cell-sub num">/ {{ num(row.fullScore, 0) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <span class="tag" :class="resultOf(row).cls">{{ resultOf(row).text }}</span>
          </template>
        </el-table-column>
        <el-table-column label="交卷时间" min-width="120">
          <template #default="{ row }">
            <span class="cell-sub">{{ dateText(row.submitTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="切屏" width="60">
          <template #default="{ row }"><span class="num">{{ num(row.switchCount, 0) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="92">
          <template #default="{ row }">
            <el-button text size="small" @click="openDetail(row)">查看答卷</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="filter.page" v-model:page-size="filter.size" background
                     :total="total" :page-sizes="[10, 20, 50]"
                     layout="total, sizes, prev, pager, next, jumper"
                     @current-change="load" @size-change="load" />
    </section>

    <el-drawer v-model="drawer" title="答卷明细" size="720px">
      <div v-loading="detailLoading" class="detail">
        <template v-if="detail">
          <div class="detail-head">
            <div>
              <h2 class="card-title">{{ detail.studentName }} · {{ detail.examTitle }}</h2>
              <p class="card-hint">
                {{ detail.username }} · {{ detail.className || '未分班' }} ·
                交卷 {{ dateText(detail.submitTime) }} · 用时 {{ usedText(detail.usedSeconds) }} ·
                切屏 {{ num(detail.switchCount, 0) }} 次
              </p>
            </div>
            <div class="detail-score">
              <div class="num detail-score-value">{{ num(detail.totalScore) }}
                <span class="metric-unit">/ {{ num(detail.fullScore, 0) }}</span>
              </div>
              <span v-if="detail.scorePublished === 1" class="tag" :class="detail.passFlag === 1 ? 'tag-ok' : 'tag-danger'">
                {{ detail.passFlag === 1 ? '及格' : '不及格' }}
              </span>
              <span v-else class="tag tag-warn">{{ detail.status === 1 ? '待阅卷' : '成绩未发布' }}</span>
            </div>
          </div>

          <div class="ans">
            <div class="ans-row ans-head">
              <span>题目</span><span>我的答案</span><span>正确答案</span><span class="ans-c">得分</span>
            </div>
            <div v-for="item in detail.items" :key="item.answerItemId" class="ans-row">
              <div class="ans-q">
                <div class="ans-no">
                  <span class="num">{{ item.sortNo }}</span> · {{ item.qTypeName }}
                  <span class="tag" :class="flagTag(item)">{{ item.correctFlagName }}</span>
                </div>
                <div class="ans-content">{{ item.content }}</div>
                <div v-if="item.reviewComment" class="cell-sub">评语：{{ item.reviewComment }}</div>
              </div>
              <div class="ans-cell">{{ joinAnswer(item.userAnswer, '未作答') }}</div>
              <div class="ans-cell ans-ref">{{ joinAnswer(item.standardAnswer, '—') }}</div>
              <div class="ans-cell num ans-c">
                {{ num(item.score) }}<span class="cell-sub"> / {{ num(item.fullScore, 0) }}</span>
              </div>
            </div>
            <div v-if="!detail.items || !detail.items.length" class="ans-empty">
              <el-empty description="这份答卷还没有答题记录" :image-size="56" />
            </div>
          </div>
        </template>
        <el-empty v-else-if="!detailLoading" description="暂无答卷数据" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.filters { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 18px; }
.filters .w-exam { width: 230px; }
.filters .w-kw { width: 200px; }
.filters .w-st { width: 140px; }
.filters .w-pass { width: 120px; }

.cell-title { font-weight: 500; color: var(--ink); }
.cell-sub { font-size: 12px; color: var(--ink-3); }
.cell-strong { font-weight: 600; }

.detail { min-height: 260px; }
.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line-soft);
}
.detail-score { text-align: right; flex: none; }
.detail-score-value { font-size: 26px; font-weight: 600; letter-spacing: -.02em; }

.ans { margin-top: 8px; }
.ans-row {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 3fr) minmax(0, 3fr) 92px;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid var(--line-soft);
  align-items: start;
}
.ans-head {
  padding: 10px 0;
  font-size: 12px;
  letter-spacing: .06em;
  color: var(--ink-4);
  border-bottom-color: var(--line);
}
.ans-c { text-align: right; }
.ans-no { font-size: 12px; color: var(--ink-3); display: flex; align-items: center; gap: 8px; }
.ans-content { margin-top: 4px; color: var(--ink); line-height: 1.65; }
.ans-cell { color: var(--ink-2); line-height: 1.65; word-break: break-word; white-space: pre-wrap; }
.ans-ref { color: var(--ink-3); }
.ans-empty { padding: 24px 0; }
</style>
