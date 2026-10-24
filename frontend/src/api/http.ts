import axios, { type AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse, Tokens } from '@/types'

const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE || '/api/v1', timeout: 20000 })
let refreshing: Promise<string> | null = null

http.interceptors.request.use(config => {
  const token = localStorage.getItem('pm_access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
http.interceptors.response.use(response => response.data.data, async (error: AxiosError<ApiResponse<unknown>>) => {
  const config = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
  if (error.response?.status === 401 && !config._retry && !config.url?.includes('/auth/')) {
    config._retry = true
    refreshing ||= axios.post<ApiResponse<Tokens>>(`${http.defaults.baseURL}/auth/refresh`, { refreshToken: localStorage.getItem('pm_refresh_token') })
      .then(r => { localStorage.setItem('pm_access_token', r.data.data.accessToken); localStorage.setItem('pm_refresh_token', r.data.data.refreshToken); return r.data.data.accessToken })
      .finally(() => { refreshing = null })
    try { config.headers.Authorization = `Bearer ${await refreshing}`; return http(config) } catch { localStorage.removeItem('pm_user');localStorage.removeItem('pm_access_token');localStorage.removeItem('pm_refresh_token');location.href = '/login' }
  }
  const message = error.response?.data?.message || error.message || '请求失败'
  ElMessage.error(message)
  return Promise.reject(error)
})
export interface ApiClient {
  get<T = any>(url:string, config?:AxiosRequestConfig):Promise<T>
  post<T = any>(url:string, data?:any, config?:AxiosRequestConfig):Promise<T>
  put<T = any>(url:string, data?:any, config?:AxiosRequestConfig):Promise<T>
  delete<T = any>(url:string, config?:AxiosRequestConfig):Promise<T>
}
export default http as unknown as ApiClient
