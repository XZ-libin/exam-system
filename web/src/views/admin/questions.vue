<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/request'
import { categoryApi, questionApi } from '../../api'
import { useAccountStore } from '../../stores/account'

const account = useAccountStore()
const canEdit = computed(() => account.isAdmin || account.isTeacher)

const qTypes = [
  { value: 1, label: '单选题' },
  { value: 2, label: '多选题' },
  { value: 3, label: '判断题' },
  { value: 4, label: '填空题' },
  { value: 5, label: '简答题' }
]
const levels = [
  { value: 1, label: '很易' },
  { value: 2, label: '较易' },
  { value: 3, label: '中等' },
  { value: 4, label: '较难' },
  { value: 5, label: '很难' }
]

function levelLabel(value) {
  return (levels.find((item) => item.value === value) || {}).label || '未设定'
}

function timeText(value) {
  if (!value) return '—'
  const text = String(value).replace('T', ' ')
  return text.length > 16 ? text.slice(0, 16) : text
}

/* ---------------------------------------------------------------- 分类树 */

const tree = ref([])
const flatCats = ref([])
const treeLoading = ref(false)
const treeKeyword = ref('')
const currentCat = ref(null)
const hoverCat = ref(null)
const treeRef = ref()

function flatten(nodes, prefix = []) {
  const out = []
  for (const node of nodes || []) {
    const path = [...prefix, node.name]
    out.push({ value: node.id, label: path.join(' › ') })
    out.push(...flatten(node.children, path))
  }
  return out
}

async function loadTree() {
  treeLoading.value = true
  try {
    tree.value = (await categoryApi.tree(treeKeyword.value || undefined)) || []
    flatCats.value = flatten(tree.value)
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    treeLoading.value = false
  }
}

function pickCategory(cat) {
  currentCat.value = { id: cat.id, name: cat.name }
  query.categoryId = cat.id
  query.page = 1
  load()
}

function clearCategory() {
  currentCat.value = null
  query.categoryId = null
  query.page = 1
  treeRef.value && treeRef.value.setCurrentKey(null)
  load()
}

const catDialog = reactive({ open: false, mode: 'child', id: null, parentId: null, title: '', saving: false })
const catForm = reactive({ name: '', sortNo: 0, remark: '' })

function openCatCreate(parent) {
  catDialog.mode = 'child'
  catDialog.parentId = parent ? parent.id : null
  catDialog.id = null
  catDialog.title = parent ? `在「${parent.name}」下新建子分类` : '新建分类'
  Object.assign(catForm, { name: '', sortNo: 0, remark: '' })
  catDialog.open = true
}

function openCatRename(cat) {
  catDialog.mode = 'rename'
  catDialog.id = cat.id
  catDialog.parentId = cat.parentId || null
  catDialog.title = `重命名「${cat.name}」`
  Object.assign(catForm, { name: cat.name, sortNo: cat.sortNo ?? 0, remark: cat.remark || '' })
  catDialog.open = true
}

async function submitCategory() {
  if (!catForm.name.trim()) {
    ElMessage.warning('请填写分类名称')
    return
  }
  catDialog.saving = true
  const body = {
    parentId: catDialog.parentId || 0,
    name: catForm.name.trim(),
    sortNo: catForm.sortNo,
    remark: catForm.remark || undefined
  }
  try {
    if (catDialog.mode === 'rename') {
      await categoryApi.update(catDialog.id, body)
      ElMessage.success('分类已更新')
    } else {
      await categoryApi.create(body)
      ElMessage.success('分类已创建')
    }
    catDialog.open = false
    await loadTree()
    if (currentCat.value) {
      const still = flatCats.value.find((item) => item.value === currentCat.value.id)
      if (still) currentCat.value = { id: still.value, name: still.label.split(' › ').pop() }
    }
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    catDialog.saving = false
  }
}

