<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import WorkflowTimeline from '@/components/WorkflowTimeline.vue'
import BusinessAttachments from '@/components/BusinessAttachments.vue'
import type { Dict } from '@/types'

const auth = useAuthStore()
const rows = ref<Dict[]>([])
const keyword = ref('')
const page = reactive({ current: 1, size: 10, total: 0 })
const materials = ref<Dict[]>([])
const suppliers = ref<Dict[]>([])
const editorVisible = ref(false)
const detailVisible = ref(false)
const convertVisible = ref(false)
const editingId = ref<string>()
const detail = ref<Dict>()
const convertItems = ref<Dict[]>([])
const saving = ref(false)

const emptyItem = () => ({ materialId: '', quantity: 1, estimatedUnitPrice: 0, remark: '' })
const form = reactive<Dict>({ requiredDate: '', purpose: '', items: [emptyItem()] })
const convert = reactive<Dict>({ supplierId: '', deliveryDate: '', taxRate: 0.13, terms: '按合同约定交货', priceOverrides: {} })
const editorTitle = computed(() => editingId.value ? '编辑采购申请' : '新建采购申请')
const budgetTotal = computed(() => form.items.reduce((sum: number, item: Dict) => sum + Number(item.quantity || 0) * Number(item.estimatedUnitPrice || 0), 0))
const orderTotal = computed(() => convertItems.value.reduce((sum, item) => sum + Number(item.quantity) * Number(convert.priceOverrides[item.materialId] ?? item.estimatedUnitPrice), 0))

const statusName: Dict = { DRAFT: '草稿', PENDING_APPROVAL: '审批中', APPROVED: '已批准', REJECTED: '已驳回', CONVERTED: '已转订单', CANCELLED: '已取消' }
const statusType: Dict = { DRAFT: 'info', PENDING_APPROVAL: 'warning', APPROVED: 'success', REJECTED: 'danger', CONVERTED: 'success' }

async function load() {
  const [pageData, materialRows, supplierRows] = await Promise.all([
    http.get('/requisitions/page', { params: { keyword: keyword.value, current: page.current, size: page.size } }),
    http.get('/materials'), http.get('/suppliers')
  ])
  rows.value = pageData.records
  page.total = Number(pageData.total)
  materials.value = materialRows
  suppliers.value = supplierRows
}

function search() { page.current = 1; load() }

function resetForm() {
  Object.assign(form, { requiredDate: '', purpose: '', items: [emptyItem()] })
  editingId.value = undefined
}

function openCreate() {
  resetForm()
  editorVisible.value = true
}

async function openDetail(row: Dict) {
  detail.value = await http.get(`/requisitions/${row.id}`)
  detailVisible.value = true
}

async function openEdit(row: Dict) {
  const data: Dict = await http.get(`/requisitions/${row.id}`)
  editingId.value = row.id
  Object.assign(form, {
    requiredDate: data.requiredDate,
    purpose: data.purpose,
    items: data.items.map((item: Dict) => ({
      materialId: item.materialId,
      quantity: Number(item.quantity),
      estimatedUnitPrice: Number(item.estimatedUnitPrice),
      remark: item.remark || ''
    }))
  })
  editorVisible.value = true
}

