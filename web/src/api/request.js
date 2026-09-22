import axios from 'axios'
import { ElMessage } from 'element-plus'

export const TOKEN_KEY = 'exam-token'
export const USER_KEY = 'exam-account'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

let redirecting = false

function toLogin() {
  if (redirecting) return
  redirecting = true
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  window.location.replace('/login')
}

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (!body || typeof body.code !== 'number') {
      return body
    }
    if (body.code === 0) {
      return body.data
    }
    if (body.code === 1001) {
      toLogin()
    } else {
      ElMessage.error(body.message || '操作没有成功')
    }
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    const status = error.response && error.response.status
    if (status === 401) {
      toLogin()
    } else if (status === 403) {
      ElMessage.error((error.response.data && error.response.data.message) || '没有该操作权限')
    } else if (status) {
      ElMessage.error(`服务返回 ${status}，请稍后重试`)
    } else {
      ElMessage.error('连不上后端服务，请确认容器已启动')
    }
    return Promise.reject(error)
  }
)

export default http
