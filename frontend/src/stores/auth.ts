import { defineStore } from 'pinia'
import http from '@/api/http'
import type { CurrentUser, Tokens } from '@/types'

/**
 * 登录态仓库。令牌与用户快照都存 localStorage：
 * pm_access_token（请求头用）、pm_refresh_token（仅换新令牌）、pm_user（用户快照）。
 * 刷新策略在 api/http.ts 的响应拦截器里做单飞重放，本仓库只负责读写与清空。
 * can() 语义：ADMIN 角色恒真，否则看 permissions 是否包含目标权限码。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({ user: JSON.parse(localStorage.getItem('pm_user') || 'null') as CurrentUser | null }),
  getters: {
// 有用户快照且有 access token 才算已登录；缺一都会被守卫送回登录页
    loggedIn: state => !!state.user && !!localStorage.getItem('pm_access_token'),
// 返回判定函数而非布尔值：模板里可直接 auth.can('xxx')，也可在脚本里复用
    can: state => (permission: string) => !!state.user && (state.user.roles.includes('ADMIN') || state.user.permissions.includes(permission))
  },
  actions: {
// 账号密码换令牌并落盘；失败由 http 拦截器统一提示，这里不重复提示
    async login(username:string, password:string) { const data = await http.post<Tokens>('/auth/login', { username, password }); this.save(data) },
// 整页刷新后重新拉取用户；权限变更无需重新登录，下次进入页面即生效
    async loadMe() { this.user = await http.get('/auth/me'); localStorage.setItem('pm_user', JSON.stringify(this.user)) },
// 先尽力通知后端撤销 refresh token，再无论成败清空本地会话（finally 保证）
    async logout() { try { await http.post('/auth/logout', { refreshToken: localStorage.getItem('pm_refresh_token') }) } finally { this.clearSession() } },
// 清空内存与 localStorage 三处登录痕迹，供刷新失败与退出登录复用
    clearSession() { this.user=null;localStorage.removeItem('pm_user');localStorage.removeItem('pm_access_token');localStorage.removeItem('pm_refresh_token') },
// 登录/刷新后的统一落盘：三个 key 必须同时更新，否则会出现半登录态
    save(data:Tokens) { localStorage.setItem('pm_access_token',data.accessToken);localStorage.setItem('pm_refresh_token',data.refreshToken);localStorage.setItem('pm_user',JSON.stringify(data.user));this.user=data.user }
  }
})