async function save() {
  if (!form.requiredDate || !form.purpose?.trim() || !form.items.length || form.items.some((x: Dict) => !x.materialId)) {
    ElMessage.warning('请完整填写需求日期、用途和物资明细')
    return
  }
  saving.value = true
  try {
    if (editingId.value) await http.put(`/requisitions/${editingId.value}`, form)
    else await http.post('/requisitions', form)
    ElMessage.success(editingId.value ? '采购申请已更新为草稿' : '采购申请已保存')
    editorVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function submit(row: Dict) {
  await ElMessageBox.confirm(`确认提交申请 ${row.requisitionNo}？提交后将进入审批流程。`, '提交确认', { type: 'warning' })
  await http.post(`/requisitions/${row.id}/submit`)
  ElMessage.success('已进入审批流程')
  await load()
}

async function cancel(row: Dict) {
  const { value } = await ElMessageBox.prompt(`请输入取消申请 ${row.requisitionNo} 的原因`, '取消申请', { inputPattern: /\S+/, inputErrorMessage: '取消原因不能为空', type: 'warning' })
  await http.patch(`/requisitions/${row.id}/cancel`, { reason: value })
  ElMessage.success('采购申请已取消')
  detailVisible.value = false
  await load()
}

async function remove(row: Dict) {
  await ElMessageBox.confirm(`确认删除申请 ${row.requisitionNo}？删除后列表中将不再显示。`, '删除确认', { type: 'warning' })
  await http.delete(`/requisitions/${row.id}`)
  ElMessage.success('采购申请已删除')
  detailVisible.value = false
  await load()
}

async function openConvert(row: Dict) {
  const data: Dict = await http.get(`/requisitions/${row.id}`)
  detail.value = data
  convertItems.value = data.items
  Object.assign(convert, { supplierId: '', deliveryDate: '', taxRate: 0.13, terms: '按合同约定交货', priceOverrides: {} })
  for (const item of data.items) convert.priceOverrides[item.materialId] = Number(item.estimatedUnitPrice)
  convertVisible.value = true
}

async function doConvert() {
  if (!convert.supplierId || !convert.deliveryDate) {
    ElMessage.warning('请选择供应商和交付日期')
    return
  }
  await http.post(`/requisitions/${detail.value?.id}/convert`, convert)
  ElMessage.success('采购订单已生成，可前往采购订单页面提交审批')
  convertVisible.value = false
  await load()
}

function isOwner(row: Dict) { return row.applicantId === auth.user?.id }
function canManage(row: Dict) { return isOwner(row) || !!auth.user?.roles.includes('ADMIN') }
function addItem() { form.items.push(emptyItem()) }
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h1>采购申请</h1><p>按金额自动匹配审批链，驳回后可修改并重新提交</p></div>
      <el-button v-if="auth.can('requisition:create')" type="primary" @click="openCreate">新建申请</el-button>
    </div>

    <div class="panel panel-pad">
      <div class="toolbar"><el-input v-model="keyword" placeholder="申请单号或用途" clearable style="width:280px" @keyup.enter="search" /><el-button @click="search">查询</el-button></div>
      <el-table :data="rows">
        <el-table-column prop="requisitionNo" label="申请单号" width="170" />
        <el-table-column prop="department" label="部门" />
        <el-table-column prop="applicant" label="申请人" />
        <el-table-column prop="purpose" label="用途" min-width="200" show-overflow-tooltip />
        <el-table-column prop="requiredDate" label="需求日期" width="120" />
        <el-table-column label="预算金额" width="130"><template #default="s">¥ {{ Number(s.row.budgetAmount).toLocaleString() }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="s"><el-tag :type="statusType[s.row.status]">{{ statusName[s.row.status] }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="s">
            <el-button link @click="openDetail(s.row)">详情</el-button>
            <el-button v-if="['DRAFT','REJECTED'].includes(s.row.status) && isOwner(s.row)" link type="primary" @click="openEdit(s.row)">编辑</el-button>
            <el-button v-if="['DRAFT','REJECTED'].includes(s.row.status) && auth.can('requisition:submit') && isOwner(s.row)" link type="primary" @click="submit(s.row)">提交</el-button>
            <el-button v-if="s.row.status === 'APPROVED' && auth.can('purchaseorder:edit')" link type="success" @click="openConvert(s.row)">转订单</el-button>
            <el-button v-if="['DRAFT','REJECTED','PENDING_APPROVAL','APPROVED'].includes(s.row.status) && auth.can('requisition:cancel') && canManage(s.row)" link type="warning" @click="cancel(s.row)">取消</el-button>
            <el-button v-if="['DRAFT','CANCELLED'].includes(s.row.status) && auth.can('requisition:delete') && canManage(s.row)" link type="danger" @click="remove(s.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="page.current" :page-size="page.size" :total="page.total" layout="total, prev, pager, next" background @current-change="load" /></div>
    </div>

    <el-dialog v-model="editorVisible" :title="editorTitle" width="900px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <div class="form-grid">
          <el-form-item label="需求日期" required><el-date-picker v-model="form.requiredDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="预算合计"><el-input :model-value="`¥ ${budgetTotal.toLocaleString()}`" disabled /></el-form-item>
        </div>
        <el-form-item label="采购用途" required><el-input v-model="form.purpose" type="textarea" :rows="2" /></el-form-item>
        <el-table :data="form.items" border>
          <el-table-column label="物资" min-width="230"><template #default="s"><el-select v-model="s.row.materialId" filterable style="width:100%"><el-option v-for="m in materials" :key="m.id" :label="`${m.code} ${m.name}`" :value="m.id" /></el-select></template></el-table-column>
          <el-table-column label="数量" width="130"><template #default="s"><el-input-number v-model="s.row.quantity" :min="1" /></template></el-table-column>
          <el-table-column label="预估单价" width="160"><template #default="s"><el-input-number v-model="s.row.estimatedUnitPrice" :min="0" :precision="2" /></template></el-table-column>
          <el-table-column label="备注"><template #default="s"><el-input v-model="s.row.remark" /></template></el-table-column>
          <el-table-column width="60"><template #default="s"><el-button link type="danger" :disabled="form.items.length === 1" @click="form.items.splice(s.$index, 1)">删</el-button></template></el-table-column>
        </el-table>
        <el-button text type="primary" @click="addItem">+ 添加明细</el-button>
      </el-form>
      <template #footer><el-button @click="editorVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存草稿</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="采购申请详情" size="720px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请单号">{{ detail.requisitionNo }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusType[detail.status]">{{ statusName[detail.status] }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="需求日期">{{ detail.requiredDate }}</el-descriptions-item>
          <el-descriptions-item label="预算金额">¥ {{ Number(detail.budgetAmount).toLocaleString() }}</el-descriptions-item>
          <el-descriptions-item label="用途" :span="2">{{ detail.purpose }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.rejectionReason" label="驳回原因" :span="2"><span class="danger-text">{{ detail.rejectionReason }}</span></el-descriptions-item>
          <el-descriptions-item v-if="detail.cancellationReason" label="取消原因" :span="2"><span class="danger-text">{{ detail.cancellationReason }}</span></el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">采购明细</h3>
        <el-table :data="detail.items" border>
          <el-table-column prop="materialCode" label="物资编码" />
          <el-table-column prop="materialName" label="物资名称" />
          <el-table-column prop="quantity" label="数量" width="90" />
          <el-table-column prop="estimatedUnitPrice" label="预估单价" width="120" />
          <el-table-column prop="remark" label="备注" />
        </el-table>
        <BusinessAttachments business-type="REQUISITION" :business-id="detail.id" :editable="['DRAFT','REJECTED'].includes(detail.status) && canManage(detail)" />
        <WorkflowTimeline business-type="REQUISITION" :business-id="detail.id" />
      </template>
    </el-drawer>

    <el-dialog v-model="convertVisible" title="生成采购订单" width="760px">
      <el-form :model="convert" label-width="100px">
        <div class="form-grid">
          <el-form-item label="供应商" required><el-select v-model="convert.supplierId" filterable style="width:100%"><el-option v-for="s in suppliers.filter(x=>x.status==='QUALIFIED')" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item>
          <el-form-item label="交付日期" required><el-date-picker v-model="convert.deliveryDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="税率"><el-input-number v-model="convert.taxRate" :step="0.01" :min="0" :max="1" style="width:100%" /></el-form-item>
          <el-form-item label="订单总额"><el-input :model-value="`¥ ${orderTotal.toLocaleString()}`" disabled /></el-form-item>
        </div>
        <el-form-item label="商业条款"><el-input v-model="convert.terms" type="textarea" /></el-form-item>
        <el-table :data="convertItems" border>
          <el-table-column prop="materialName" label="物资" />
          <el-table-column prop="quantity" label="数量" width="100" />
          <el-table-column label="订单单价" width="180"><template #default="s"><el-input-number v-model="convert.priceOverrides[s.row.materialId]" :min="0" :precision="2" /></template></el-table-column>
        </el-table>
      </el-form>
      <template #footer><el-button @click="convertVisible=false">取消</el-button><el-button type="primary" @click="doConvert">生成订单</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}.section-title{font-size:16px;margin:24px 0 12px}.danger-text{color:#d94b4b}.pager{display:flex;justify-content:flex-end;margin-top:14px}.toolbar{display:flex;gap:10px;margin-bottom:14px}
</style>
