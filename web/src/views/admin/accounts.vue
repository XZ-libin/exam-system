<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '../../api'
import { useAccountStore } from '../../stores/account'

const account = useAccountStore()
const canManage = computed(() => account.isAdmin)
const lockReason = '账号资料由管理员统一维护，教师账号如需变更请联系管理员'

const query = reactive({ page: 1, size: 10, keyword: '', roleCode: '', status: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const choices = reactive({ roles: [], classNames: [] })

async function load() {
  loading.value = true
  try {
    const res = await userApi.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      roleCode: query.roleCode || undefined,
      status: typeof query.status === 'number' ? query.status : undefined
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

function reset() {
  query.keyword = ''
  query.roleCode = ''
  query.status = ''
  search()
}

function loginTime(value) {
  if (!value) return '从未登录'
  const text = String(value).replace('T', ' ')
  return text.length > 16 ? text.slice(0, 16) : text
}

/* ------------------------------------------------------------------ 表单 */

const dialog = reactive({ open: false, mode: 'create', id: null, saving: false })
const formRef = ref()
const form = reactive({
  username: '',
  realName: '',
  phone: '',
  email: '',
  className: '',
  password: '',
  status: 1,
  roles: ['STUDENT']
})

const rules = {
  username: [{ required: true, message: '请填写登录账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  roles: [{ required: true, type: 'array', min: 1, message: '请至少选择一个角色', trigger: 'change' }]
}

function openCreate() {
  Object.assign(form, {
    username: '',
    realName: '',
    phone: '',
    email: '',
    className: '',
    password: '',
    status: 1,
    roles: ['STUDENT']
  })
  dialog.mode = 'create'
  dialog.id = null
  dialog.open = true
}

function openEdit(row) {
  Object.assign(form, {
    username: row.username,
    realName: row.realName || '',
    phone: row.phone || '',
    email: row.email || '',
    className: row.className || '',
    password: '',
    status: row.status ?? 1,
    roles: [...(row.roles || [])]
  })
  dialog.mode = 'edit'
  dialog.id = row.id
  dialog.open = true
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  dialog.saving = true
  try {
    const body = {
      realName: form.realName,
      phone: form.phone || undefined,
      email: form.email || undefined,
      className: form.className || undefined,
      status: form.status,
      roles: form.roles
    }
    if (dialog.mode === 'create') {
      body.username = form.username
      if (form.password) body.password = form.password
      await userApi.create(body)
      ElMessage.success('账号已创建')
    } else {
      await userApi.update(dialog.id, body)
      ElMessage.success('资料已更新')
    }
    dialog.open = false
    load()
  } catch {
    /* 校验未过或请求失败，提示已给出 */
  } finally {
    dialog.saving = false
  }
}

/* ------------------------------------------------------------------ 行操作 */

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  const word = next === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定${word}「${row.realName || row.username}」的账号吗？`, `${word}账号`, {
      confirmButtonText: word,
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await userApi.changeStatus(row.id, next)
    ElMessage.success(`已${word}`)
    load()
  } catch {
    /* 提示已由请求层给出 */
  }
}

async function resetPassword(row) {
  try {
    await ElMessageBox.confirm(
      `为「${row.realName || row.username}」生成一个新的初始密码，原密码随即失效。`,
      '重置密码',
      { confirmButtonText: '重置', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const password = await userApi.resetPassword(row.id)
    await ElMessageBox.alert(
      `新密码：${password}。请转告本人，登录后及时修改。`,
      '密码已重置',
      { confirmButtonText: '知道了', type: 'success' }
    )
  } catch {
    /* 提示已由请求层给出 */
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除后「${row.realName || row.username}」将无法登录，其历史答卷仍会保留。`,
      '删除账号',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await userApi.remove(row.id)
    ElMessage.success('账号已删除')
    if (rows.value.length === 1 && query.page > 1) query.page -= 1
    load()
  } catch {
    /* 提示已由请求层给出 */
  }
}

onMounted(async () => {
  load()
  try {
    const res = await userApi.options()
    choices.roles = res.roles || []
    choices.classNames = res.classNames || []
  } catch {
    /* 选项拉取失败时表单仍可手填 */
  }
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">用户中心</h1>
        <p class="page-sub">维护学生、教师与管理员账号，处理登录异常与密码重置。</p>
      </div>
      <div class="page-actions">
        <span v-if="!canManage" class="tag tag-quiet">{{ lockReason }}</span>
        <el-button
          type="primary"
          :disabled="!canManage"
          :title="canManage ? '' : lockReason"
          @click="openCreate"
        >
          新建账号
        </el-button>
      </div>
    </div>

    <section class="card">
      <div class="filter-bar">
        <el-input
          v-model.trim="query.keyword"
          class="filter-keyword"
          placeholder="按账号、姓名、手机号或邮箱搜索"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="query.roleCode" class="filter-item" placeholder="全部角色" clearable @change="search">
          <el-option v-for="role in choices.roles" :key="role.code" :label="role.name" :value="role.code" />
        </el-select>
        <el-select v-model="query.status" class="filter-item" placeholder="全部状态" clearable @change="search">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button @click="search">查询</el-button>
        <el-button text @click="reset">重置条件</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="账号" min-width="130">
          <template #default="{ row }">
            <span class="num">{{ row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column label="姓名" min-width="100">
          <template #default="{ row }">
            <div class="cell-strong">{{ row.realName || '—' }}</div>
            <div v-if="row.phone" class="cell-meta num">{{ row.phone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="170">
          <template #default="{ row }">
            <span v-for="name in row.roleNames || []" :key="name" class="tag role-tag">{{ name }}</span>
            <span v-if="!(row.roleNames || []).length">—</span>
          </template>
        </el-table-column>
        <el-table-column label="班级" min-width="130">
          <template #default="{ row }">{{ row.className || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="tag" :class="row.status === 1 ? 'tag-ok' : 'tag-danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" min-width="150">
          <template #default="{ row }">
            <span class="num">{{ loginTime(row.lastLoginTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button text :disabled="!canManage" :title="canManage ? '' : lockReason" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button
              text
              :disabled="!canManage"
              :title="canManage ? '' : lockReason"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button text :disabled="!canManage" :title="canManage ? '' : lockReason" @click="resetPassword(row)">
              重置密码
            </el-button>
            <el-button text :disabled="!canManage" :title="canManage ? '' : lockReason" @click="remove(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!loading" description="没有符合条件的账号，试试放宽筛选条件" :image-size="72" />
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

    <el-dialog v-model="dialog.open" :title="dialog.mode === 'create' ? '新建账号' : '编辑账号'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="登录账号" prop="username">
            <el-input v-model.trim="form.username" :disabled="dialog.mode === 'edit'" placeholder="学号或工号" />
          </el-form-item>
          <el-form-item label="姓名" prop="realName">
            <el-input v-model.trim="form.realName" placeholder="真实姓名" />
          </el-form-item>
        </div>
        <el-form-item label="角色" prop="roles">
          <el-select v-model="form.roles" multiple placeholder="选择该账号可使用的身份" class="full">
            <el-option v-for="role in choices.roles" :key="role.code" :label="role.name" :value="role.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select
            v-model="form.className"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="选择已有班级，或直接输入新班级名"
            class="full"
          >
            <el-option v-for="name in choices.classNames" :key="name" :label="name" :value="name" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="手机号">
            <el-input v-model.trim="form.phone" placeholder="选填" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model.trim="form.email" placeholder="选填" />
          </el-form-item>
        </div>
        <el-form-item v-if="dialog.mode === 'create'" label="初始密码">
          <el-input v-model="form.password" placeholder="留空则由系统分配，创建后可随时重置" show-password />
        </el-form-item>
        <el-form-item label="账号状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.open = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="submit">保存</el-button>
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
  flex: 1 1 260px;
  max-width: 340px;
}

.filter-item {
  width: 148px;
}

.cell-strong {
  color: var(--ink);
  font-weight: 500;
}

.cell-meta {
  font-size: 12px;
  color: var(--ink-4);
}

.role-tag {
  margin: 2px 6px 2px 0;
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
</style>
