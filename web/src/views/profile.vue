<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { useAccountStore } from '../stores/account'

const account = useAccountStore()

const profileRef = ref()
const profileSaving = ref(false)
const profile = reactive({ realName: '', phone: '', email: '' })

const passwordRef = ref()
const passwordSaving = ref(false)
const password = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const user = computed(() => account.user || {})

const profileRules = {
  realName: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 30, message: '新密码长度需在 6 到 30 位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== password.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function dateText(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '从未登录'
}

async function load() {
  try {
    const data = await account.refresh()
    const info = data.user || {}
    profile.realName = info.realName || ''
    profile.phone = info.phone || ''
    profile.email = info.email || ''
  } catch {
    /* 提示已由请求层给出 */
  }
}

async function saveProfile() {
  const valid = await profileRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  profileSaving.value = true
  try {
    await authApi.updateProfile({
      realName: profile.realName.trim(),
      phone: profile.phone.trim() || null,
      email: profile.email.trim() || null
    })
    await account.refresh()
    ElMessage.success('账号资料已保存')
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    profileSaving.value = false
  }
}

async function savePassword() {
  const valid = await passwordRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  passwordSaving.value = true
  try {
    await authApi.changePassword({
      oldPassword: password.oldPassword,
      newPassword: password.newPassword
    })
    passwordRef.value.resetFields()
    ElMessage.success('密码已更新，下次登录请使用新密码')
  } catch {
    /* 提示已由请求层给出 */
  } finally {
    passwordSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">个人中心</h1>
        <p class="page-sub">维护自己的联系资料与登录密码，资料会同步显示在侧栏与顶栏。</p>
      </div>
    </div>

    <div class="grid grid-2">
      <section class="card">
        <div class="card-head">
          <div>
            <h2 class="card-title">账号资料</h2>
            <p class="card-hint">姓名会出现在成绩表与答卷记录上</p>
          </div>
        </div>

        <el-form ref="profileRef" :model="profile" :rules="profileRules" label-position="top" @submit.prevent="saveProfile">
          <el-form-item label="姓名" prop="realName">
            <el-input v-model.trim="profile.realName" maxlength="20" placeholder="请输入真实姓名" />
          </el-form-item>
          <div class="grid grid-2 form-pair">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model.trim="profile.phone" maxlength="11" placeholder="选填，用于接收考试通知" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model.trim="profile.email" maxlength="60" placeholder="选填，例如 teacher@exam.edu" />
            </el-form-item>
          </div>
          <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存资料</el-button>
        </el-form>

        <div class="facts">
          <div class="list-row">
            <span class="metric-label">登录账号</span>
            <span class="fact-value num">{{ user.username || '—' }}</span>
          </div>
          <div class="list-row">
            <span class="metric-label">角色</span>
            <span class="fact-value">{{ (user.roleNames || []).join('、') || '—' }}</span>
          </div>
          <div class="list-row">
            <span class="metric-label">所属班级</span>
            <span class="fact-value">{{ user.className || '教师与管理员无班级' }}</span>
          </div>
          <div class="list-row">
            <span class="metric-label">最近登录</span>
            <span class="fact-value num">{{ dateText(user.lastLoginTime) }}</span>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="card-head">
          <div>
            <h2 class="card-title">修改密码</h2>
            <p class="card-hint">修改后当前登录状态仍然有效，下次登录使用新密码</p>
          </div>
        </div>

        <el-form ref="passwordRef" :model="password" :rules="passwordRules" label-position="top"
                 @submit.prevent="savePassword">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="password.oldPassword" type="password" show-password autocomplete="current-password"
                      placeholder="请输入当前使用的密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="password.newPassword" type="password" show-password autocomplete="new-password"
                      placeholder="6 到 30 位，建议字母与数字组合" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="password.confirmPassword" type="password" show-password autocomplete="new-password"
                      placeholder="请再次输入新密码" />
          </el-form-item>
          <el-button type="primary" :loading="passwordSaving" @click="savePassword">更新密码</el-button>
        </el-form>

        <p class="tip">忘记密码请联系管理员在用户中心重置，重置后请尽快改为自己的密码。</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.grid-2 { align-items: start; }
.form-pair { column-gap: 16px; }
@media (max-width: 760px) {
  .form-pair { grid-template-columns: minmax(0, 1fr); }
}

.facts { margin-top: 22px; padding-top: 6px; }
.fact-value { font-size: 14px; color: var(--ink); }

.tip {
  margin: 22px 0 0;
  padding-top: 16px;
  border-top: 1px solid var(--line-soft);
  font-size: 13px;
  color: var(--ink-3);
}
</style>