async function removeCategory(cat) {
  try {
    await ElMessageBox.confirm(
      `删除「${cat.name}」后无法恢复，分类下有题目或子分类时需要先清空。`,
      '删除分类',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await categoryApi.remove(cat.id)
    ElMessage.success('分类已删除')
    if (currentCat.value && currentCat.value.id === cat.id) clearCategory()
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    await loadTree()
  }
}

/* ---------------------------------------------------------------- 题目列表 */

const query = reactive({ page: 1, size: 10, keyword: '', qType: '', difficulty: '', categoryId: null })
const rows = ref([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await questionApi.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      qType: query.qType || undefined,
      difficulty: query.difficulty || undefined,
      categoryId: query.categoryId || undefined
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
  query.qType = ''
  query.difficulty = ''
  search()
}

/* ---------------------------------------------------------------- 题目编辑 */

const drawer = reactive({ open: false, mode: 'create', saving: false })
const draft = reactive({
  id: null,
  categoryId: null,
  qType: 1,
  content: '',
  analysis: '',
  difficulty: 3,
  score: 2,
  status: 1,
  options: [{ key: 'A', text: '' }, { key: 'B', text: '' }],
  answer: [],
  judge: 'T',
  blanks: [''],
  reference: ''
})

const isSingle = computed(() => draft.qType === 1)
const isMultiple = computed(() => draft.qType === 2)
const isChoice = computed(() => isSingle.value || isMultiple.value)
const isJudge = computed(() => draft.qType === 3)
const isBlank = computed(() => draft.qType === 4)
const isEssay = computed(() => draft.qType === 5)
const blankCount = computed(() => (String(draft.content || '').match(/_{2,}/g) || []).length)

function resetDraft(categoryId) {
  Object.assign(draft, {
    id: null,
    categoryId,
    qType: 1,
    content: '',
    analysis: '',
    difficulty: 3,
    score: 2,
    status: 1,
    options: [{ key: 'A', text: '' }, { key: 'B', text: '' }],
    answer: [],
    judge: 'T',
    blanks: [''],
    reference: ''
  })
}

function openCreate() {
  const target = currentCat.value ? currentCat.value.id : flatCats.value[0] && flatCats.value[0].value
  if (!target) {
    ElMessage.warning('请先在左侧建立一个分类，题目需要归类存放')
    return
  }
  resetDraft(target)
  drawer.mode = 'create'
  drawer.open = true
}

function openEdit(row) {
  Object.assign(draft, {
    id: row.id,
    categoryId: row.categoryId,
    qType: row.qType,
    content: row.content || '',
    analysis: row.analysis || '',
    difficulty: row.difficulty || 3,
    score: Number(row.score) || 2,
    status: row.status ?? 1,
    options: (row.options || []).map((item, index) => ({ key: item.key || String.fromCharCode(65 + index), text: item.text || '' })),
    answer: [...(row.answer || [])],
    judge: (row.answer || [])[0] || 'T',
    blanks: row.qType === 4 && (row.answer || []).length ? [...row.answer] : [''],
    reference: (row.answer || [])[0] || ''
  })
  if (isChoice.value && !draft.options.length) {
    draft.options = [{ key: 'A', text: '' }, { key: 'B', text: '' }]
  }
  syncBlanks()
  drawer.mode = 'edit'
  drawer.open = true
}

function changeType(type) {
  draft.qType = type
  draft.answer = []
  draft.judge = 'T'
  draft.blanks = ['']
  draft.reference = ''
  if (isChoice.value && draft.options.length < 2) {
    draft.options = [{ key: 'A', text: '' }, { key: 'B', text: '' }]
  }
  syncBlanks()
}

function renumberOptions() {
  draft.options.forEach((item, index) => {
    item.key = String.fromCharCode(65 + index)
  })
  const keys = draft.options.map((item) => item.key)
  draft.answer = draft.answer.filter((key) => keys.includes(key))
}

function addOption() {
  if (draft.options.length >= 6) return
  draft.options.push({ key: String.fromCharCode(65 + draft.options.length), text: '' })
}

function removeOption(index) {
  draft.options.splice(index, 1)
  renumberOptions()
}

function toggleKey(key) {
  const at = draft.answer.indexOf(key)
  if (at >= 0) {
    draft.answer.splice(at, 1)
  } else {
    draft.answer = isSingle.value ? [key] : [key, ...draft.answer].sort()
  }
}

function syncBlanks() {
  if (!isBlank.value) return
  const count = blankCount.value
  const list = draft.blanks.slice(0, count)
  while (list.length < count) list.push('')
  draft.blanks = list
}

watch(blankCount, syncBlanks)

const composedAnswer = computed(() => {
  if (isChoice.value) return draft.answer.slice()
  if (isJudge.value) return [draft.judge]
  if (isBlank.value) return draft.blanks.map((item) => (item || '').trim()).filter(Boolean)
  return draft.reference.trim() ? [draft.reference.trim()] : []
})

const canSave = computed(() => {
  if (!draft.categoryId) return false
  if (!draft.content.trim()) return false
  if (isChoice.value) {
    const filled = draft.options.filter((item) => item.text.trim())
    if (filled.length < 2) return false
    if (isSingle.value) return draft.answer.length === 1
    return draft.answer.length >= 2
  }
  if (isBlank.value) return blankCount.value > 0 && composedAnswer.value.length === blankCount.value
  return composedAnswer.value.length > 0
})

async function save() {
  if (!canSave.value) {
    ElMessage.warning('请把题干与标准答案补充完整')
    return
  }
  drawer.saving = true
  const body = {
    categoryId: draft.categoryId,
    qType: draft.qType,
    content: draft.content.trim(),
    answer: composedAnswer.value,
    analysis: draft.analysis.trim() || undefined,
    difficulty: draft.difficulty,
    score: draft.score,
    status: draft.status,
    options: isChoice.value ? draft.options.filter((item) => item.text.trim()).map((item) => ({ key: item.key, text: item.text.trim() })) : null
  }
  try {
    if (drawer.mode === 'edit') {
      await questionApi.update(draft.id, body)
      ElMessage.success('题目已更新')
    } else {
      await questionApi.create(body)
      ElMessage.success('题目已加入题库')
    }
    drawer.open = false
    await Promise.all([load(), loadTree()])
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    drawer.saving = false
  }
}

async function remove(row) {
  const used = Number(row.useCount) || 0
  try {
    await ElMessageBox.confirm(
      used > 0
        ? `这道题已被 ${used} 份试卷引用，需要先在这些试卷中移除后才能删除。`
        : '删除后这道题不再出现在选题列表中，确定继续吗？',
      '删除题目',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await questionApi.remove(row.id)
    ElMessage.success('题目已删除')
  } catch {
    /* 引用冲突等原因由请求层提示，这里只同步列表与计数 */
  } finally {
    await Promise.all([load(), loadTree()])
  }
}

/* ---------------------------------------------------------------- 批量导入 */

const importer = reactive({ open: false, categoryId: null, file: null, running: false, result: null })

function openImporter() {
  importer.categoryId = currentCat.value ? currentCat.value.id : flatCats.value[0] && flatCats.value[0].value
  importer.file = null
  importer.result = null
  importer.open = true
}

function onFileChange(file) {
  importer.file = file.raw || file
  importer.result = null
}

async function runImport() {
  if (!importer.file) {
    ElMessage.warning('请先选择整理好的文本文件')
    return
  }
  if (!importer.categoryId) {
    ElMessage.warning('请选择题目归入的分类')
    return
  }
  importer.running = true
  try {
    importer.result = await questionApi.importText(importer.categoryId, importer.file)
    ElMessage.success(`已录入 ${(importer.result || {}).saved || 0} 道题目`)
    await Promise.all([load(), loadTree()])
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    importer.running = false
  }
}

async function downloadTemplate() {
  try {
    const text = await http.get('/questions/template')
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = '题目录入模板.txt'
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    /* 提示已由请求层给出 */
  }
}

/** 左树占掉 268px 后表格只剩 780px 左右，动作收进下拉才不会把「操作」列推出卡片 */
function runAction(cmd, row) {
  if (cmd === 'edit') {
    openEdit(row)
  } else if (cmd === 'remove') {
    remove(row)
  }
}

onMounted(() => {
  loadTree()
  load()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">题库管理</h1>
        <p class="page-sub">按科目分类维护题目，新增、修改与批量录入都在这里完成。</p>
      </div>
      <div class="page-actions">
        <el-button @click="openImporter">批量录入</el-button>
        <el-button type="primary" :disabled="!canEdit" @click="openCreate">新增题目</el-button>
      </div>
    </div>

    <div class="split">
      <section class="card" v-loading="treeLoading">
        <div class="card-head">
          <div>
            <h2 class="card-title">分类</h2>
            <p class="card-hint">悬停分类名可新建子分类或重命名</p>
          </div>
          <el-button text :disabled="!canEdit" @click="openCatCreate(null)">新建</el-button>
        </div>
        <el-input v-model.trim="treeKeyword" placeholder="搜索分类" clearable @keyup.enter="loadTree" @clear="loadTree">
          <template #suffix><span class="mini-action" @click="loadTree">查找</span></template>
        </el-input>
        <div class="tree-toolbar">
          <span v-if="currentCat" class="tag tag-quiet">当前：{{ currentCat.name }}</span>
          <el-button v-if="currentCat" text @click="clearCategory">看全部</el-button>
        </div>
        <el-tree
          ref="treeRef"
          class="cat-tree"
          :data="tree"
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          :expand-on-click-node="false"
          default-expand-all
          highlight-current
          @node-click="pickCategory"
        >
          <template #default="{ data: cat }">
            <div class="tree-node" @mouseenter="hoverCat = cat.id" @mouseleave="hoverCat = null">
              <span class="tree-name">{{ cat.name }}</span>
              <span v-show="hoverCat !== cat.id" class="num tree-count">{{ cat.questionCount || 0 }}</span>
              <span v-show="hoverCat === cat.id" class="tree-ops">
                <el-button text size="small" :disabled="!canEdit" @click.stop="openCatCreate(cat)">子分类</el-button>
                <el-button text size="small" :disabled="!canEdit" @click.stop="openCatRename(cat)">改名</el-button>
                <el-button text size="small" :disabled="!canEdit" @click.stop="removeCategory(cat)">删除</el-button>
              </span>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="!treeLoading && !tree.length" description="还没有分类" :image-size="60" />
      </section>

      <section class="card">
        <div class="filter-bar">
          <el-input
            v-model.trim="query.keyword"
            class="filter-keyword"
            placeholder="按题干或解析搜索"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="query.qType" class="filter-item" placeholder="全部题型" clearable @change="search">
            <el-option v-for="item in qTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.difficulty" class="filter-item" placeholder="全部难度" clearable @change="search">
            <el-option v-for="item in levels" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button @click="search">查询</el-button>
          <el-button text @click="resetFilters">重置条件</el-button>
        </div>

        <el-table v-loading="loading" :data="rows" row-key="id">
          <el-table-column label="题干" min-width="180">
            <template #default="{ row }">
              <div class="clamp-2">{{ row.content }}</div>
              <div v-if="row.categoryName" class="cell-meta">{{ row.categoryName }}</div>
            </template>
          </el-table-column>
          <el-table-column label="题型" width="84">
            <template #default="{ row }"><span class="tag">{{ row.qTypeName }}</span></template>
          </el-table-column>
          <el-table-column label="难度" width="96">
            <template #default="{ row }">
              <div class="level-cell">
                <span class="dots">
                  <i v-for="n in 5" :key="n" :class="{ on: n <= (row.difficulty || 0) }" />
                </span>
                <span class="cell-meta">{{ levelLabel(row.difficulty) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="分值" width="64">
            <template #default="{ row }"><span class="num">{{ row.score }}</span></template>
          </el-table-column>
          <el-table-column label="引用" width="60">
            <template #default="{ row }"><span class="num">{{ row.useCount || 0 }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <span class="tag" :class="row.status === 1 ? 'tag-ok' : 'tag-quiet'">
                {{ row.status === 1 ? '可用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="124">
            <template #default="{ row }"><span class="num">{{ timeText(row.updateTime || row.createTime) }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="92">
            <template #default="{ row }">
              <el-dropdown trigger="click" @command="(cmd) => runAction(cmd, row)">
                <el-button text size="small">操作</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit" :disabled="!canEdit">编辑</el-dropdown-item>
                    <el-dropdown-item command="remove" :disabled="!canEdit">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty v-if="!loading" description="这个分类下还没有题目，点右上角新增或批量录入" :image-size="72" />
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
    </div>

    <el-dialog v-model="catDialog.open" :title="catDialog.title" width="420px">
      <el-form label-position="top">
        <el-form-item label="分类名称" required>
          <el-input v-model.trim="catForm.name" maxlength="50" placeholder="例如 Java 基础" />
        </el-form-item>
        <el-form-item label="同级排序">
          <el-input-number v-model="catForm.sortNo" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model.trim="catForm.remark" type="textarea" :rows="2" placeholder="选填，给协作者看的用途说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catDialog.open = false">取消</el-button>
        <el-button type="primary" :loading="catDialog.saving" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawer.open" :title="drawer.mode === 'create' ? '新增题目' : '编辑题目'" size="620px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="所属分类">
            <el-select v-model="draft.categoryId" filterable placeholder="选择题目的归属分类" class="full">
              <el-option v-for="item in flatCats" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="题型">
            <el-select :model-value="draft.qType" class="full" @update:model-value="changeType">
              <el-option v-for="item in qTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="题干">
          <el-input
            v-model="draft.content"
            type="textarea"
            :rows="3"
            :placeholder="isBlank ? '用连续下划线表示空，例如：Java 的 ____ 关键字用于继承。' : '请输入题干'"
          />
          <p v-if="isBlank" class="field-hint">
            {{ blankCount ? `已识别 ${blankCount} 个空，需为每个空各给一条答案` : '题干中至少要有 1 处 ____' }}
          </p>
        </el-form-item>

        <el-form-item v-if="isChoice" label="选项与正确答案">
          <div class="opt-list">
            <div v-for="(opt, index) in draft.options" :key="index" class="opt-row">
              <span class="opt-key num">{{ opt.key }}</span>
              <el-input v-model="opt.text" placeholder="选项内容" />
              <el-checkbox
                :model-value="draft.answer.includes(opt.key)"
                @change="toggleKey(opt.key)"
              >
                正确
              </el-checkbox>
              <el-button text :disabled="draft.options.length <= 2" @click="removeOption(index)">移除</el-button>
            </div>
          </div>
          <el-button text :disabled="draft.options.length >= 6" @click="addOption">再加一个选项</el-button>
          <p class="field-hint">{{ isSingle ? '单选只标一个正确答案' : '多选至少标两个正确答案' }}</p>
        </el-form-item>

        <el-form-item v-if="isJudge" label="正确答案">
          <el-radio-group v-model="draft.judge">
            <el-radio value="T">正确</el-radio>
            <el-radio value="F">错误</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="isBlank" label="每空的标准答案">
          <div class="blank-list">
            <el-input
              v-for="(item, index) in draft.blanks"
              :key="index"
              v-model="draft.blanks[index]"
              :placeholder="`第 ${index + 1} 空的答案`"
            />
          </div>
          <p v-if="!blankCount" class="field-hint">先在题干里写下 ____，这里会自动出现对应的输入框。</p>
        </el-form-item>

        <el-form-item v-if="isEssay" label="参考答案">
          <el-input v-model="draft.reference" type="textarea" :rows="4" placeholder="写出要点，阅卷时作为对照" />
        </el-form-item>

        <el-form-item label="解析">
          <el-input v-model="draft.analysis" type="textarea" :rows="2" placeholder="选填，说明解题思路" />
        </el-form-item>

        <div class="form-grid grid-3">
          <el-form-item label="分值">
            <el-input-number v-model="draft.score" :min="0.5" :max="100" :step="0.5" :precision="1" class="full" />
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="draft.difficulty" class="full">
              <el-option v-for="item in levels" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-switch
              v-model="draft.status"
              :active-value="1"
              :inactive-value="0"
              active-text="可组卷"
              inactive-text="暂不用"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="drawer.open = false">取消</el-button>
        <el-button type="primary" :loading="drawer.saving" :disabled="!canSave" @click="save">
          {{ drawer.mode === 'create' ? '加入题库' : '保存修改' }}
        </el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="importer.open" title="批量录入题目" width="520px">
      <p class="import-tip">按模板整理好文本，一次录入同一分类下的多道题。</p>
      <el-form label-position="top">
        <el-form-item label="归入分类">
          <el-select v-model="importer.categoryId" filterable placeholder="选择题目分类" class="full">
            <el-option v-for="item in flatCats" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="文本文件">
          <el-upload
            action="#"
            accept=".txt"
            :auto-upload="false"
            :show-file-list="false"
            :on-change="onFileChange"
          >
            <el-button>选择文件</el-button>
            <template #tip><span class="field-hint">仅支持 UTF-8 编码的 txt</span></template>
          </el-upload>
          <div v-if="importer.file" class="file-line">
            <span>{{ importer.file.name }}</span>
            <el-button text @click="importer.file = null">移除</el-button>
          </div>
        </el-form-item>
      </el-form>
      <div v-if="importer.result" class="import-result">
        <p class="import-summary num">
          成功录入 {{ importer.result.saved || 0 }} 道
          <span v-if="importer.result.failed">，{{ importer.result.failed }} 道未通过</span>
        </p>
        <ul v-if="(importer.result.messages || []).length" class="import-messages">
          <li v-for="(message, index) in importer.result.messages" :key="index">{{ message }}</li>
        </ul>
      </div>
      <template #footer>
        <el-button @click="downloadTemplate">下载录入模板</el-button>
        <el-button @click="importer.open = false">关闭</el-button>
        <el-button type="primary" :loading="importer.running" @click="runImport">开始录入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.split {
  display: grid;
  grid-template-columns: 268px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

@media (max-width: 960px) {
  .split {
    grid-template-columns: minmax(0, 1fr);
  }
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.filter-keyword {
  flex: 1 1 220px;
  max-width: 320px;
}

.filter-item {
  width: 132px;
}

.tree-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0 4px;
}

.cat-tree {
  margin-top: 4px;
  background: transparent;
}

.cat-tree :deep(.el-tree-node__content) {
  height: 30px;
}

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex: 1 1 auto;
  min-width: 0;
  padding-right: 4px;
}

.tree-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-count {
  font-size: 12px;
  color: var(--ink-4);
}

.tree-ops {
  display: inline-flex;
  align-items: center;
  gap: 0;
  flex-shrink: 0;
}

.tree-ops .el-button.is-text {
  padding-inline: 5px;
  font-size: 12px;
}

.el-table .el-button.is-text {
  padding-inline: 7px;
}

.mini-action {
  font-size: 12px;
  color: var(--ink-3);
  cursor: pointer;
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

.level-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dots {
  display: inline-flex;
  gap: 3px;
}

.dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--line);
}

.dots i.on {
  background: var(--ink-2);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.grid-3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: center;
}

.full {
  width: 100%;
}

.field-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--ink-4);
}

.opt-list {
  width: 100%;
  display: grid;
  gap: 8px;
}

.opt-row {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 10px;
}

.opt-key {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-3);
}

.blank-list {
  width: 100%;
  display: grid;
  gap: 8px;
}

.import-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--ink-3);
}

.file-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 13px;
  color: var(--ink-2);
}

.import-result {
  margin-top: 4px;
  padding: 12px 14px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-sm);
  background: var(--paper-sunk);
}

.import-summary {
  margin: 0;
  font-size: 13px;
  color: var(--ink-2);
}

.import-messages {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--ink-3);
}
</style>
