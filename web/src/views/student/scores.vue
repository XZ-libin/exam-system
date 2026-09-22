<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { scoreApi } from '../../api'

const router = useRouter()

const loading = ref(true)
const rows = ref([])
const pager = reactive({ page: 1, size: 10, total: 0 })

function statusClass(status) {
  if (status === 1) return 'tag tag-warn'
  if (status === 2) return 'tag tag-ok'
  if (status === 3) return 'tag tag-quiet'
  return 'tag'
}

function dayText(value) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '--'
}

function scoreText(row) {
  if (row.totalScore == null) return '--'
  return row.fullScore == null ? row.totalScore : `${row.totalScore} / ${row.fullScore}`
}

async function load() {
  loading.value = true
  try {
    const data = await scoreApi.my({ page: pager.page, size: pager.size })
    rows.value = (data && data.records) || []
    pager.total = (data && data.total) || 0
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    loading.value = false
  }
}

function changePage(page) {
  pager.page = page
  load()
}

function openDetail(row) {
  router.push({ name: 'studentResult', params: { recordId: row.recordId } })
}

function resume(row) {
  router.push({ name: 'studentTake', params: { examId: row.examId } })
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1 class="page-title">我的成绩</h1>
        <p class="page-sub">每一场考试的每次作答都在这里，分数以教师发布的成绩为准。</p>
      </div>
      <div class="page-actions">
        <el-button text :loading="loading" @click="load">刷新</el-button>
        <el-button @click="router.push('/student/analysis')">学习分析</el-button>
      </div>
    </header>

    <section class="card table-card">
      <el-table v-loading="loading" :data="rows" empty-text="还没有作答记录">
        <el-table-column label="考试" min-width="240">
          <template #default="{ row }">
            <div class="cell-title">{{ row.examTitle }}</div>
            <div class="cell-meta">{{ row.paperTitle }} · 第 {{ row.attemptNo }} 次作答</div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="116">
          <template #default="{ row }">
            <span :class="statusClass(row.status)">{{ row.statusName }}</span>
          </template>
        </el-table-column>

        <el-table-column label="得分" width="140">
          <template #default="{ row }">
            <div class="num cell-score">{{ scoreText(row) }}</div>
            <div v-if="row.scoreHint" class="cell-meta">{{ row.scoreHint }}</div>
          </template>
        </el-table-column>

        <el-table-column label="是否及格" width="100">
          <template #default="{ row }">
            <span v-if="row.passFlag === 1" class="tag tag-ok">及格</span>
            <span v-else-if="row.passFlag === 0" class="tag tag-danger">不及格</span>
            <span v-else class="cell-meta">--</span>
          </template>
        </el-table-column>

        <el-table-column label="交卷时间" width="150">
          <template #default="{ row }">
            <span class="num">{{ dayText(row.submitTime) }}</span>
            <div v-if="row.switchCount" class="cell-meta">切屏 {{ row.switchCount }} 次</div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" align="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" text @click="resume(row)">继续作答</el-button>
            <el-button v-else text @click="openDetail(row)">查看明细</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="pager.total > pager.size"
        :current-page="pager.page"
        :page-size="pager.size"
        :total="pager.total"
        layout="total, prev, pager, next"
        background
        @current-change="changePage"
      />
    </section>
  </div>
</template>

<style scoped>
.table-card { padding: 8px 24px 20px; }

.cell-title { font-weight: 500; color: var(--ink); }

.cell-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--ink-3);
}

.cell-score { font-size: 15px; font-weight: 600; }
</style>
