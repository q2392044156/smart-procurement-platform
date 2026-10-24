<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Dict } from '@/types'

const auth = useAuthStore()
const users = ref<Dict[]>([])
const departments = ref<Dict[]>([])
const roles = ref<Dict[]>([])
const permissions = ref<Dict[]>([])
const audits = ref<Dict[]>([])
const tab = ref('users')
const dataScopeName: Dict = { ALL: '全部数据', DEPARTMENT: '当前部门', DEPARTMENT_AND_CHILDREN: '当前部门及下级', SELF: '仅本人' }
const permissionType: Dict = { MENU: '菜单', BUTTON: '按钮', API: '接口' }

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

async function load() {
  const requests: Promise<unknown>[] = [http.get('/users'), http.get('/departments'), http.get('/roles'), http.get('/permissions')]
  const result = await Promise.all(requests)
  users.value = result[0] as Dict[]
  departments.value = result[1] as Dict[]
  roles.value = result[2] as Dict[]
  permissions.value = result[3] as Dict[]
  if (auth.can('audit:view')) audits.value = await http.get('/audits')
}

function formatTime(value: unknown) { return value ? new Date(String(value)).toLocaleString('zh-CN', { hour12: false }) : '—' }
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head"><div><h1>系统与审计</h1><p>组织、角色、数据范围、权限项及关键操作留痕</p></div><el-button @click="load">刷新</el-button></div>
    <div class="panel panel-pad">
      <el-tabs v-model="tab">
        <el-tab-pane label="用户" name="users">
          <el-table :data="users">
            <el-table-column prop="username" label="账号" />
            <el-table-column prop="displayName" label="姓名" />
            <el-table-column prop="department" label="部门" />
            <el-table-column label="状态"><template #default="s"><el-tag :type="s.row.enabled ? 'success' : 'danger'">{{ s.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="组织架构" name="departments">
          <el-table :data="departmentTree" row-key="id" default-expand-all>
            <el-table-column prop="name" label="部门名称" />
            <el-table-column prop="code" label="部门编码" />
            <el-table-column prop="parentId" label="上级部门ID"><template #default="s">{{ s.row.parentId || '—' }}</template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="角色与数据范围" name="roles">
          <el-table :data="roles">
            <el-table-column prop="code" label="角色编码" />
            <el-table-column prop="name" label="角色名称" />
            <el-table-column label="数据范围"><template #default="s"><el-tag>{{ dataScopeName[s.row.dataScope] || s.row.dataScope }}</el-tag></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="权限清单" name="permissions">
          <el-table :data="permissions">
            <el-table-column prop="code" label="权限编码" min-width="220" />
            <el-table-column prop="name" label="权限名称" />
            <el-table-column label="类型" width="100"><template #default="s"><el-tag type="info">{{ permissionType[s.row.type] || s.row.type }}</el-tag></template></el-table-column>
            <el-table-column prop="parentCode" label="上级权限"><template #default="s">{{ s.row.parentCode || '—' }}</template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane v-if="auth.can('audit:view')" label="操作审计" name="audit">
          <el-table :data="audits" height="560">
            <el-table-column label="时间" width="180"><template #default="s">{{ formatTime(s.row.createdAt) }}</template></el-table-column>
            <el-table-column prop="username" label="用户" width="110" />
            <el-table-column prop="method" label="方法" width="80" />
            <el-table-column prop="path" label="接口" min-width="260" show-overflow-tooltip />
            <el-table-column label="结果" width="90"><template #default="s"><el-tag :type="s.row.result === 'SUCCESS' ? 'success' : 'danger'">{{ s.row.result }}</el-tag></template></el-table-column>
            <el-table-column prop="durationMs" label="耗时(ms)" width="90" />
            <el-table-column prop="requestId" label="请求ID" min-width="220" show-overflow-tooltip />
            <el-table-column prop="errorMessage" label="错误信息" min-width="180" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>
