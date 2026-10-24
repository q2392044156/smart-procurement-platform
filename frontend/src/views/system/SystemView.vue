<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Dict } from '@/types'

const auth = useAuthStore()
const users = ref<Dict[]>([])
const departments = ref<Dict[]>([])
const roles = ref<Dict[]>([])
const permissions = ref<Dict[]>([])
const audits = ref<Dict[]>([])
const canViewSystem = computed(() => auth.can('system:user:view'))
const tab = ref(canViewSystem.value ? 'users' : 'audit')
const userDialog = ref(false)
const departmentDialog = ref(false)
const roleDialog = ref(false)
const editingUserId = ref('')
const editingDepartmentId = ref('')
const editingRoleId = ref('')
const dataScopeName: Dict = { ALL: '全部数据', DEPARTMENT: '当前部门', DEPARTMENT_AND_CHILDREN: '当前部门及下级', SELF: '仅本人' }
const permissionType: Dict = { MENU: '菜单', BUTTON: '按钮', API: '接口' }
const userForm = reactive<Dict>({ username: '', password: '', displayName: '', departmentId: '', roleIds: [] })
const departmentForm = reactive<Dict>({ parentId: null, code: '', name: '', sortOrder: 0 })
const roleForm = reactive<Dict>({ code: '', name: '', dataScope: 'SELF', permissionIds: [] })

