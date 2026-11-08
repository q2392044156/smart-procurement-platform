<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Dict } from '@/types'

const auth = useAuthStore()
const canEdit = computed(() => auth.can('contract:edit'))
const rows = ref<Dict[]>([])
const suppliers = ref<Dict[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const filters = reactive({ keyword: '', status: '' })
const dialog = ref(false)
const editingId = ref('')
const form = reactive<Dict>({ name: '', supplierId: '', type: 'PURCHASE', amount: 0, signedDate: '', startDate: '', endDate: '', remark: '' })

const statusName: Dict = { DRAFT: '草稿', ACTIVE: '生效中', EXPIRED: '已到期', TERMINATED: '已终止' }
const statusType: Dict = { DRAFT: 'info', ACTIVE: 'success', EXPIRED: 'warning', TERMINATED: 'danger' }
const typeName: Dict = { FRAMEWORK: '框架协议', PURCHASE: '采购合同', SERVICE: '服务合同', NDA: '保密协议' }

async function load() {
  const [data, supplierRows] = await Promise.all([
    http.get('/contracts', { params: { keyword: filters.keyword || undefined, status: filters.status || undefined, current: page.current, size: page.size } }),
    suppliers.value.length ? Promise.resolve(suppliers.value) : http.get('/suppliers')
  ])
  rows.value = data.records
  page.total = Number(data.total)
  suppliers.value = supplierRows
}

function search() { page.current = 1; load() }

function resetForm() {
  editingId.value = ''
  Object.assign(form, { name: '', supplierId: '', type: 'PURCHASE', amount: 0, signedDate: '', startDate: '', endDate: '', remark: '' })
}

function openCreate() { resetForm(); dialog.value = true }

function openEdit(row: Dict) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name, supplierId: row.supplierId, type: row.type, amount: Number(row.amount),
    signedDate: row.signedDate || '', startDate: row.startDate || '', endDate: row.endDate || '', remark: row.remark || ''
  })
  dialog.value = true
}

async function save() {
  if (!form.name?.trim() || !form.supplierId) return ElMessage.warning('请填写合同名称并选择供应商')
  if (editingId.value) await http.put(`/contracts/${editingId.value}`, form)
  else await http.post('/contracts', form)
  ElMessage.success(editingId.value ? '合同已更新' : '合同已创建')
  dialog.value = false
  await load()
}

async function activate(row: Dict) {
  await ElMessageBox.confirm(`确认启用合同“${row.name}”？`, '启用合同', { type: 'warning' })
  await http.post(`/contracts/${row.id}/activate`)
  ElMessage.success('合同已启用')
  await load()
}

async function terminate(row: Dict) {
  await ElMessageBox.confirm(`确认终止合同“${row.name}”？终止后不可恢复。`, '终止合同', { type: 'warning' })
  await http.post(`/contracts/${row.id}/terminate`)
  ElMessage.success('合同已终止')
  await load()
}

async function remove(row: Dict) {
  await ElMessageBox.confirm(`确认删除草稿合同“${row.name}”？`, '删除合同', { type: 'warning' })
  await http.delete(`/contracts/${row.id}`)
  ElMessage.success('合同已删除')
  await load()
}

function money(value: unknown) { return `¥ ${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h1>合同管理</h1><p>供应商合同台账、生效与到期跟踪，可与采购订单关联</p></div>
      <el-button v-if="canEdit" type="primary" @click="openCreate">新建合同</el-button>
    </div>

    <div class="panel panel-pad filters">
      <el-input v-model="filters.keyword" placeholder="合同编号 / 名称 / 供应商" clearable style="width:260px" @keyup.enter="search" />
      <el-select v-model="filters.status" placeholder="状态" clearable style="width:130px">
        <el-option v-for="(name, key) in statusName" :key="key" :label="name" :value="key" />
      </el-select>
      <el-button type="primary" plain @click="search">查询</el-button>
    </div>

    <div class="panel panel-pad">
      <el-table :data="rows" empty-text="暂无合同">
        <el-table-column prop="contractNo" label="合同编号" width="170" />
        <el-table-column prop="name" label="合同名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="supplier" label="供应商" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="110"><template #default="s">{{ typeName[s.row.type] || s.row.type }}</template></el-table-column>
        <el-table-column label="金额" width="140"><template #default="s">{{ money(s.row.amount) }}</template></el-table-column>
        <el-table-column prop="endDate" label="到期日" width="120" />
        <el-table-column prop="orderNo" label="关联订单" width="170" />
        <el-table-column label="状态" width="100"><template #default="s"><el-tag :type="statusType[s.row.status]">{{ statusName[s.row.status] }}</el-tag></template></el-table-column>
        <el-table-column v-if="canEdit" label="操作" width="220" fixed="right">
          <template #default="s">
            <el-button v-if="s.row.status === 'DRAFT'" link type="success" @click="activate(s.row)">启用</el-button>
            <el-button v-if="['DRAFT','ACTIVE'].includes(s.row.status)" link type="primary" @click="openEdit(s.row)">编辑</el-button>
            <el-button v-if="['ACTIVE','EXPIRED'].includes(s.row.status)" link type="warning" @click="terminate(s.row)">终止</el-button>
            <el-button v-if="s.row.status === 'DRAFT'" link type="danger" @click="remove(s.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="page.current" :page-size="page.size" :total="page.total" layout="total, prev, pager, next" background @current-change="load" /></div>
    </div>

    <el-dialog v-model="dialog" :title="editingId ? '编辑合同' : '新建合同'" width="720px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="合同名称" required><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <div class="grid">
          <el-form-item label="供应商" required>
            <el-select v-model="form.supplierId" filterable style="width:100%">
              <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="合同类型" required>
            <el-select v-model="form.type" style="width:100%">
              <el-option v-for="(name, key) in typeName" :key="key" :label="name" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item label="合同金额" required><el-input-number v-model="form.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
          <el-form-item label="签订日期"><el-date-picker v-model="form.signedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="开始日期"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="结束日期"><el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        </div>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filters{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:16px}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}
.pager{display:flex;justify-content:flex-end;margin-top:14px}
</style>
