<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAccountStore } from '../stores/account'

const router = useRouter()
const route = useRoute()
const account = useAccountStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const formRef = ref()

const rules = {
  username: [{ required: true, message: '请输入学号或工号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const demos = [
  { label: '学生', username: '20230101', note: '答题与成绩' },
  { label: '教师', username: 'T2025001', note: '题库、组卷、阅卷' },
  { label: '管理员', username: 'admin', note: '用户与全量数据' }
]

function useDemo(demo) {
  form.username = demo.username
  form.password = '123456'
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await account.login({ ...form })
    ElMessage.success(`欢迎回来，${account.displayName}`)
    router.replace(route.query.next || account.homePath)
  } catch {
    /* 错误提示已由拦截器给出 */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <main class="login-panel">
      <div class="login-brand">
        <span class="brand-mark">考</span>
        <span>在线考试系统</span>
      </div>
      <h1 class="login-title">用学号或工号登录</h1>
      <p class="login-sub">答题、组卷与成绩分析都在这一处完成。</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="submit">
        <el-form-item label="账号" prop="username">
          <el-input v-model.trim="form.username" placeholder="例如 20230101" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password
                    autocomplete="current-password" @keyup.enter="submit" />
        </el-form-item>
        <el-button class="login-submit" type="primary" size="large" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>

      <div class="login-demo">
        <span class="login-demo-label">演示账号（密码 123456）</span>
        <button v-for="demo in demos" :key="demo.username" type="button" class="demo-chip" @click="useDemo(demo)">
          <strong>{{ demo.label }}</strong>
          <span>{{ demo.username }}</span>
          <em>{{ demo.note }}</em>
        </button>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 48px 24px;
  background: var(--canvas);
}

.login-panel {
  width: 100%;
  max-width: 400px;
  background: var(--paper);
  border: 1px solid rgba(0, 0, 0, .06);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 36px 32px 28px;
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-3);
  letter-spacing: -.01em;
}

.login-title {
  margin-top: 22px;
  font-size: 24px;
  line-height: 1.25;
}

.login-sub {
  margin: 6px 0 24px;
  font-size: 14px;
  color: var(--ink-3);
}

.login-submit { width: 100%; height: 46px; font-size: 15px; margin-top: 4px; }

.login-demo {
  margin-top: 26px;
  padding-top: 18px;
  border-top: 1px solid var(--line-soft);
  display: grid;
  gap: 8px;
}

.login-demo-label {
  font-size: 11px;
  letter-spacing: .08em;
  text-transform: uppercase;
  color: var(--ink-4);
}

.demo-chip {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2px 10px;
  align-items: baseline;
  text-align: left;
  padding: 10px 12px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-sm);
  background: var(--paper);
  cursor: pointer;
  font: inherit;
  transition: border-color .18s var(--ease), transform .18s var(--ease);
}

.demo-chip:hover { border-color: var(--line); transform: translateY(-1px); }
.demo-chip:active { transform: scale(.99); }
.demo-chip strong { font-size: 13px; font-weight: 600; color: var(--ink); }
.demo-chip span { font-size: 13px; color: var(--ink-2); justify-self: end; }
.demo-chip em { grid-column: 1 / -1; font-style: normal; font-size: 12px; color: var(--ink-4); }
</style>
