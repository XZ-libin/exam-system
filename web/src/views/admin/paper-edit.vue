<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoryApi, paperApi, questionApi } from '../../api'

const route = useRoute()
const router = useRouter()

const paperId = computed(() => Number(route.params.id))
const hasId = computed(() => Number.isFinite(paperId.value) && paperId.value > 0)

const qTypeMap = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题' }
const levelMap = { 1: '很易', 2: '较易', 3: '中等', 4: '较难', 5: '很难' }
const qTypes = Object.entries(qTypeMap).map(([value, label]) => ({ value: Number(value), label }))

function readJson(text) {
  if (!text) return null
  if (typeof text !== 'string') return text
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

/* ---------------------------------------------------------------- 整卷 */

const paper = ref({})
const questions = ref([])
const loading = ref(false)

const published = computed(() => paper.value.status === 1)
const draftOnly = computed(() => paper.value.status === 0)
const statusText = computed(() => (published.value ? '已发布' : paper.value.status === 2 ? '已归档' : '草稿'))
const chosenIds = computed(() => new Set(questions.value.map((item) => item.questionId)))
const totalScore = computed(() => Number(questions.value.reduce((sum, item) => sum + (Number(item.score) || 0), 0).toFixed(2)))

function toRows(list) {
  return (list || []).map((item) => ({
    questionId: item.questionId,
    qType: item.qType,
    content: item.content,
    difficulty: item.difficulty,
    score: Number(item.score) || 0,
    options: readJson(item.options) || [],
    answer: readJson(item.answer) || [],
    analysis: item.analysis || ''
  }))
}

async function loadPaper() {
  if (!hasId.value) return
  loading.value = true
  try {
    const res = await paperApi.detail(paperId.value)
    paper.value = res || {}
    questions.value = toRows(res && res.questions)
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    loading.value = false
  }
}

function answerText(row) {
  if (row.qType === 3) return row.answer[0] === 'F' ? '错误' : '正确'
  if (row.qType === 4) return row.answer.join('、')
  if (row.qType === 5) return row.answer.join('')
  return row.answer.join('、')
}

/* ---------------------------------------------------------------- 题库选题 */

const subjects = ref([])
const bank = reactive({ page: 1, size: 10, categoryId: '', qType: '', keyword: '' })
const bankRows = ref([])
const bankTotal = ref(0)
const bankLoading = ref(false)
const adding = ref(null)

async function loadBank() {
  bankLoading.value = true
  try {
    const res = await questionApi.page({
      page: bank.page,
      size: bank.size,
      categoryId: bank.categoryId || undefined,
      qType: bank.qType || undefined,
      keyword: bank.keyword || undefined
    })
    bankRows.value = res.records || []
    bankTotal.value = res.total || 0
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    bankLoading.value = false
  }
}

function searchBank() {
  bank.page = 1
  loadBank()
}

async function addQuestion(row) {
  adding.value = row.id
  try {
    await paperApi.addQuestions(paperId.value, [{ questionId: row.id }])
    ElMessage.success('已加入试卷')
    await loadPaper()
    loadBank()
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    adding.value = null
  }
}

async function saveScore(row) {
  if (!Number.isFinite(row.score) || row.score <= 0) {
    ElMessage.warning('每题分值要大于 0')
    loadPaper()
    return
  }
  try {
    await paperApi.updateScore(paperId.value, row.questionId, row.score)
    await loadPaper()
  } catch {
    loadPaper()
  }
}

async function dropQuestion(row) {
  try {
    await paperApi.removeQuestion(paperId.value, row.questionId)
    ElMessage.success('已从试卷移除')
    await loadPaper()
    loadBank()
  } catch {
    /* 提示已由请求层给出 */
  }
}

/* ---------------------------------------------------------------- 信息 / 预览 / 发布 */

const infoBox = reactive({ open: false, saving: false })
const infoForm = reactive({ title: '', description: '', categoryId: null, passScore: 60, suggestMinutes: 60 })

function openInfo() {
  Object.assign(infoForm, {
    title: paper.value.title || '',
    description: paper.value.description || '',
    categoryId: paper.value.categoryId || null,
    passScore: Number(paper.value.passScore) || 60,
    suggestMinutes: Number(paper.value.suggestMinutes) || 60
  })
  infoBox.open = true
}

async function submitInfo() {
  if (!infoForm.title.trim()) {
    ElMessage.warning('试卷名称不能为空')
    return
  }
  infoBox.saving = true
  try {
    await paperApi.update(paperId.value, {
      title: infoForm.title.trim(),
      description: infoForm.description.trim() || undefined,
      categoryId: infoForm.categoryId || undefined,
      passScore: infoForm.passScore,
      suggestMinutes: infoForm.suggestMinutes
    })
    ElMessage.success('试卷信息已保存')
    infoBox.open = false
    await loadPaper()
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    infoBox.saving = false
  }
}

const previewBox = reactive({ open: false, loading: false, title: '', rows: [] })

async function openPreview() {
  previewBox.open = true
  previewBox.loading = true
  try {
    const res = await paperApi.preview(paperId.value)
    previewBox.title = (res || {}).title || ''
    previewBox.rows = toRows(res && res.questions)
  } catch {
    previewBox.rows = []
  } finally {
    previewBox.loading = false
  }
}

async function publishPaper() {
  try {
    await ElMessageBox.confirm(
      `发布后整卷共 ${totalScore.value} 分、${questions.value.length} 道题将被锁定，不能再增删或改分值。`,
      '发布试卷',
      { confirmButtonText: '发布', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await paperApi.publish(paperId.value)
    ElMessage.success('试卷已发布，可以排进考试了')
    await loadPaper()
  } catch {
    /* 题量或总分不一致等原因由请求层提示 */
  }
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

function goBack() {
  router.push('/admin/papers')
}

onMounted(async () => {
  try {
    subjects.value = flatten(await categoryApi.tree())
  } catch {
    subjects.value = []
  }
  await loadPaper()
  if (paper.value.categoryId) bank.categoryId = paper.value.categoryId
  loadBank()
})
</script>

<template>
  <div class="page">
    <div v-if="!hasId" class="card">
      <div class="card-head">
        <div>
          <h1 class="page-title">缺少试卷</h1>
          <p class="page-sub">这条链接没有指向具体试卷，请从试卷列表重新打开。</p>
        </div>
        <el-button type="primary" @click="goBack">返回试卷列表</el-button>
      </div>
    </div>

    <template v-else>
      <div class="page-head">
        <div>
          <h1 class="page-title">{{ paper.title || '组卷' }}</h1>
          <p class="page-sub">
            {{ paper.description || '按题型把题目从左侧题库加入卷面，右侧可调分值与顺序。' }}
          </p>
        </div>
        <div class="page-actions">
          <el-button @click="goBack">返回列表</el-button>
          <el-button :disabled="published" @click="openInfo">保存信息</el-button>
          <el-button @click="openPreview">预览整卷</el-button>
          <el-button type="primary" :disabled="published || !questions.length" @click="publishPaper">发布</el-button>
        </div>
      </div>

      <div class="metric-bar" v-loading="loading">
        <div>
          <div class="metric-label">总分</div>
          <div class="metric-value sm num">{{ totalScore }}</div>
        </div>
        <div>
          <div class="metric-label">题数</div>
          <div class="metric-value sm num">{{ questions.length }}</div>
        </div>
        <div>
          <div class="metric-label">及格线</div>
          <div class="metric-value sm num">{{ paper.passScore ?? '—' }}</div>
        </div>
        <div>
          <div class="metric-label">建议时长</div>
          <div class="metric-value sm num">{{ paper.suggestMinutes ?? '—' }}<span class="metric-unit">分钟</span></div>
        </div>
        <div>
          <div class="metric-label">状态</div>
          <div class="metric-value sm"><span class="tag" :class="published ? 'tag-ok' : statusText === '已归档' ? 'tag-quiet' : 'tag-warn'">{{ statusText }}</span></div>
        </div>
      </div>

      <p v-if="published" class="lock-tip">该试卷已发布，题目与分值均已锁定，本页只供查看与预览。</p>
      <p v-else-if="paper.status === 2" class="lock-tip">该试卷已归档，题目结构不可再调整；需要改题请新建一张试卷。</p>

      <div class="grid grid-2 work">
        <section class="card">
          <div class="card-head">
            <div>
              <h2 class="card-title">题库选题</h2>
              <p class="card-hint">筛选题目后点「加入」即可进入右侧卷面</p>
            </div>
          </div>
          <div class="bank-filter">
            <el-select v-model="bank.categoryId" placeholder="全部科目" clearable filterable class="cell" @change="searchBank">
              <el-option v-for="item in subjects" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="bank.qType" placeholder="全部题型" clearable class="cell short" @change="searchBank">
              <el-option v-for="item in qTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-input
              v-model.trim="bank.keyword"
              placeholder="搜索题干"
              clearable
              class="cell grow"
              @keyup.enter="searchBank"
              @clear="searchBank"
            />
          </div>

          <el-table v-loading="bankLoading" :data="bankRows" row-key="id" class="bank-table">
            <el-table-column label="题干" min-width="220">
              <template #default="{ row }">
                <div class="clamp-2">{{ row.content }}</div>
                <div class="cell-meta">{{ row.categoryName }} · 默认 {{ row.score }} 分</div>
              </template>
            </el-table-column>
            <el-table-column label="题型" width="88">
              <template #default="{ row }"><span class="tag">{{ row.qTypeName }}</span></template>
            </el-table-column>
            <el-table-column label="操作" width="88" align="right">
              <template #default="{ row }">
                <span v-if="chosenIds.has(row.id)" class="tag tag-ok">已选</span>
                <el-button
                  v-else
                  text
                  :disabled="!draftOnly || adding === row.id"
                  :loading="adding === row.id"
                  @click="addQuestion(row)"
                >
                  加入
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty v-if="!bankLoading" description="换个条件再试试，或先到题库加题" :image-size="60" />
            </template>
          </el-table>

          <el-pagination
            v-model:current-page="bank.page"
            :page-size="bank.size"
            :total="bankTotal"
            layout="total, prev, pager, next"
            @current-change="loadBank"
          />
        </section>

        <section class="card">
          <div class="card-head">
            <div>
              <h2 class="card-title">试卷题目</h2>
              <p class="card-hint">调整分值后总分自动重算</p>
            </div>
          </div>
          <div v-loading="loading" class="q-list">
            <div v-for="(item, index) in questions" :key="item.questionId" class="list-row q-item">
              <span class="num q-index">{{ index + 1 }}</span>
              <div class="list-row-main">
                <div class="q-meta">
                  <span class="tag">{{ qTypeMap[item.qType] || '题目' }}</span>
                  <span class="cell-meta">{{ levelMap[item.difficulty] || '难度未标注' }}</span>
                </div>
                <div class="clamp-2">{{ item.content }}</div>
              </div>
              <div class="q-tail">
                <el-input-number
                  v-model="item.score"
                  :min="0.5"
                  :max="100"
                  :step="0.5"
                  :precision="1"
                  :disabled="!draftOnly"
                  @change="saveScore(item)"
                />
                <el-button text :disabled="!draftOnly" @click="dropQuestion(item)">移除</el-button>
              </div>
            </div>
            <el-empty v-if="!loading && !questions.length" description="卷面还是空的，从左边挑几道题" :image-size="60" />
          </div>
        </section>
      </div>
    </template>

    <el-dialog v-model="infoBox.open" title="试卷信息" width="520px">
      <el-form label-position="top">
        <el-form-item label="试卷名称" required>
          <el-input v-model.trim="infoForm.title" maxlength="60" />
        </el-form-item>
        <el-form-item label="所属科目">
          <el-select v-model="infoForm.categoryId" filterable clearable placeholder="可留空" class="full">
            <el-option v-for="item in subjects" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="建议时长（分钟）">
            <el-input-number v-model="infoForm.suggestMinutes" :min="10" :max="300" :step="10" class="full" />
          </el-form-item>
          <el-form-item label="及格分">
            <el-input-number v-model="infoForm.passScore" :min="1" :max="300" :step="5" class="full" />
          </el-form-item>
        </div>
        <el-form-item label="试卷说明">
          <el-input v-model="infoForm.description" type="textarea" :rows="3" placeholder="考试说明会一并展示给学生" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="infoBox.open = false">取消</el-button>
        <el-button type="primary" :loading="infoBox.saving" @click="submitInfo">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewBox.open" title="整卷预览" width="760px" top="6vh">
      <div v-loading="previewBox.loading" class="preview">
        <p v-if="previewBox.title" class="preview-title">{{ previewBox.title }}</p>
        <ol class="preview-list">
          <li v-for="(item, index) in previewBox.rows" :key="item.questionId" class="preview-item">
            <div class="preview-head">
              <span class="num preview-no">{{ index + 1 }}</span>
              <span class="tag">{{ qTypeMap[item.qType] || '题目' }}</span>
              <span class="num preview-score">{{ item.score }} 分</span>
            </div>
            <p class="preview-content">{{ item.content }}</p>
            <ul v-if="(item.options || []).length" class="preview-options">
              <li v-for="opt in item.options" :key="opt.key">{{ opt.key }}. {{ opt.text }}</li>
            </ul>
            <p class="preview-answer">答案：{{ answerText(item) || '—' }}</p>
            <p v-if="item.analysis" class="preview-analysis">解析：{{ item.analysis }}</p>
          </li>
        </ol>
        <el-empty v-if="!previewBox.loading && !previewBox.rows.length" description="这份试卷还没有题目" :image-size="60" />
      </div>
      <template #footer>
        <el-button @click="previewBox.open = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.metric-bar {
  position: sticky;
  top: 56px;
  z-index: 4;
  display: flex;
  align-items: flex-end;
  gap: 40px;
  flex-wrap: wrap;
  padding: 16px 24px;
  background: var(--paper);
  border: 1px solid rgba(0, 0, 0, .06);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.metric-value.sm {
  margin-top: 2px;
  font-size: 22px;
}

.lock-tip {
  margin: 12px 2px 0;
  font-size: 13px;
  color: var(--ink-3);
}

.work {
  margin-top: 20px;
  align-items: start;
}

.bank-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.cell {
  width: 158px;
}

.cell.short {
  width: 124px;
}

.cell.grow {
  flex: 1;
  min-width: 0;
}

.bank-table {
  min-height: 260px;
}

.clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cell-meta {
  font-size: 12px;
  color: var(--ink-4);
}

.el-table .el-button.is-text {
  padding-inline: 7px;
}

.q-list {
  min-height: 260px;
}

.q-item {
  align-items: flex-start;
  gap: 12px;
}

.q-index {
  flex-shrink: 0;
  width: 22px;
  font-size: 13px;
  color: var(--ink-4);
}

.q-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}

.q-tail {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.q-tail .el-input-number {
  width: 112px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.full {
  width: 100%;
}

.preview {
  max-height: 62vh;
  overflow-y: auto;
}

.preview-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
}

.preview-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.preview-item {
  padding: 14px 0;
  border-top: 1px solid var(--line-soft);
}

.preview-item:first-child {
  border-top: 0;
}

.preview-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.preview-no {
  font-size: 13px;
  color: var(--ink-4);
}

.preview-score {
  font-size: 12px;
  color: var(--ink-3);
  margin-left: auto;
}

.preview-content {
  margin: 6px 0 0;
  color: var(--ink);
}

.preview-options {
  margin: 8px 0 0;
  padding-left: 20px;
  color: var(--ink-2);
}

.preview-answer {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--ink-2);
}

.preview-analysis {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--ink-3);
}
</style>
