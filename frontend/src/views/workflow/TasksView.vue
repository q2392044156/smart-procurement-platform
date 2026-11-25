<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import type { Dict } from '@/types'
import { formatQty } from '@/utils/datetime'

/**
 * 审批中心：待办/已办两个页签，覆盖采购申请与采购订单两类单据。
 * 数据来源：GET /workflow/tasks、/workflow/tasks/done；预览再按 businessKey 取单据详情。
 * 权限：workflow:approve（路由 meta.permission 已拦截，页面内不再重复判定）。
 * 关键交互：抽屉预览单据 → 填写意见后通过/驳回（驳回原因必填）。
 */

const todo = ref<Dict[]>([])
const done = ref<Dict[]>([])
const tab = ref('todo')
const previewVisible = ref(false)
const previewData = ref<Dict>()
const previewTask = ref<Dict>()
const previewType = computed(() => String(previewTask.value?.businessKey || '').startsWith('REQUISITION:') ? '采购申请' : '采购订单')

// 并发取待办与已办；任一失败由 http 拦截器提示，本页保持旧数据不抖动
async function load() {
  ;[todo.value, done.value] = await Promise.all([http.get('/workflow/tasks'), http.get('/workflow/tasks/done')])
}

// businessKey 形如 REQUISITION:12 / PURCHASE_ORDER:9，转成「采购申请 #12」展示
function businessName(key: unknown) {
  const value = String(key || '')
  if (value.startsWith('REQUISITION:')) return `采购申请 #${value.split(':')[1]}`
  if (value.startsWith('PURCHASE_ORDER:')) return `采购订单 #${value.split(':')[1]}`
  return value || '—'
}

function formatTime(value: unknown) { return value ? new Date(String(value)).toLocaleString('zh-CN', { hour12: false }) : '—' }

// 预览：按 businessKey 前缀决定查哪类单据，先取详情再开抽屉，避免抽屉里空白
async function preview(row: Dict) {
  const [type, id] = String(row.businessKey).split(':')
  previewTask.value = row
  previewData.value = await http.get(type === 'REQUISITION' ? `/requisitions/${id}` : `/purchase-orders/${id}`)
  previewVisible.value = true
}

// 审批通过/驳回：意见必填校验交给弹框，成功后关闭预览并刷新待办与已办
async function decide(row: Dict, action: 'approve' | 'reject') {
  const { value } = await ElMessageBox.prompt(action === 'approve' ? '填写审批意见（可选）' : '填写驳回原因', action === 'approve' ? '通过审批' : '驳回审批', {
    confirmButtonText: action === 'approve' ? '确认通过' : '确认驳回',
    inputType: 'textarea',
    inputValidator: value => action === 'approve' || !!value?.trim() || '驳回原因不能为空'
  })
  await http.post(`/workflow/${row.id}/${action}`, { comment: value })
  ElMessage.success(action === 'approve' ? '审批已通过' : '单据已驳回')
  previewVisible.value = false
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head"><div><h1>审批中心</h1><p>先核对单据内容，再处理采购申请与采购订单审批</p></div><el-button @click="load">刷新</el-button></div>
    <div class="panel panel-pad">
      <el-tabs v-model="tab">
        <el-tab-pane :label="`待办 (${todo.length})`" name="todo">
          <el-table :data="todo">
            <el-table-column prop="name" label="审批节点" />
            <el-table-column label="业务单据"><template #default="s">{{ businessName(s.row.businessKey) }}</template></el-table-column>
            <el-table-column label="到达时间"><template #default="s">{{ formatTime(s.row.createdAt) }}</template></el-table-column>
            <el-table-column label="操作" width="220"><template #default="s"><el-button link @click="preview(s.row)">查看单据</el-button><el-button link type="primary" @click="decide(s.row,'approve')">通过</el-button><el-button link type="danger" @click="decide(s.row,'reject')">驳回</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`已办 (${done.length})`" name="done">
          <el-table :data="done">
            <el-table-column prop="name" label="审批节点" />
            <el-table-column prop="processInstanceId" label="流程实例" min-width="220" />
            <el-table-column label="任务创建时间"><template #default="s">{{ formatTime(s.row.createdAt) }}</template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-drawer v-model="previewVisible" :title="`${previewType}审批`" size="720px">
      <template v-if="previewData && previewTask">
        <el-alert :title="`当前节点：${previewTask.name}`" type="warning" :closable="false" show-icon />
        <el-descriptions :column="2" border class="preview-descriptions">
          <el-descriptions-item label="单据编号">{{ previewData.requisitionNo || previewData.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ previewData.status }}</el-descriptions-item>
          <el-descriptions-item label="金额">¥ {{ Number(previewData.budgetAmount || previewData.totalAmount).toLocaleString() }}</el-descriptions-item>
          <el-descriptions-item label="日期">{{ previewData.requiredDate || previewData.deliveryDate }}</el-descriptions-item>
          <el-descriptions-item :label="previewData.purpose ? '采购用途' : '商业条款'" :span="2">{{ previewData.purpose || previewData.terms || '—' }}</el-descriptions-item>
        </el-descriptions>
        <h3>单据明细</h3>
        <el-table :data="previewData.items" border>
          <el-table-column prop="materialCode" label="物资编码" />
          <el-table-column prop="materialName" label="物资名称" />
          <el-table-column label="数量" width="90"><template #default="s">{{ formatQty(s.row.quantity) }}</template></el-table-column>
          <el-table-column :prop="previewData.requisitionNo ? 'estimatedUnitPrice' : 'unitPrice'" label="单价" width="120" />
          <el-table-column prop="remark" label="备注" />
        </el-table>
        <div class="drawer-actions"><el-button type="danger" plain @click="decide(previewTask,'reject')">驳回</el-button><el-button type="primary" @click="decide(previewTask,'approve')">通过审批</el-button></div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.preview-descriptions{margin-top:18px}h3{font-size:16px;margin:24px 0 12px}.drawer-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:24px}
</style>
