import axios, { type AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse, Tokens } from '@/types'

/**
 * 全局 axios 实例：baseURL 取 VITE_API_BASE（缺省 /api/v1），超时 20 秒。
 * 响应拦截器负责拆包统一契约 { code, message, data }，调用方直接拿到 data。
 */
const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE || '/api/v1', timeout: 20000 })
// 并发 401 只触发一次刷新，其余请求复用同一个 Promise，避免刷新风暴
let refreshing: Promise<string> | null = null

// 请求拦截：从 localStorage 取 access token 注入 Authorization，鉴权不依赖 cookie
http.interceptors.request.use(config => {
  const token = localStorage.getItem('pm_access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
/**
 * 响应拦截：401 且未重放过时，用 refresh token 换新令牌并重放原请求。
 * _retry 标记保证每个请求最多重放一次，/auth/ 下的登录与刷新接口自身不参与重放。
 * 刷新失败则清空本地登录态并跳登录页；其余错误统一 ElMessage 提示后继续 reject。
 */
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
/**
 * 取二进制内容（图表/文档）。
 *
 * 浏览器无法给 <img src> 带上 Authorization 头，所以统一走这里取 Blob，
 * 再由调用方换成 objectURL 展示或触发下载。
 */
export async function fetchBlob(path: string): Promise<Blob> {
  const token = localStorage.getItem('pm_access_token')
  const response = await axios.get<Blob>(`${http.defaults.baseURL}${path}`, {
    responseType: 'blob',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  return response.data
}

/**
 * 对外使用的精简客户端类型：拦截器已把响应拆成业务数据，因此五个方法直接返回 T。
 */
export interface ApiClient {
  get<T = any>(url:string, config?:AxiosRequestConfig):Promise<T>
  post<T = any>(url:string, data?:any, config?:AxiosRequestConfig):Promise<T>
  put<T = any>(url:string, data?:any, config?:AxiosRequestConfig):Promise<T>
  patch<T = any>(url:string, data?:any, config?:AxiosRequestConfig):Promise<T>
  delete<T = any>(url:string, config?:AxiosRequestConfig):Promise<T>
}
// 类型断言只在此处收敛一次：运行时行为由上面的拦截器决定
export default http as unknown as ApiClient