const departmentTree = computed(() => {
  const nodes = new Map<string, Dict>()
  departments.value.forEach(item => nodes.set(item.id, { ...item, children: [] }))
  const roots: Dict[] = []
  nodes.forEach(node => {
    const parent = node.parentId && nodes.get(node.parentId)
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
})

const permissionGroups = computed(() => permissions.value.filter(item => item.type === 'MENU').map(menu => ({
  menu,
  children: permissions.value.filter(item => item.parentCode === menu.code)
})))

async function load() {
  if (canViewSystem.value) {
    const result = await Promise.all([http.get('/users'), http.get('/departments'), http.get('/roles'), http.get('/permissions')])
    users.value = result[0] as Dict[]
    departments.value = result[1] as Dict[]
    roles.value = result[2] as Dict[]
    permissions.value = result[3] as Dict[]
  }
  if (auth.can('audit:view')) audits.value = await http.get('/audits')
}

function openUser(row?: Dict) {
  editingUserId.value = row?.id || ''
  Object.assign(userForm, row ? {
    username: row.username, password: '', displayName: row.displayName,
    departmentId: row.departmentId, roleIds: [...(row.roleIds || [])]
  } : { username: '', password: '', displayName: '', departmentId: departments.value.find(x => x.enabled)?.id || '', roleIds: [] })
  userDialog.value = true
}

async function saveUser() {
  if (!userForm.username || !userForm.displayName || !userForm.departmentId || !userForm.roleIds.length || (!editingUserId.value && !userForm.password)) {
    ElMessage.warning('请完整填写用户信息并至少分配一个角色')
    return
  }
  const payload = { displayName: userForm.displayName, departmentId: userForm.departmentId, roleIds: userForm.roleIds }
  if (editingUserId.value) await http.put(`/users/${editingUserId.value}`, payload)
  else await http.post('/users', { ...payload, username: userForm.username, password: userForm.password })
  ElMessage.success(editingUserId.value ? '用户已更新' : '用户已创建')
  userDialog.value = false
  await load()
}

async function toggleUser(row: Dict) {
  const action = row.enabled ? '停用' : '启用'
  await ElMessageBox.confirm(`确认${action}用户“${row.displayName}”吗？`, `${action}用户`, { type: 'warning' })
  await http.patch(`/users/${row.id}/status`, { enabled: !row.enabled })
  ElMessage.success(`用户已${action}`)
  await load()
}

async function resetPassword(row: Dict) {
  const result = await ElMessageBox.prompt(`请输入“${row.displayName}”的新密码`, '重置密码', {
    inputType: 'password', inputPlaceholder: '至少8位，包含大小写、数字和特殊字符',
    inputPattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{8,72}$/,
    inputErrorMessage: '密码强度不符合要求'
  })
  await http.post(`/users/${row.id}/reset-password`, { password: result.value })
  ElMessage.success('密码已重置，用户现有刷新令牌已撤销')
}

async function removeUser(row: Dict) {
  await ElMessageBox.confirm(`删除后用户“${row.displayName}”将不能登录，确认继续？`, '删除用户', { type: 'warning' })
  await http.delete(`/users/${row.id}`)
  ElMessage.success('用户已删除')
  await load()
}

function openDepartment(row?: Dict, parentId?: string) {
  editingDepartmentId.value = row?.id || ''
  Object.assign(departmentForm, row ? { parentId: row.parentId || null, code: row.code, name: row.name, sortOrder: row.sortOrder } : { parentId: parentId || null, code: '', name: '', sortOrder: 0 })
  departmentDialog.value = true
}

async function saveDepartment() {
  if (!departmentForm.code || !departmentForm.name) { ElMessage.warning('部门编码和名称不能为空'); return }
  if (editingDepartmentId.value) await http.put(`/departments/${editingDepartmentId.value}`, departmentForm)
  else await http.post('/departments', departmentForm)
  ElMessage.success(editingDepartmentId.value ? '部门已更新' : '部门已创建')
  departmentDialog.value = false
  await load()
}

async function toggleDepartment(row: Dict) {
  await ElMessageBox.confirm(`确认${row.enabled ? '停用' : '启用'}部门“${row.name}”吗？`, '部门状态', { type: 'warning' })
  await http.patch(`/departments/${row.id}/status`, { enabled: !row.enabled })
  ElMessage.success('部门状态已更新')
  await load()
}

async function removeDepartment(row: Dict) {
  await ElMessageBox.confirm(`确认删除部门“${row.name}”吗？`, '删除部门', { type: 'warning' })
  await http.delete(`/departments/${row.id}`)
  ElMessage.success('部门已删除')
  await load()
}

function openRole(row?: Dict) {
  editingRoleId.value = row?.id || ''
  Object.assign(roleForm, row ? { code: row.code, name: row.name, dataScope: row.dataScope, permissionIds: [...(row.permissionIds || [])] } : { code: '', name: '', dataScope: 'SELF', permissionIds: [] })
  roleDialog.value = true
}

async function saveRole() {
  if (!roleForm.code || !roleForm.name) { ElMessage.warning('角色编码和名称不能为空'); return }
  if (editingRoleId.value) await http.put(`/roles/${editingRoleId.value}`, roleForm)
  else await http.post('/roles', roleForm)
  ElMessage.success(editingRoleId.value ? '角色已更新' : '角色已创建')
  roleDialog.value = false
  await load()
}

function togglePermission(id: string, checked: boolean) {
  const values = new Set(roleForm.permissionIds as string[])
  checked ? values.add(id) : values.delete(id)
  roleForm.permissionIds = [...values]
}

function togglePermissionGroup(group: Dict, checked: boolean) {
  const ids = [group.menu.id, ...group.children.map((item: Dict) => item.id)]
  ids.forEach(id => togglePermission(id, checked))
}

function onPermissionChange(item: Dict, value: unknown) {
  const checked = Boolean(value)
  togglePermission(item.id, checked)
  if (checked && item.parentCode) {
    const parent = permissions.value.find(permission => permission.code === item.parentCode)
    if (parent) togglePermission(parent.id, true)
  }
}
function onPermissionGroupChange(group: Dict, value: unknown) { togglePermissionGroup(group, Boolean(value)) }

async function toggleRole(row: Dict) {
  await ElMessageBox.confirm(`确认${row.enabled ? '停用' : '启用'}角色“${row.name}”吗？`, '角色状态', { type: 'warning' })
  await http.patch(`/roles/${row.id}/status`, { enabled: !row.enabled })
  ElMessage.success('角色状态已更新')
  await load()
}

async function removeRole(row: Dict) {
  await ElMessageBox.confirm(`确认删除角色“${row.name}”吗？`, '删除角色', { type: 'warning' })
  await http.delete(`/roles/${row.id}`)
  ElMessage.success('角色已删除')
  await load()
}

function formatTime(value: unknown) { return value ? new Date(String(value)).toLocaleString('zh-CN', { hour12: false }) : '—' }
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head"><div><h1>系统与审计</h1><p>维护用户、组织、角色、数据范围与权限，关键变更自动留痕</p></div><el-button @click="load">刷新</el-button></div>
    <div class="panel panel-pad">
      <el-tabs v-model="tab">
        <el-tab-pane v-if="canViewSystem" label="用户管理" name="users">
          <div class="toolbar"><el-button v-if="auth.can('system:user:create')" type="primary" @click="openUser()">新增用户</el-button></div>
          <el-table :data="users" stripe>
            <el-table-column prop="username" label="账号" width="150" /><el-table-column prop="displayName" label="姓名" width="150" /><el-table-column prop="department" label="部门" width="150" />
            <el-table-column label="角色" min-width="220"><template #default="s"><el-tag v-for="role in s.row.roles" :key="role" class="role-tag" type="info">{{ role }}</el-tag></template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="s"><el-tag :type="s.row.enabled ? 'success' : 'danger'">{{ s.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="310" fixed="right"><template #default="s">
              <el-button v-if="auth.can('system:user:edit')" link type="primary" @click="openUser(s.row)">编辑</el-button><el-button v-if="auth.can('system:user:disable')" link :type="s.row.enabled ? 'warning' : 'success'" @click="toggleUser(s.row)">{{ s.row.enabled ? '停用' : '启用' }}</el-button><el-button v-if="auth.can('system:user:reset-password')" link @click="resetPassword(s.row)">重置密码</el-button><el-button v-if="auth.can('system:user:edit')" link type="danger" @click="removeUser(s.row)">删除</el-button>
            </template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane v-if="canViewSystem" label="组织架构" name="departments">
          <div class="toolbar"><el-button v-if="auth.can('system:department:edit')" type="primary" @click="openDepartment()">新增一级部门</el-button></div>
          <el-table :data="departmentTree" row-key="id" default-expand-all>
            <el-table-column prop="name" label="部门名称" min-width="180" /><el-table-column prop="code" label="部门编码" width="140" /><el-table-column prop="userCount" label="用户数" width="90" /><el-table-column prop="sortOrder" label="排序" width="80" />
            <el-table-column label="状态" width="90"><template #default="s"><el-tag :type="s.row.enabled ? 'success' : 'danger'">{{ s.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column v-if="auth.can('system:department:edit')" label="操作" width="280"><template #default="s"><el-button link type="primary" @click="openDepartment(undefined,s.row.id)">新增下级</el-button><el-button link @click="openDepartment(s.row)">编辑</el-button><el-button link :type="s.row.enabled ? 'warning' : 'success'" @click="toggleDepartment(s.row)">{{ s.row.enabled ? '停用' : '启用' }}</el-button><el-button link type="danger" @click="removeDepartment(s.row)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane v-if="canViewSystem" label="角色与权限" name="roles">
          <div class="toolbar"><el-button v-if="auth.can('system:role:edit')" type="primary" @click="openRole()">新增角色</el-button></div>
          <el-table :data="roles" stripe>
            <el-table-column prop="code" label="角色编码" min-width="190" /><el-table-column prop="name" label="角色名称" width="150" /><el-table-column label="数据范围" width="160"><template #default="s"><el-tag>{{ dataScopeName[s.row.dataScope] || s.row.dataScope }}</el-tag></template></el-table-column><el-table-column prop="userCount" label="用户数" width="90" /><el-table-column label="类型" width="90"><template #default="s">{{ s.row.builtIn ? '内置' : '自定义' }}</template></el-table-column><el-table-column label="状态" width="90"><template #default="s"><el-tag :type="s.row.enabled ? 'success' : 'danger'">{{ s.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column v-if="auth.can('system:role:edit')" label="操作" width="210"><template #default="s"><el-button link type="primary" :disabled="s.row.code === 'ADMIN'" @click="openRole(s.row)">编辑权限</el-button><el-button link :type="s.row.enabled ? 'warning' : 'success'" :disabled="s.row.code === 'ADMIN'" @click="toggleRole(s.row)">{{ s.row.enabled ? '停用' : '启用' }}</el-button><el-button v-if="!s.row.builtIn" link type="danger" @click="removeRole(s.row)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane v-if="canViewSystem" label="权限清单" name="permissions">
          <el-table :data="permissions"><el-table-column prop="code" label="权限编码" min-width="240" /><el-table-column prop="name" label="权限名称" /><el-table-column label="类型" width="100"><template #default="s"><el-tag type="info">{{ permissionType[s.row.type] || s.row.type }}</el-tag></template></el-table-column><el-table-column prop="parentCode" label="上级权限"><template #default="s">{{ s.row.parentCode || '—' }}</template></el-table-column></el-table>
        </el-tab-pane>

        <el-tab-pane v-if="auth.can('audit:view')" label="操作审计" name="audit">
          <el-table :data="audits" height="560"><el-table-column label="时间" width="180"><template #default="s">{{ formatTime(s.row.createdAt) }}</template></el-table-column><el-table-column prop="username" label="用户" width="110" /><el-table-column prop="method" label="方法" width="80" /><el-table-column prop="path" label="接口" min-width="260" show-overflow-tooltip /><el-table-column label="结果" width="90"><template #default="s"><el-tag :type="s.row.result === 'SUCCESS' ? 'success' : 'danger'">{{ s.row.result }}</el-tag></template></el-table-column><el-table-column prop="durationMs" label="耗时(ms)" width="90" /><el-table-column prop="requestId" label="请求ID" min-width="220" show-overflow-tooltip /><el-table-column prop="errorMessage" label="错误信息" min-width="180" show-overflow-tooltip /></el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="userDialog" :title="editingUserId ? '编辑用户' : '新增用户'" width="600px"><el-form :model="userForm" label-width="100px"><el-form-item label="登录账号" required><el-input v-model="userForm.username" :disabled="!!editingUserId" placeholder="字母开头，可使用数字、点、横线和下划线" /></el-form-item><el-form-item v-if="!editingUserId" label="初始密码" required><el-input v-model="userForm.password" type="password" show-password placeholder="至少8位，包含大小写、数字和特殊字符" /></el-form-item><el-form-item label="姓名" required><el-input v-model="userForm.displayName" /></el-form-item><el-form-item label="所属部门" required><el-select v-model="userForm.departmentId" filterable style="width:100%"><el-option v-for="item in departments.filter(x=>x.enabled)" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" /></el-select></el-form-item><el-form-item label="角色" required><el-select v-model="userForm.roleIds" multiple filterable style="width:100%"><el-option v-for="item in roles.filter(x=>x.enabled)" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item></el-form><template #footer><el-button @click="userDialog=false">取消</el-button><el-button type="primary" @click="saveUser">保存</el-button></template></el-dialog>

    <el-dialog v-model="departmentDialog" :title="editingDepartmentId ? '编辑部门' : '新增部门'" width="560px"><el-form :model="departmentForm" label-width="100px"><el-form-item label="上级部门"><el-select v-model="departmentForm.parentId" clearable filterable style="width:100%"><el-option v-for="item in departments.filter(x=>x.enabled && x.id !== editingDepartmentId)" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" /></el-select></el-form-item><el-form-item label="部门编码" required><el-input v-model="departmentForm.code" placeholder="例如 QA 或 MFG_01" /></el-form-item><el-form-item label="部门名称" required><el-input v-model="departmentForm.name" /></el-form-item><el-form-item label="排序"><el-input-number v-model="departmentForm.sortOrder" :min="0" /></el-form-item></el-form><template #footer><el-button @click="departmentDialog=false">取消</el-button><el-button type="primary" @click="saveDepartment">保存</el-button></template></el-dialog>

    <el-dialog v-model="roleDialog" :title="editingRoleId ? '编辑角色权限' : '新增角色'" width="760px"><el-form :model="roleForm" label-width="100px"><el-form-item label="角色编码" required><el-input v-model="roleForm.code" :disabled="!!editingRoleId" placeholder="大写字母和下划线" /></el-form-item><el-form-item label="角色名称" required><el-input v-model="roleForm.name" /></el-form-item><el-form-item label="数据范围" required><el-select v-model="roleForm.dataScope" style="width:100%"><el-option v-for="(name,code) in dataScopeName" :key="code" :label="name" :value="code" /></el-select></el-form-item><el-form-item label="功能权限"><div class="permission-list"><div v-for="group in permissionGroups" :key="group.menu.id" class="permission-group"><div class="permission-title"><el-checkbox :model-value="roleForm.permissionIds.includes(group.menu.id)" @change="onPermissionGroupChange(group, $event)">{{ group.menu.name }}</el-checkbox><code>{{ group.menu.code }}</code></div><div class="permission-children"><el-checkbox v-for="item in group.children" :key="item.id" :model-value="roleForm.permissionIds.includes(item.id)" @change="onPermissionChange(item, $event)">{{ item.name }}</el-checkbox></div></div></div></el-form-item></el-form><template #footer><el-button @click="roleDialog=false">取消</el-button><el-button type="primary" @click="saveRole">保存</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.toolbar{display:flex;justify-content:flex-end;margin-bottom:14px}.role-tag{margin-right:5px}.permission-list{width:100%;border:1px solid var(--el-border-color);border-radius:6px;max-height:380px;overflow:auto}.permission-group{padding:10px 14px;border-bottom:1px solid var(--el-border-color-lighter)}.permission-group:last-child{border-bottom:0}.permission-title{display:flex;align-items:center;justify-content:space-between}.permission-title code{font-size:12px;color:var(--el-text-color-secondary)}.permission-children{display:flex;flex-wrap:wrap;gap:0 18px;padding:8px 0 0 24px}
</style>
