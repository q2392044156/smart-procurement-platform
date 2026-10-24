<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import WorkflowTimeline from '@/components/WorkflowTimeline.vue'
import type { Dict } from '@/types'

const auth = useAuthStore()
const rows = ref<Dict[]>([])
const detail = ref<Dict>()
const warehouses = ref<Dict[]>([])
const drawer = ref(false)
const receiveDialog = ref(false)
const receiving = ref(false)
const receipt = reactive<Dict>({ purchaseOrderId: '', warehouseId: '', remark: '', items: [] })
const status: Dict = { DRAFT: '草稿', PENDING_APPROVAL: '审批中', APPROVED: '待收货', PARTIALLY_RECEIVED: '部分收货', RECEIVED: '已收货', CLOSED: '已关闭', REJECTED: '已驳回', CANCELLED: '已取消' }
const statusType: Dict = { DRAFT: 'info', PENDING_APPROVAL: 'warning', APPROVED: 'success', PARTIALLY_RECEIVED: 'warning', RECEIVED: 'success', CLOSED: 'info', REJECTED: 'danger', CANCELLED: 'info' }

async function load() {
  ;[rows.value, warehouses.value] = await Promise.all([http.get('/purchase-orders'), http.get('/warehouses')])
  if (!receipt.warehouseId && warehouses.value.length) receipt.warehouseId = warehouses.value[0].id
}

async function open(row: Dict) {
  detail.value = await http.get(`/purchase-orders/${row.id}`)
  drawer.value = true
}

async function submit(row: Dict) {
  await ElMessageBox.confirm(`确认提交订单 ${row.orderNo} 进行商业条款审批？`, '提交确认', { type: 'warning' })
  await http.post(`/purchase-orders/${row.id}/submit`)
  ElMessage.success('订单已提交审批')
  await load()
}

async function openReceive(row: Dict) {
  detail.value = await http.get(`/purchase-orders/${row.id}`)
  receipt.purchaseOrderId = row.id
  receipt.remark = ''
  receipt.items = detail.value?.items
    .filter((item: Dict) => Number(item.receivedQuantity) < Number(item.quantity))
    .map((item: Dict) => {
      const remaining = Number(item.quantity) - Number(item.receivedQuantity)
      return { orderItemId: item.id, materialName: item.materialName, trackingMode: item.trackingMode, quantity: remaining, maxQuantity: remaining, batchNo: '', serialText: '' }
    }) || []
  receiveDialog.value = true
}

function validateReceipt() {
  if (!receipt.warehouseId || !receipt.items.length) return '请选择仓库并保留至少一条收货明细'
  for (const item of receipt.items) {
    if (Number(item.quantity) <= 0 || Number(item.quantity) > Number(item.maxQuantity)) return `${item.materialName} 的收货数量必须在剩余数量范围内`
    if (item.trackingMode === 'BATCH' && !item.batchNo?.trim()) return `${item.materialName} 必须填写批次号`
    if (item.trackingMode === 'SERIAL') {
      if (!Number.isInteger(Number(item.quantity))) return `${item.materialName} 的序列号库存数量必须是整数`
      const serials = String(item.serialText || '').split(/[,\n]/).map(x => x.trim()).filter(Boolean)
      if (serials.length !== Number(item.quantity)) return `${item.materialName} 需要填写 ${item.quantity} 个序列号，当前为 ${serials.length} 个`
      if (new Set(serials).size !== serials.length) return `${item.materialName} 本次填写了重复序列号`
    }
  }
}

async function receive() {
  const error = validateReceipt()
  if (error) {
    ElMessage.warning(error)
    return
  }
  const payload = {
    ...receipt,
    items: receipt.items.map((item: Dict) => ({
      orderItemId: item.orderItemId,
      quantity: item.quantity,
      batchNo: item.batchNo || null,
      serialNumbers: item.serialText ? item.serialText.split(/[,\n]/).map((value: string) => value.trim()).filter(Boolean) : []
    }))
  }
  receiving.value = true
  try {
    await http.post('/receipts', payload, { headers: { 'Idempotency-Key': crypto.randomUUID() } })
    ElMessage.success('收货与库存入账成功')
    receiveDialog.value = false
    await load()
  } finally {
    receiving.value = false
  }
}

