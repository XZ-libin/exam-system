import http from './request'

/** 认证与个人中心 */
export const authApi = {
  login: (body) => http.post('/auth/login', body),
  logout: () => http.post('/auth/logout'),
  me: () => http.get('/auth/me'),
  changePassword: (body) => http.put('/auth/password', body),
  updateProfile: (body) => http.put('/auth/profile', body)
}

/** 用户中心 */
export const userApi = {
  page: (params) => http.get('/users', { params }),
  detail: (id) => http.get(`/users/${id}`),
  options: () => http.get('/users/options'),
  create: (body) => http.post('/users', body),
  update: (id, body) => http.put(`/users/${id}`, body),
  assignRoles: (id, roles) => http.post(`/users/${id}/roles`, { roles }),
  changeStatus: (id, status) => http.put(`/users/${id}/status`, { status }),
  resetPassword: (id) => http.post(`/users/${id}/reset-password`),
  remove: (id) => http.delete(`/users/${id}`)
}

/** 题库 */
export const categoryApi = {
  tree: (keyword) => http.get('/categories/tree', { params: { keyword } }),
  create: (body) => http.post('/categories', body),
  update: (id, body) => http.put(`/categories/${id}`, body),
  remove: (id) => http.delete(`/categories/${id}`)
}

export const questionApi = {
  page: (params) => http.get('/questions', { params }),
  detail: (id) => http.get(`/questions/${id}`),
  create: (body) => http.post('/questions', body),
  update: (id, body) => http.put(`/questions/${id}`, body),
  remove: (id) => http.delete(`/questions/${id}`),
  batch: (items) => http.post('/questions/batch', { items }),
  importText: (categoryId, file) => {
    const form = new FormData()
    form.append('file', file)
    return http.post('/questions/import', form, { params: { categoryId } })
  }
}

/** 试卷 */
export const paperApi = {
  page: (params) => http.get('/papers', { params }),
  detail: (id) => http.get(`/papers/${id}`),
  create: (body) => http.post('/papers', body),
  update: (id, body) => http.put(`/papers/${id}`, body),
  remove: (id) => http.delete(`/papers/${id}`),
  addQuestions: (id, items) => http.post(`/papers/${id}/questions`, items),
  removeQuestion: (id, questionId) => http.delete(`/papers/${id}/questions/${questionId}`),
  updateScore: (id, questionId, score) => http.put(`/papers/${id}/questions/${questionId}`, { score }),
  autoGenerate: (body) => http.post('/papers/auto-generate', body),
  preview: (id) => http.get(`/papers/${id}/preview`),
  publish: (id) => http.put(`/papers/${id}/publish`),
  archive: (id) => http.put(`/papers/${id}/archive`)
}

/** 考试 */
export const examApi = {
  page: (params) => http.get('/exams', { params }),
  detail: (id) => http.get(`/exams/${id}`),
  create: (body) => http.post('/exams', body),
  update: (id, body) => http.put(`/exams/${id}`, body),
  remove: (id) => http.delete(`/exams/${id}`),
  changeStatus: (id, status) => http.put(`/exams/${id}/status`, { status }),
  publishScore: (id) => http.post(`/exams/${id}/publish-score`),
  forceSubmit: (id) => http.post(`/exams/${id}/force-submit`)
}

/** 在线答题 */
export const attemptApi = {
  myExams: () => http.get('/exam/my-exams'),
  enter: (examId) => http.post(`/exam/${examId}/enter`),
  save: (recordId, answers) => http.post(`/exam/record/${recordId}/answers`, { answers }),
  reportSwitch: (recordId) => http.post(`/exam/record/${recordId}/switch`),
  submit: (recordId) => http.post(`/exam/record/${recordId}/submit`),
  result: (recordId) => http.get(`/exam/record/${recordId}/result`)
}

/** 阅卷 */
export const gradingApi = {
  pending: (params) => http.get('/grading/pending', { params }),
  pendingCount: (examId) => http.get('/grading/pending-count', { params: { examId } }),
  detail: (recordId) => http.get(`/grading/record/${recordId}`),
  submit: (body) => http.post('/grading/submit', body)
}

/** 成绩 */
export const scoreApi = {
  page: (params) => http.get('/scores', { params }),
  my: (params) => http.get('/scores/my', { params }),
  exportUrl: '/api/scores/export'
}

/** 统计 */
export const statApi = {
  overview: () => http.get('/stats/overview'),
  exam: (id) => http.get(`/stats/exam/${id}`),
  question: (id) => http.get(`/stats/question/${id}`),
  myAnalysis: () => http.get('/stats/my/analysis')
}

/** 日志 */
export const logApi = {
  page: (params) => http.get('/logs', { params })
}
