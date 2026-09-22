import { createRouter, createWebHistory } from 'vue-router'
import { useAccountStore } from '../stores/account'

const shell = () => import('../layouts/shell.vue')

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/login.vue'), meta: { public: true } },
  {
    path: '/exam/take/:examId',
    name: 'studentTake',
    component: () => import('../views/student/take.vue'),
    meta: { roles: ['STUDENT'], fullscreen: true }
  },
  {
    path: '/',
    component: shell,
    children: [
      { path: '', redirect: '/gate' },
      { path: 'gate', name: 'gate', component: () => import('../views/gate.vue') },
      { path: 'profile', name: 'profile', component: () => import('../views/profile.vue') },
      { path: 'student/exams', name: 'studentExams', component: () => import('../views/student/exams.vue'), meta: { roles: ['STUDENT'] } },
      { path: 'student/result/:recordId', name: 'studentResult', component: () => import('../views/student/result.vue'), meta: { roles: ['STUDENT'] } },
      { path: 'student/scores', name: 'studentScores', component: () => import('../views/student/scores.vue'), meta: { roles: ['STUDENT'] } },
      { path: 'student/analysis', name: 'studentAnalysis', component: () => import('../views/student/analysis.vue'), meta: { roles: ['STUDENT'] } },
      { path: 'admin/dashboard', name: 'dashboard', component: () => import('../views/admin/dashboard.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
      { path: 'admin/accounts', name: 'accounts', component: () => import('../views/admin/accounts.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
      { path: 'admin/questions', name: 'questions', component: () => import('../views/admin/questions.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
      { path: 'admin/papers', name: 'papers', component: () => import('../views/admin/papers.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
      { path: 'admin/papers/edit/:id?', name: 'paperEdit', component: () => import('../views/admin/paper-edit.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
      { path: 'admin/exams', name: 'exams', component: () => import('../views/admin/exams.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
      { path: 'admin/grading', name: 'grading', component: () => import('../views/admin/grading.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
      { path: 'admin/scores', name: 'scores', component: () => import('../views/admin/scores.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
      { path: 'admin/statistics', name: 'statistics', component: () => import('../views/admin/statistics.vue'), meta: { roles: ['ADMIN', 'TEACHER'] } },
      { path: 'admin/logs', name: 'logs', component: () => import('../views/admin/logs.vue'), meta: { roles: ['ADMIN'] } }
    ]
  },
  { path: '/403', name: 'forbidden', component: () => import('../views/forbidden.vue'), meta: { public: true } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const account = useAccountStore()
  if (to.meta.public) {
    return true
  }
  if (!account.logged) {
    return { path: '/login', query: to.fullPath === '/' ? {} : { next: to.fullPath } }
  }
  const roles = to.meta.roles
  if (roles && !roles.some((role) => account.roles.includes(role))) {
    return '/403'
  }
  return true
})

export default router
