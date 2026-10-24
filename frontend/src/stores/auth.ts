import { defineStore } from 'pinia'
import http from '@/api/http'
import type { CurrentUser, Tokens } from '@/types'

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: JSON.parse(localStorage.getItem('pm_user') || 'null') as CurrentUser | null }),
  getters: {
    loggedIn: state => !!state.user && !!localStorage.getItem('pm_access_token'),
    can: state => (permission: string) => !!state.user && (state.user.roles.includes('ADMIN') || state.user.permissions.includes(permission))
  },
  actions: {
    async login(username:string, password:string) { const data = await http.post<Tokens>('/auth/login', { username, password }); this.save(data) },
    async loadMe() { this.user = await http.get('/auth/me'); localStorage.setItem('pm_user', JSON.stringify(this.user)) },
    async logout() { try { await http.post('/auth/logout', { refreshToken: localStorage.getItem('pm_refresh_token') }) } finally { this.clearSession() } },
    clearSession() { this.user=null;localStorage.removeItem('pm_user');localStorage.removeItem('pm_access_token');localStorage.removeItem('pm_refresh_token') },
    save(data:Tokens) { localStorage.setItem('pm_access_token',data.accessToken);localStorage.setItem('pm_refresh_token',data.refreshToken);localStorage.setItem('pm_user',JSON.stringify(data.user));this.user=data.user }
  }
})
