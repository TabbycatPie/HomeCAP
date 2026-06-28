import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  },
  (error) => Promise.reject(error)
)

api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const msg = error.response?.data?.error || error.message || '请求失败'
    ElMessage.error(msg)
    if (error.response?.status === 403 || error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/#/login'
    }
    return Promise.reject(error)
  }
)

export const authApi = {
  login: (data) => api.post('/auth/login', data),
  register: (data) => api.post('/auth/register', data),
}

export const userApi = {
  me: () => api.get('/user/me'),
  changePassword: (data) => api.put('/user/password', data),
}

export const adminUserApi = {
  list: () => api.get('/admin/users'),
  create: (data) => api.post('/admin/users', data),
  delete: (id) => api.delete(`/admin/users/${id}`),
  updateStatus: (id, status) => api.put(`/admin/users/${id}/status`, { status }),
  setPassword: (id, newPassword) => api.put(`/admin/users/${id}/password`, { newPassword }),
}

export const adminAppApi = {
  listPluginTypes: () => api.get('/admin/plugin-types'),
  list: () => api.get('/admin/apps'),
  create: (data) => api.post('/admin/apps', data),
  update: (id, data) => api.put(`/admin/apps/${id}`, data),
  updateStatus: (id, status) => api.put(`/admin/apps/${id}/status`, { status }),
  testConnection: (id) => api.post(`/admin/apps/${id}/test`),
}

export const adminPermApi = {
  grant: (data) => api.post('/admin/permissions/grant', data),
  revoke: (data) => api.post('/admin/permissions/revoke', data),
  getByUser: (userId) => api.get(`/admin/permissions/user/${userId}`),
}

export const userPermApi = {
  myPerms: () => api.get('/user/permissions'),
  apps: () => api.get('/user/apps'),
}

export const ssoApi = {
  access: (appId) => api.post(`/sso/access/${appId}`),
  myApps: () => api.get('/sso/apps'),
}

export const uploadApi = {
  appIcon: (appId, file) => {
    const fd = new FormData()
    fd.append('file', file)
    return api.post(`/upload/app-icon/${appId}`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

export default api
