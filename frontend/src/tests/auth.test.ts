import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '@/stores/auth'

describe('RBAC permission state',()=>{
  beforeEach(()=>{localStorage.clear();setActivePinia(createPinia())})
  it('grants all permissions to admin',()=>{const store=useAuthStore();store.user={id:'1',username:'admin',displayName:'管理员',departmentId:'1',roles:['ADMIN'],permissions:[]};expect(store.can('anything')).toBe(true)})
  it('checks explicit permission for normal user',()=>{const store=useAuthStore();store.user={id:'2',username:'requester',displayName:'申请人',departmentId:'2',roles:['APPLICANT'],permissions:['requisition:view']};expect(store.can('requisition:view')).toBe(true);expect(store.can('system:user:view')).toBe(false)})
  it('allows auditor to use audit capability without system administration',()=>{const store=useAuthStore();store.user={id:'8',username:'auditor',displayName:'审计员',departmentId:'1',roles:['AUDITOR'],permissions:['audit:view']};expect(store.can('audit:view')).toBe(true);expect(store.can('system:user:view')).toBe(false)})
})
