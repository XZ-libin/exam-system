<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAccountStore } from '../stores/account'

const route = useRoute()
const router = useRouter()
const account = useAccountStore()

const consoleNav = [
  { to: '/admin/dashboard', label: '工作台' },
  { to: '/admin/questions', label: '题库管理' },
  { to: '/admin/papers', label: '试卷管理' },
  { to: '/admin/exams', label: '考试管理' },
  { to: '/admin/grading', label: '主观题阅卷' },
  { to: '/admin/scores', label: '成绩管理' },
  { to: '/admin/statistics', label: '统计分析' },
  { to: '/admin/accounts', label: '用户中心' },
  { to: '/admin/logs', label: '操作日志', roles: ['ADMIN'] }
]

const studentNav = [
  { to: '/student/exams', label: '我的考试' },
  { to: '/student/scores', label: '我的成绩' },
  { to: '/student/analysis', label: '学习分析' }
]

const navItems = computed(() => {
  const list = account.isStudent && !account.isTeacher && !account.isAdmin ? studentNav : consoleNav
  return list.filter((item) => !item.roles || item.roles.some((role) => account.roles.includes(role)))
})

const navGroup = computed(() => {
  const studentOnly = account.isStudent && !account.isTeacher && !account.isAdmin
  return studentOnly ? '学生' : '控制台'
})

const initials = computed(() => (account.displayName || '').slice(0, 1))

async function signOut() {
  await ElMessageBox.confirm('退出后需要重新登录，确定继续吗？', '退出登录', {
    confirmButtonText: '退出',
    cancelButtonText: '再看看',
    type: 'warning'
  })
  await account.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">考</span>
        <span class="brand-name">在线考试系统</span>
      </div>

      <nav>
        <div class="nav-group-label">{{ navGroup }}</div>
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          class="nav-item"
          :class="{ active: route.path.startsWith(item.to) }"
          :to="item.to"
        >
          {{ item.label }}
        </RouterLink>

        <div class="nav-group-label">账号</div>
        <RouterLink class="nav-item" :class="{ active: route.path === '/profile' }" to="/profile">个人中心</RouterLink>
      </nav>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="identity">
          <span class="avatar-dot">{{ initials }}</span>
          <span>
            <span class="identity-name">{{ account.displayName }}</span>
            <span v-if="account.user && account.user.className"> · {{ account.user.className }}</span>
          </span>
        </div>
        <el-button text @click="signOut">退出登录</el-button>
      </header>

      <RouterView />
    </div>
  </div>
</template>
