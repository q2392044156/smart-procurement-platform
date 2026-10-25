<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Dict } from '@/types'

const auth = useAuthStore()
const tab = ref('warehouses')
const warehouses = ref<Dict[]>([])
const units = ref<Dict[]>([])
const brands = ref<Dict[]>([])
const warehouseDialog = ref(false)
const unitDialog = ref(false)
const brandDialog = ref(false)
const editingWarehouseId = ref('')
const editingUnitId = ref('')
const editingBrandId = ref('')
const warehouseForm = reactive<Dict>({ code: '', name: '', address: '', manager: '' })
const unitForm = reactive<Dict>({ code: '', name: '', precisionScale: 0 })
const brandForm = reactive<Dict>({ code: '', name: '' })

async function load() {
  ;[warehouses.value, units.value, brands.value] = await Promise.all([http.get('/warehouses'), http.get('/units'), http.get('/brands')])
}

function openWarehouse(row?: Dict) {
  editingWarehouseId.value = row?.id || ''
  Object.assign(warehouseForm, row ? { code: row.code, name: row.name, address: row.address || '', manager: row.manager || '' } : { code: '', name: '', address: '', manager: '' })
  warehouseDialog.value = true
}

async function saveWarehouse() {
  if (!warehouseForm.code || !warehouseForm.name) { ElMessage.warning('仓库编码和名称不能为空'); return }
  if (editingWarehouseId.value) await http.put(`/warehouses/${editingWarehouseId.value}`, warehouseForm)
  else await http.post('/warehouses', warehouseForm)
  ElMessage.success(editingWarehouseId.value ? '仓库已更新' : '仓库已创建')
  warehouseDialog.value = false
  await load()
}

function openUnit(row?: Dict) {
  editingUnitId.value = row?.id || ''
  Object.assign(unitForm, row ? { code: row.code, name: row.name, precisionScale: Number(row.precisionScale) } : { code: '', name: '', precisionScale: 0 })
  unitDialog.value = true
}

async function saveUnit() {
  if (!unitForm.code || !unitForm.name) { ElMessage.warning('单位编码和名称不能为空'); return }
  if (editingUnitId.value) await http.put(`/units/${editingUnitId.value}`, unitForm)
  else await http.post('/units', unitForm)
  ElMessage.success(editingUnitId.value ? '计量单位已更新' : '计量单位已创建')
  unitDialog.value = false
  await load()
}

function openBrand(row?: Dict) {
  editingBrandId.value = row?.id || ''
  Object.assign(brandForm, row ? { code: row.code, name: row.name } : { code: '', name: '' })
  brandDialog.value = true
}

async function saveBrand() {
  if (!brandForm.code || !brandForm.name) { ElMessage.warning('品牌编码和名称不能为空'); return }
  if (editingBrandId.value) await http.put(`/brands/${editingBrandId.value}`, brandForm)
  else await http.post('/brands', brandForm)
  ElMessage.success(editingBrandId.value ? '品牌已更新' : '品牌已创建')
  brandDialog.value = false
  await load()
}

async function toggle(kind: 'warehouses'|'units'|'brands', row: Dict) {
  await ElMessageBox.confirm(`确认${row.enabled ? '停用' : '启用'}“${row.name}”吗？`, '状态变更', { type: 'warning' })
  await http.patch(`/${kind}/${row.id}/status`, { enabled: !row.enabled })
  ElMessage.success('状态已更新')
  await load()
}

