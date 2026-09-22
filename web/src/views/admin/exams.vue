<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { examApi, paperApi, userApi } from '../../api'

const route = useRoute()

const loading = ref(false)
const exams = ref([])
const total = ref(0)
const filter = reactive({ page: 1, size: 10, keyword: '', status: null })

const drawer = ref(false)
const drawerTitle = ref('新建考试')
const saving = ref(false)
const formRef = ref()
const papers = ref([])
const paperLoading = ref(false)
const classes = ref([])

const form = reactive({
  id: null,
  paperId: null,
  title: '',
  description: '',
  window: [],
  durationMinutes: 60,
  lateMinutes: 15,
  maxAttempts: 1,
  audienceType: 1,
  classNames: [],
  switchLimit: 0,
  shuffleQuestion: 0,
  shuffleOption: 0,
  status: 0
})

const rules = {
  paperId: [{ required: true, message: '请选择试卷', trigger: 'change' }],
  title: [{ required: true, message: '请输入考试名称', trigger: 'blur' }],
  window: [{ required: true, message: '请选择考试的起止时间', trigger: 'change' }]
}

const STATE_TEXT = { 1: '未开始', 2: '进行中', 3: '已结束' }
const STATE_TAG = { 1: 'tag-quiet', 2: 'tag-ok', 3: 'tag-quiet' }
const STATUS_TEXT = { 0: '草稿', 1: '已发布', 3: '已结束' }

function dateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

function toPicker(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : ''
}

function toBackend(value) {
  return value ? String(value).replace(' ', 'T') : null
}

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

function picked(value) {
  return value === '' || value === null || value === undefined ? undefined : value
}

