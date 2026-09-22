<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoryApi, paperApi } from '../../api'

const route = useRoute()
const router = useRouter()

const statusOptions = [
  { value: 0, label: '草稿' },
  { value: 1, label: '已发布' },
  { value: 2, label: '已归档' }
]
const buildOptions = [
  { value: 1, label: '人工组卷' },
  { value: 2, label: '规则组卷' }
]

function statusTag(value) {
  if (value === 1) return { text: '已发布', cls: 'tag-ok' }
  if (value === 2) return { text: '已归档', cls: 'tag-quiet' }
  return { text: '草稿', cls: 'tag-warn' }
}

function buildText(value) {
  return value === 2 ? '规则组卷' : '人工组卷'
}

function timeText(value) {
  if (!value) return '—'
  const text = String(value).replace('T', ' ')
  return text.length > 16 ? text.slice(0, 16) : text
}

const query = reactive({ page: 1, size: 10, keyword: '', status: '', categoryId: '', buildType: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const subjects = ref([])

async function load() {
  loading.value = true
  try {
    const res = await paperApi.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: typeof query.status === 'number' ? query.status : undefined,
      categoryId: query.categoryId || undefined,
      buildType: query.buildType || undefined
    })
    rows.value = res.records || []
    total.value = res.total || 0
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function resetFilters() {
  query.keyword = ''
  query.status = ''
  query.categoryId = ''
  query.buildType = ''
  search()
}

function flatten(nodes, prefix = []) {
  const out = []
  for (const node of nodes || []) {
    const path = [...prefix, node.name]
    out.push({ value: node.id, label: path.join(' › ') })
    out.push(...flatten(node.children, path))
  }
  return out
}

/* ---------------------------------------------------------------- 新建草稿 */

const createBox = reactive({ open: false, saving: false })
const createForm = reactive({ title: '', categoryId: null, suggestMinutes: 60, passScore: 60 })

function openCreate() {
  Object.assign(createForm, { title: '', categoryId: subjects.value[0] && subjects.value[0].value, suggestMinutes: 60, passScore: 60 })
  createBox.open = true
}

async function submitCreate() {
  if (!createForm.title.trim()) {
    ElMessage.warning('请先给试卷起个名字')
    return
  }
  createBox.saving = true
  try {
    const id = await paperApi.create({
      title: createForm.title.trim(),
      categoryId: createForm.categoryId || undefined,
      suggestMinutes: createForm.suggestMinutes,
      passScore: createForm.passScore
    })
    createBox.open = false
    router.push(`/admin/papers/edit/${id}`)
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    createBox.saving = false
  }
}

/* ---------------------------------------------------------------- 规则组卷 */

const qTypes = [
  { value: 1, label: '单选题' },
  { value: 2, label: '多选题' },
  { value: 3, label: '判断题' },
  { value: 4, label: '填空题' },
  { value: 5, label: '简答题' }
]
const levelOptions = [
  { value: 1, label: '很易' },
  { value: 2, label: '较易' },
  { value: 3, label: '中等' },
  { value: 4, label: '较难' },
  { value: 5, label: '很难' }
]

const autoBox = reactive({ open: false, saving: false })
const autoForm = reactive({ title: '', categoryId: null, suggestMinutes: 60, passScore: 60, rules: [] })
const autoCount = computed(() => autoForm.rules.reduce((sum, item) => sum + (Number(item.count) || 0), 0))

function newRule() {
  return { qType: 1, count: 5, difficulty: null, scorePerItem: 2 }
}

function openAuto() {
  Object.assign(autoForm, {
    title: '',
    categoryId: subjects.value[0] && subjects.value[0].value,
    suggestMinutes: 60,
    passScore: 60,
    rules: [newRule()]
  })
  autoBox.open = true
}

function addRule() {
  if (autoForm.rules.length >= 5) return
  autoForm.rules.push(newRule())
}

function dropRule(index) {
  autoForm.rules.splice(index, 1)
}

async function submitAuto() {
  if (!autoForm.title.trim()) {
    ElMessage.warning('请先给试卷起个名字')
    return
  }
  if (!autoForm.rules.length || !autoCount.value) {
    ElMessage.warning('至少配置一条抽题规则')
    return
  }
  autoBox.saving = true
  try {
    const id = await paperApi.autoGenerate({
      title: autoForm.title.trim(),
      categoryId: autoForm.categoryId || undefined,
      suggestMinutes: autoForm.suggestMinutes,
      passScore: autoForm.passScore,
      rules: autoForm.rules.map((item) => ({
        qType: item.qType,
        count: item.count,
        difficulty: item.difficulty || undefined,
        scorePerItem: item.scorePerItem || undefined
      }))
    })
    autoBox.open = false
    ElMessage.success('抽题完成，请核对整卷')
    router.push(`/admin/papers/edit/${id}`)
  } catch {
    /* 题量不足等原因由请求层提示，试卷保持草稿可继续调整 */
  } finally {
    autoBox.saving = false
  }
}

/* ---------------------------------------------------------------- 行操作 */

function goEdit(row) {
  router.push(`/admin/papers/edit/${row.id}`)
}

async function publish(row) {
  try {
    await ElMessageBox.confirm(
      `发布后「${row.title}」将被锁定，可以排进考试但不能再增删题目。`,
      '发布试卷',
      { confirmButtonText: '发布', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await paperApi.publish(row.id)
    ElMessage.success('试卷已发布')
    load()
  } catch {
    /* 提示已由请求层给出 */
  }
}

async function archive(row) {
  try {
    await ElMessageBox.confirm(`归档后「${row.title}」不再出现在组卷与排考列表中。`, '归档试卷', {
      confirmButtonText: '归档',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await paperApi.archive(row.id)
    ElMessage.success('试卷已归档')
    load()
  } catch {
    /* 提示已由请求层给出 */
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`删除后「${row.title}」不再可用，已被考试使用的试卷无法删除。`, '删除试卷', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await paperApi.remove(row.id)
    ElMessage.success('试卷已删除')
    if (rows.value.length === 1 && query.page > 1) query.page -= 1
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    load()
  }
}

onMounted(async () => {
  load()
  try {
    subjects.value = flatten(await categoryApi.tree())
  } catch {
    subjects.value = []
  }
  if (route.query.create) openCreate()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">试卷管理</h1>
        <p class="page-sub">从题库选题或按规则抽题组成试卷，发布后即可排进考试。</p>
      </div>
      <div class="page-actions">
        <el-button @click="openAuto">规则组卷</el-button>
        <el-button type="primary" @click="openCreate">新建试卷</el-button>
      </div>
    </div>

    <section class="card">
      <div class="filter-bar">
        <el-input
          v-model.trim="query.keyword"
          class="filter-keyword"
          placeholder="按试卷名称搜索"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="query.categoryId" class="filter-item" placeholder="全部科目" clearable filterable @change="search">
          <el-option v-for="item in subjects" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.status" class="filter-item" placeholder="全部状态" clearable @change="search">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.buildType" class="filter-item" placeholder="全部组卷方式" clearable @change="search">
          <el-option v-for="item in buildOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button @click="search">查询</el-button>
        <el-button text @click="resetFilters">重置条件</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="试卷" min-width="260">
          <template #default="{ row }">
            <div class="cell-strong">{{ row.title }}</div>
            <div class="cell-meta">{{ row.categoryName || '未归学科' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="题数" width="80">
          <template #default="{ row }"><span class="num">{{ row.questionCount ?? 0 }}</span></template>
        </el-table-column>
        <el-table-column label="总分" width="90">
          <template #default="{ row }"><span class="num">{{ row.totalScore ?? 0 }}</span></template>
        </el-table-column>
        <el-table-column label="及格线" width="90">
          <template #default="{ row }"><span class="num">{{ row.passScore ?? '—' }}</span></template>
        </el-table-column>
        <el-table-column label="组卷方式" width="110">
          <template #default="{ row }">{{ buildText(row.buildType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <span class="tag" :class="statusTag(row.status).cls">{{ statusTag(row.status).text }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建人" width="110">
          <template #default="{ row }">{{ row.creatorName || '—' }}</template>
        </el-table-column>
        <el-table-column label="被考次数" width="100">
          <template #default="{ row }"><span class="num">{{ row.examCount ?? 0 }}</span></template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }"><span class="num">{{ timeText(row.updateTime || row.createTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text :disabled="row.status === 1" :title="row.status === 1 ? '已发布锁定' : ''" @click="goEdit(row)">
              编辑
            </el-button>
            <el-button v-if="row.status !== 1" text @click="publish(row)">发布</el-button>
            <el-button v-if="row.status === 1" text @click="archive(row)">归档</el-button>
            <el-button text @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!loading" description="还没有试卷，先新建一张草稿卷吧" :image-size="72" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="search"
      />
    </section>

    <el-dialog v-model="createBox.open" title="新建试卷" width="480px">
      <el-form label-position="top">
        <el-form-item label="试卷名称" required>
          <el-input v-model.trim="createForm.title" placeholder="例如 数据结构期中测验" maxlength="60" />
        </el-form-item>
        <el-form-item label="所属科目">
          <el-select v-model="createForm.categoryId" filterable clearable placeholder="可留空，稍后在编辑页补充" class="full">
            <el-option v-for="item in subjects" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="建议时长（分钟）">
            <el-input-number v-model="createForm.suggestMinutes" :min="10" :max="300" :step="10" class="full" />
          </el-form-item>
          <el-form-item label="及格分">
            <el-input-number v-model="createForm.passScore" :min="1" :max="300" :step="5" class="full" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createBox.open = false">取消</el-button>
        <el-button type="primary" :loading="createBox.saving" @click="submitCreate">创建并组卷</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="autoBox.open" title="按规则抽题组卷" width="680px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="试卷名称" required>
            <el-input v-model.trim="autoForm.title" placeholder="例如 Java 基础阶段测验" maxlength="60" />
          </el-form-item>
          <el-form-item label="所属科目">
            <el-select v-model="autoForm.categoryId" filterable clearable placeholder="不限分类" class="full">
              <el-option v-for="item in subjects" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="建议时长（分钟）">
            <el-input-number v-model="autoForm.suggestMinutes" :min="10" :max="300" :step="10" class="full" />
          </el-form-item>
          <el-form-item label="及格分">
            <el-input-number v-model="autoForm.passScore" :min="1" :max="300" :step="5" class="full" />
          </el-form-item>
        </div>
      </el-form>

      <div class="rule-head">
        <h3 class="card-title">抽题规则</h3>
        <el-button text :disabled="autoForm.rules.length >= 5" @click="addRule">增加一行</el-button>
      </div>
      <div v-for="(rule, index) in autoForm.rules" :key="index" class="rule-row">
        <el-select v-model="rule.qType" class="rule-cell type">
          <el-option v-for="item in qTypes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input-number v-model="rule.count" :min="1" :max="50" class="rule-cell count" />
        <el-select v-model="rule.difficulty" class="rule-cell level" placeholder="难度不限" clearable>
          <el-option v-for="item in levelOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input-number
          v-model="rule.scorePerItem"
          :min="0.5"
          :max="50"
          :step="0.5"
          :precision="1"
          class="rule-cell score"
          placeholder="每题分值"
        />
        <el-button text :disabled="autoForm.rules.length <= 1" @click="dropRule(index)">删除</el-button>
      </div>
      <p class="rule-tip">
        共抽取 <span class="num">{{ autoCount }}</span> 道题。任一条规则在题库里凑不齐题量时，整卷会生成失败并提示可用题数，按提示下调即可。
      </p>

      <template #footer>
        <el-button @click="autoBox.open = false">取消</el-button>
        <el-button type="primary" :loading="autoBox.saving" @click="submitAuto">开始抽题</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.filter-keyword {
  flex: 1 1 200px;
  max-width: 300px;
}

.filter-item {
  width: 148px;
}

.cell-strong {
  font-weight: 500;
  color: var(--ink);
}

.cell-meta {
  font-size: 12px;
  color: var(--ink-4);
}

.el-table .el-button.is-text {
  padding-inline: 7px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.full {
  width: 100%;
}

.rule-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 4px;
  border-top: 1px solid var(--line-soft);
}

.rule-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 110px 130px 120px auto;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
}

.rule-cell.type {
  min-width: 0;
}

.rule-cell.count,
.rule-cell.level,
.rule-cell.score {
  width: 100%;
}

.rule-tip {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--ink-3);
}
</style>