async function remove(kind: 'warehouses'|'units'|'brands', row: Dict) {
  await ElMessageBox.confirm(`确认删除“${row.name}”吗？存在业务引用时后端将拒绝删除。`, '删除确认', { type: 'warning' })
  await http.delete(`/${kind}/${row.id}`)
  ElMessage.success('数据已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head"><div><h1>基础资料</h1><p>维护仓库、计量单位和品牌，引用中的资料只能停用</p></div><el-button @click="load">刷新</el-button></div>
    <div class="panel panel-pad">
      <el-tabs v-model="tab">
        <el-tab-pane label="仓库" name="warehouses">
          <div class="toolbar"><el-button v-if="auth.can('masterdata:warehouse:edit')" type="primary" @click="openWarehouse()">新增仓库</el-button></div>
          <el-table :data="warehouses" stripe><el-table-column prop="code" label="仓库编码" width="150" /><el-table-column prop="name" label="仓库名称" min-width="160" /><el-table-column prop="address" label="地址" min-width="220" /><el-table-column prop="manager" label="负责人" width="120" /><el-table-column prop="balanceCount" label="库存记录" width="100" /><el-table-column prop="receiptCount" label="收货记录" width="100" /><el-table-column label="状态" width="80"><template #default="s"><el-tag :type="s.row.enabled ? 'success' : 'danger'">{{ s.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column v-if="auth.can('masterdata:warehouse:edit')" label="操作" width="190" fixed="right"><template #default="s"><el-button link type="primary" @click="openWarehouse(s.row)">编辑</el-button><el-button link :type="s.row.enabled ? 'warning' : 'success'" @click="toggle('warehouses',s.row)">{{ s.row.enabled ? '停用' : '启用' }}</el-button><el-button link type="danger" @click="remove('warehouses',s.row)">删除</el-button></template></el-table-column></el-table>
        </el-tab-pane>
        <el-tab-pane label="计量单位" name="units">
          <div class="toolbar"><el-button v-if="auth.can('masterdata:unit:edit')" type="primary" @click="openUnit()">新增单位</el-button></div>
          <el-table :data="units" stripe><el-table-column prop="code" label="单位编码" /><el-table-column prop="name" label="单位名称" /><el-table-column prop="precisionScale" label="小数精度" /><el-table-column prop="materialCount" label="引用物资数" /><el-table-column label="状态"><template #default="s"><el-tag :type="s.row.enabled ? 'success' : 'danger'">{{ s.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column v-if="auth.can('masterdata:unit:edit')" label="操作" width="190"><template #default="s"><el-button link type="primary" @click="openUnit(s.row)">编辑</el-button><el-button link :type="s.row.enabled ? 'warning' : 'success'" @click="toggle('units',s.row)">{{ s.row.enabled ? '停用' : '启用' }}</el-button><el-button link type="danger" @click="remove('units',s.row)">删除</el-button></template></el-table-column></el-table>
        </el-tab-pane>
        <el-tab-pane label="品牌" name="brands">
          <div class="toolbar"><el-button v-if="auth.can('masterdata:brand:edit')" type="primary" @click="openBrand()">新增品牌</el-button></div>
          <el-table :data="brands" stripe><el-table-column prop="code" label="品牌编码" /><el-table-column prop="name" label="品牌名称" /><el-table-column prop="materialCount" label="引用物资数" /><el-table-column label="状态"><template #default="s"><el-tag :type="s.row.enabled ? 'success' : 'danger'">{{ s.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column v-if="auth.can('masterdata:brand:edit')" label="操作" width="190"><template #default="s"><el-button link type="primary" @click="openBrand(s.row)">编辑</el-button><el-button link :type="s.row.enabled ? 'warning' : 'success'" @click="toggle('brands',s.row)">{{ s.row.enabled ? '停用' : '启用' }}</el-button><el-button link type="danger" @click="remove('brands',s.row)">删除</el-button></template></el-table-column></el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="warehouseDialog" :title="editingWarehouseId ? '编辑仓库' : '新增仓库'" width="580px"><el-form :model="warehouseForm" label-width="90px"><el-form-item label="仓库编码" required><el-input v-model="warehouseForm.code" /></el-form-item><el-form-item label="仓库名称" required><el-input v-model="warehouseForm.name" /></el-form-item><el-form-item label="地址"><el-input v-model="warehouseForm.address" /></el-form-item><el-form-item label="负责人"><el-input v-model="warehouseForm.manager" /></el-form-item></el-form><template #footer><el-button @click="warehouseDialog=false">取消</el-button><el-button type="primary" @click="saveWarehouse">保存</el-button></template></el-dialog>
    <el-dialog v-model="unitDialog" :title="editingUnitId ? '编辑计量单位' : '新增计量单位'" width="520px"><el-form :model="unitForm" label-width="100px"><el-form-item label="单位编码" required><el-input v-model="unitForm.code" /></el-form-item><el-form-item label="单位名称" required><el-input v-model="unitForm.name" /></el-form-item><el-form-item label="小数精度" required><el-input-number v-model="unitForm.precisionScale" :min="0" :max="6" /></el-form-item></el-form><template #footer><el-button @click="unitDialog=false">取消</el-button><el-button type="primary" @click="saveUnit">保存</el-button></template></el-dialog>
    <el-dialog v-model="brandDialog" :title="editingBrandId ? '编辑品牌' : '新增品牌'" width="520px"><el-form :model="brandForm" label-width="90px"><el-form-item label="品牌编码" required><el-input v-model="brandForm.code" /></el-form-item><el-form-item label="品牌名称" required><el-input v-model="brandForm.name" /></el-form-item></el-form><template #footer><el-button @click="brandDialog=false">取消</el-button><el-button type="primary" @click="saveBrand">保存</el-button></template></el-dialog>
  </div>
</template>

<style scoped>.toolbar{display:flex;justify-content:flex-end;margin-bottom:14px}</style>
