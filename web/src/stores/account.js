import { defineStore } from 'pinia'
import { authApi } from '../api'
import { TOKEN_KEY, USER_KEY } from '../api/request'

function readCache(key) {
  try {
    return JSON.parse(localStorage.getItem(key)) || null
  } catch {
    return null
  }
}

export const useAccountStore = defineStore('account', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readCache(USER_KEY)
  }),
  getters: {
    logged: (state) => !!state.token,
    roles: (state) => (state.user && state.user.roles) || [],
    isStudent: (state) => ((state.user && state.user.roles) || []).includes('STUDENT'),
    isTeacher: (state) => ((state.user && state.user.roles) || []).includes('TEACHER'),
    isAdmin: (state) => ((state.user && state.user.roles) || []).includes('ADMIN'),
    /** 教师与管理员都能进控制台；只有学生时落到学生端 */
    homePath: (state) => {
      const roles = (state.user && state.user.roles) || []
      if (roles.includes('ADMIN') || roles.includes('TEACHER')) return '/admin/dashboard'
      return '/student/exams'
    },
    displayName: (state) => (state.user && state.user.realName) || ''
  },
  actions: {
    async login(payload) {
      const data = await authApi.login(payload)
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      return data
    },
    async refresh() {
      const data = await authApi.me()
      this.user = data.user
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      return data
    },
    async logout() {
      try {
        await authApi.logout()
      } catch {
        /* 退出失败也要清本地态 */
      }
      this.clear()
    },
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