function isOwner(row: Dict) { return row.purchaserId === auth.user?.id }
function removeReceiptItem(index: number) { receipt.items.splice(index, 1) }
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head"><div><h1>采购订单</h1><p>商业条款审批、交付跟踪与分批收货</p></div><el-button @click="load">刷新</el-button></div>
    <div class="panel panel-pad">
      <el-table :data="rows">
        <el-table-column prop="orderNo" label="订单号" width="170" />
        <el-table-column prop="supplier" label="供应商" min-width="200" />
        <el-table-column prop="purchaser" label="采购员" />
        <el-table-column prop="deliveryDate" label="交付日期" width="120" />
        <el-table-column label="订单金额" width="130"><template #default="s">¥ {{ Number(s.row.totalAmount).toLocaleString() }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="s"><el-tag :type="statusType[s.row.status]">{{ status[s.row.status] }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="s">
            <el-button link @click="open(s.row)">详情</el-button>
            <el-button v-if="['DRAFT','REJECTED'].includes(s.row.status) && auth.can('purchaseorder:edit') && isOwner(s.row)" link type="primary" @click="submit(s.row)">提交审批</el-button>
            <el-button v-if="['APPROVED','PARTIALLY_RECEIVED'].includes(s.row.status) && auth.can('inventory:receive')" link type="success" @click="openReceive(s.row)">收货</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="drawer" title="订单详情" size="720px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusType[detail.status]">{{ status[detail.status] }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="金额">¥ {{ Number(detail.totalAmount).toLocaleString() }}</el-descriptions-item>
          <el-descriptions-item label="税率">{{ Number(detail.taxRate || 0) * 100 }}%</el-descriptions-item>
          <el-descriptions-item label="交付日期">{{ detail.deliveryDate }}</el-descriptions-item>
          <el-descriptions-item label="来源申请">{{ detail.requisitionId || '直接创建' }}</el-descriptions-item>
          <el-descriptions-item label="商业条款" :span="2">{{ detail.terms || '—' }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">订单明细</h3>
        <el-table :data="detail.items" border>
          <el-table-column prop="materialCode" label="物资编码" />
          <el-table-column prop="materialName" label="物资" />
          <el-table-column prop="trackingMode" label="跟踪" width="90" />
          <el-table-column prop="quantity" label="订购" width="80" />
          <el-table-column prop="receivedQuantity" label="已收" width="80" />
          <el-table-column prop="unitPrice" label="单价" width="110" />
        </el-table>
        <WorkflowTimeline business-type="PURCHASE_ORDER" :business-id="detail.id" />
      </template>
    </el-drawer>

    <el-dialog v-model="receiveDialog" title="采购收货" width="900px">
      <el-alert title="收货提交后将与库存入账在同一事务中完成；本次提交自动携带幂等键。" type="info" :closable="false" show-icon />
      <el-form label-width="90px" class="receipt-form">
        <el-form-item label="入库仓库" required><el-select v-model="receipt.warehouseId"><el-option v-for="w in warehouses" :key="w.id" :label="`${w.name}（${w.address}）`" :value="w.id" /></el-select></el-form-item>
        <el-form-item label="收货备注"><el-input v-model="receipt.remark" /></el-form-item>
      </el-form>
      <el-table :data="receipt.items" border>
        <el-table-column prop="materialName" label="物资" min-width="150" />
        <el-table-column label="本次数量" width="150"><template #default="s"><el-input-number v-model="s.row.quantity" :min="1" :max="s.row.maxQuantity" /></template></el-table-column>
        <el-table-column label="剩余可收" width="90"><template #default="s">{{ s.row.maxQuantity }}</template></el-table-column>
        <el-table-column label="批次号" width="170"><template #default="s"><el-input v-if="s.row.trackingMode === 'BATCH'" v-model="s.row.batchNo" placeholder="必填" /><span v-else>—</span></template></el-table-column>
        <el-table-column label="序列号（逗号/换行分隔）" min-width="240"><template #default="s"><el-input v-if="s.row.trackingMode === 'SERIAL'" v-model="s.row.serialText" type="textarea" :rows="2" placeholder="每件对应一个唯一序列号" /><span v-else>—</span></template></el-table-column>
        <el-table-column width="60"><template #default="s"><el-button link type="danger" @click="removeReceiptItem(s.$index)">移除</el-button></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="receiveDialog=false">取消</el-button><el-button type="primary" :loading="receiving" @click="receive">确认收货并入账</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.section-title{font-size:16px;margin:24px 0 12px}.receipt-form{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:18px}.receipt-form :deep(.el-form-item){margin-bottom:8px}
</style>