async function load() {
  loading.value = true
  try {
    const data = await examApi.page({
      page: filter.page,
      size: filter.size,
      keyword: filter.keyword || undefined,
      status: picked(filter.status)
    })
    exams.value = data.records || []
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
  filter.keyword = ''
  filter.status = null
  load()
}

async function loadPapers() {
  if (papers.value.length) {
    return
  }
  paperLoading.value = true
  try {
    const data = await paperApi.page({ page: 1, size: 100, status: 1 })
    papers.value = data.records || []
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    paperLoading.value = false
  }
}

async function loadClasses() {
  if (classes.value.length) {
    return
  }
  try {
    const data = await userApi.options()
    classes.value = data.classNames || []
  } catch {
    /* 提示已由请求层给出 */
  }
}

function openCreate() {
  drawerTitle.value = '新建考试'
  Object.assign(form, {
    id: null,
    paperId: route.query.paperId ? Number(route.query.paperId) : null,
    title: '',
    description: '',
    window: [],
    durationMinutes: 60,
    lateMinutes: 15,
    maxAttempts: 1,
    audienceType: 1,
    classNames: [],
    switchLimit: 0,
    shuffleQuestion: 0,
    shuffleOption: 0,
    status: 0
  })
  drawer.value = true
  loadPapers()
  loadClasses()
  nextTick(() => formRef.value && formRef.value.clearValidate())
}

function openEdit(row) {
  drawerTitle.value = '编辑考试'
  Object.assign(form, {
    id: row.id,
    paperId: row.paperId,
    title: row.title || '',
    description: row.description || '',
    window: row.startTime && row.endTime ? [toPicker(row.startTime), toPicker(row.endTime)] : [],
    durationMinutes: row.durationMinutes ?? 60,
    lateMinutes: row.lateMinutes ?? 15,
    maxAttempts: row.maxAttempts ?? 1,
    audienceType: row.audienceType ?? 1,
    classNames: [...(row.classNames || [])],
    switchLimit: row.switchLimit ?? 0,
    shuffleQuestion: row.shuffleQuestion ?? 0,
    shuffleOption: row.shuffleOption ?? 0,
    status: row.status ?? 0
  })
  drawer.value = true
  loadPapers()
  loadClasses()
  nextTick(() => formRef.value && formRef.value.clearValidate())
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (form.audienceType === 2 && !form.classNames.length) {
    ElMessage.warning('指定班级参考时，请至少选择一个班级')
    return
  }
  const body = {
    paperId: form.paperId,
    title: form.title.trim(),
    description: form.description.trim() || null,
    startTime: toBackend(form.window && form.window[0]),
    endTime: toBackend(form.window && form.window[1]),
    durationMinutes: form.durationMinutes,
    lateMinutes: form.lateMinutes,
    maxAttempts: form.maxAttempts,
    audienceType: form.audienceType,
    classNames: form.audienceType === 2 ? form.classNames : [],
    switchLimit: form.switchLimit,
    shuffleQuestion: form.shuffleQuestion,
    shuffleOption: form.shuffleOption,
    status: form.status
  }
  saving.value = true
  try {
    if (form.id) {
      await examApi.update(form.id, body)
      ElMessage.success('考试信息已更新')
    } else {
      await examApi.create(body)
      ElMessage.success(form.status === 1 ? '考试已发布' : '已保存为草稿')
    }
    drawer.value = false
    load()
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    saving.value = false
  }
}

async function confirmBox(message, title) {
  try {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    return true
  } catch {
    return false
  }
}

async function changeStatus(row) {
  const publishing = row.status !== 1
  const message = publishing
    ? `发布后「${row.title}」会出现在学生的考试列表里，到点即可进入答题。确定发布吗？`
    : `结束后学生不能再进入「${row.title}」答题，已经开始的答卷不受影响。确定结束本场考试吗？`
  if (!(await confirmBox(message, publishing ? '发布考试' : '结束考试'))) {
    return
  }
  try {
    await examApi.changeStatus(row.id, publishing ? 1 : 3)
    ElMessage.success(publishing ? '考试已发布' : '考试已结束')
    load()
  } catch {
    /* 提示已由请求层给出 */
  }
}

async function publishScore(row) {
  if (!(await confirmBox(`成绩发布后，学生可以查看本场考试的得分、逐题答案与解析。确定发布「${row.title}」的成绩吗？`, '发布成绩'))) {
    return
  }
  try {
    await examApi.publishScore(row.id)
    ElMessage.success('成绩已发布')
    load()
  } catch {
    /* 提示已由请求层给出 */
  }
}

async function forceSubmit(row) {
  if (!(await confirmBox(`所有正在作答的答卷会被立即收卷并判分，学生无法继续答题。确定对「${row.title}」强制收卷吗？`, '强制收卷'))) {
    return
  }
  try {
    const count = await examApi.forceSubmit(row.id)
    ElMessage.success(typeof count === 'number' ? `已强制收交 ${count} 份答卷` : '强制收卷已完成')
    load()
  } catch {
    /* 提示已由请求层给出 */
  }
}

async function remove(row) {
  if (!(await confirmBox(`删除后「${row.title}」不再显示，已有作答记录的考试需要先结束。确定删除吗？`, '删除考试'))) {
    return
  }
  try {
    await examApi.remove(row.id)
    ElMessage.success('考试已删除')
    if (exams.value.length === 1 && filter.page > 1) {
      filter.page -= 1
    }
    load()
  } catch {
    /* 提示已由请求层给出 */
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">考试管理</h1>
        <p class="page-sub">安排考试场次、发布与收卷；成绩发布后学生端才能看到分数与解析。</p>
      </div>
      <div class="page-actions">
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate">新建考试</el-button>
      </div>
    </div>

    <section class="card">
      <div class="filters">
        <el-input v-model.trim="filter.keyword" class="kw" clearable placeholder="按考试名称搜索"
                  @keyup.enter="search" @clear="search" />
        <el-select v-model="filter.status" class="st" clearable placeholder="全部状态" @change="search">
          <el-option label="草稿" :value="0" />
          <el-option label="已发布" :value="1" />
          <el-option label="已结束" :value="3" />
        </el-select>
        <el-button @click="search">查询</el-button>
        <el-button text @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="exams" row-key="id">
        <template #empty>
          <el-empty description="还没有考试场次，点右上角新建考试" :image-size="60" />
        </template>
        <el-table-column label="考试 / 试卷" min-width="210">
          <template #default="{ row }">
            <div class="cell-title">{{ row.title }}</div>
            <div class="cell-sub">{{ row.paperTitle || '试卷已删除' }} · 满分 {{ num(row.paperTotalScore, 0) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="时间窗" min-width="170">
          <template #default="{ row }">
            <div class="cell-sub lines">
              <div>{{ dateTime(row.startTime) }}</div>
              <div>{{ dateTime(row.endTime) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时长" width="106">
          <template #default="{ row }">
            <span class="num">{{ num(row.durationMinutes, 0) }}</span> 分钟
            <div class="cell-sub">迟到 {{ num(row.lateMinutes, 0) }} 分可入场</div>
          </template>
        </el-table-column>
        <el-table-column label="次数" width="72">
          <template #default="{ row }">
            <span class="num">{{ num(row.maxAttempts, 0) }}</span> 次
          </template>
        </el-table-column>
        <el-table-column label="范围" min-width="150">
          <template #default="{ row }">
            <span v-if="row.audienceType === 1" class="tag">全部学生</span>
            <span v-else-if="row.audienceType === 3" class="tag tag-quiet">指定名单</span>
            <div v-else class="class-tags">
              <span v-for="name in row.classNames || []" :key="name" class="tag">{{ name }}</span>
              <span v-if="!(row.classNames || []).length" class="cell-sub">未配置班级</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="128">
          <template #default="{ row }">
            <span class="tag" :class="STATE_TAG[row.state] || 'tag-quiet'">{{ STATE_TEXT[row.state] || '—' }}</span>
            <div class="cell-sub">
              {{ STATUS_TEXT[row.status] || '—' }}
              <span v-if="row.scorePublished === 1"> · 成绩已发布</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="应交 · 实交" width="106">
          <template #default="{ row }">
            <span class="num">{{ num(row.expectedCount, 0) }} · {{ num(row.submittedCount, 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="平均分" width="88">
          <template #default="{ row }">
            <span class="num">{{ num(row.avgScore) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="248">
          <template #default="{ row }">
            <el-button text size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text size="small" @click="changeStatus(row)">{{ row.status === 1 ? '结束' : '发布' }}</el-button>
            <el-button v-if="row.status !== 0 && row.scorePublished !== 1" text size="small" @click="publishScore(row)">
              成绩发布
            </el-button>
            <el-button v-if="row.state === 2" text size="small" @click="forceSubmit(row)">强制收卷</el-button>
            <el-button text size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="filter.page" v-model:page-size="filter.size" background
                     :total="total" :page-sizes="[10, 20, 50]"
                     layout="total, sizes, prev, pager, next, jumper"
                     @current-change="load" @size-change="load" />
    </section>

    <el-drawer v-model="drawer" :title="drawerTitle" size="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="试卷" prop="paperId">
          <el-select v-model="form.paperId" :loading="paperLoading" filterable placeholder="只可选题库中已发布的试卷">
            <el-option v-for="p in papers" :key="p.id" :label="`${p.title}（满分 ${num(p.totalScore, 0)} 分）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试名称" prop="title">
          <el-input v-model.trim="form.title" maxlength="60" show-word-limit placeholder="例如：数据结构期末考试" />
        </el-form-item>
        <el-form-item label="考试说明">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" show-word-limit
                    placeholder="考前提示，学生进入考试前可见" />
        </el-form-item>
        <el-form-item label="考试时间窗" prop="window">
          <el-date-picker v-model="form.window" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
                          start-placeholder="开始时间" end-placeholder="结束时间" range-separator="至" />
        </el-form-item>
        <div class="grid grid-2">
          <el-form-item label="答题时长（分钟）">
            <el-input-number v-model="form.durationMinutes" :min="1" :max="600" :step="5" controls-position="right" />
          </el-form-item>
          <el-form-item label="开考后允许迟到（分钟，0 = 不允许迟到入场）">
            <el-input-number v-model="form.lateMinutes" :min="0" :max="240" :step="5" controls-position="right" />
          </el-form-item>
          <el-form-item label="允许作答次数">
            <el-input-number v-model="form.maxAttempts" :min="1" :max="5" controls-position="right" />
          </el-form-item>
          <el-form-item label="切屏次数上限">
            <el-input-number v-model="form.switchLimit" :min="0" :max="50" controls-position="right" />
          </el-form-item>
        </div>
        <p class="form-hint">切屏上限填 0 表示不做限制；填 3 表示学生第三次切走屏幕时自动交卷。</p>

        <el-form-item label="参考范围">
          <el-radio-group v-model="form.audienceType">
            <el-radio :value="1">全部学生</el-radio>
            <el-radio :value="2">指定班级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.audienceType === 2" label="班级">
          <el-select v-model="form.classNames" multiple filterable allow-create default-first-option
                     placeholder="选择班级，也可直接输入新班级名后回车">
            <el-option v-for="c in classes" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>

        <div class="shuffle">
          <div class="shuffle-row">
            <el-switch v-model="form.shuffleQuestion" :active-value="1" :inactive-value="0" />
            <div>
              <div class="shuffle-title">题目乱序</div>
              <div class="cell-sub">每位学生的题号顺序不同，降低横向抄袭风险。</div>
            </div>
          </div>
          <div class="shuffle-row">
            <el-switch v-model="form.shuffleOption" :active-value="1" :inactive-value="0" />
            <div>
              <div class="shuffle-title">选项乱序</div>
              <div class="cell-sub">选择题选项打乱，「选 A」这类口头答案不再通用。</div>
            </div>
          </div>
        </div>

        <el-form-item label="保存方式">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">存为草稿</el-radio>
            <el-radio :value="1">保存并发布</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="page-actions">
          <el-button @click="drawer = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
.filters .kw { width: 240px; }
.filters .st { width: 140px; }

.cell-title { font-weight: 500; color: var(--ink); }
.cell-sub { font-size: 12px; color: var(--ink-3); }
.lines div + div { margin-top: 2px; }
.class-tags { display: flex; gap: 6px; flex-wrap: wrap; }

.grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}
.form-hint {
  margin: -6px 0 18px;
  font-size: 12px;
  color: var(--ink-3);
}
.shuffle { display: grid; gap: 14px; margin-bottom: 22px; }
.shuffle-row { display: flex; gap: 12px; align-items: flex-start; }
.shuffle-title { font-size: 14px; font-weight: 500; color: var(--ink); }
</style>
