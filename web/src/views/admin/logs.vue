<script setup>
import { onMounted, reactive, ref } from 'vue'
import { logApi } from '../../api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const filter = reactive({ page: 1, size: 20, action: null, keyword: '' })

const ACTIONS = ['LOGIN', 'LOGOUT', 'EXAM_ENTER', 'EXAM_SWITCH_LIMIT', 'GRADING', 'CHANGE_PASSWORD']

function dateText(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
}

async function load() {
  loading.value = true
  try {
    const data = await logApi.page({
      page: filter.page,
      size: filter.size,
      action: filter.action || undefined,
      keyword: filter.keyword || undefined
    })
    rows.value = data.records || []
    total.value = data.total || 0
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    loading.value = false
  }
}

function search() {
  filter.page = 1
  load()
}

function reset() {
  filter.page = 1
  filter.action = null
  filter.keyword = ''
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">操作日志</h1>
        <p class="page-sub">记录登录、考试进入与交卷、阅卷、改密等关键动作，用于回溯异常操作。</p>
      </div>
      <div class="page-actions">
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <section class="card">
      <div class="filters">
        <el-select v-model="filter.action" class="w-action" clearable placeholder="全部动作" @change="search">
          <el-option v-for="code in ACTIONS" :key="code" :label="code" :value="code" />
        </el-select>
        <el-input v-model.trim="filter.keyword" class="w-kw" clearable placeholder="搜索账号或操作对象"
                  @keyup.enter="search" @clear="search" />
        <el-button @click="search">查询</el-button>
        <el-button text @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <template #empty>
          <el-empty description="没有符合条件的操作记录" :image-size="60" />
        </template>
        <el-table-column label="时间" width="180">
          <template #default="{ row }"><span class="num">{{ dateText(row.createTime) }}</span></template>
        </el-table-column>
        <el-table-column label="账号" width="140">
          <template #default="{ row }">{{ row.username || '—' }}</template>
        </el-table-column>
        <el-table-column label="动作" width="180">
          <template #default="{ row }"><span class="tag tag-quiet">{{ row.action }}</span></template>
        </el-table-column>
        <el-table-column label="对象" min-width="180">
          <template #default="{ row }"><span class="cell-sub">{{ row.target || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="详情" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.detail || '—' }}</template>
        </el-table-column>
        <el-table-column label="IP" width="140">
          <template #default="{ row }"><span class="num cell-sub">{{ row.clientIp || '—' }}</span></template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="filter.page" v-model:page-size="filter.size" background
                     :total="total" :page-sizes="[20, 50, 100]"
                     layout="total, sizes, prev, pager, next, jumper"
                     @current-change="load" @size-change="load" />
    </section>
  </div>
</template>

<style scoped>
.filters { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 18px; }
.filters .w-action { width: 200px; }
.filters .w-kw { width: 240px; }
.cell-sub { font-size: 13px; color: var(--ink-3); }
</style>
