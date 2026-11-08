<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Dict } from '@/types'

const auth = useAuthStore()
const balances=ref<Dict[]>([]),ledger=ref<Dict[]>([]),receipts=ref<Dict[]>([]),issues=ref<Dict[]>([]),tab=ref('balance')
const warehouses=ref<Dict[]>([]),materials=ref<Dict[]>([])
const detail=ref<Dict>(),drawer=ref(false)
const issueDialog=ref(false),issuing=ref(false)
const issueForm=reactive<Dict>({warehouseId:'',materialId:'',quantity:1,batchNo:'',serialText:'',purpose:'',remark:''})
const selectedMaterial=computed(()=>materials.value.find(m=>m.id===issueForm.materialId))

async function load(){
  const [balanceRows,ledgerRows,receiptRows,issueRows]=await Promise.all([
    http.get('/inventory/balances'),http.get('/inventory/ledger'),http.get('/receipts'),http.get('/inventory/issues')
  ])
  balances.value=balanceRows;ledger.value=ledgerRows;receipts.value=receiptRows;issues.value=issueRows.records
}
async function openReceipt(row:Dict){detail.value=await http.get(`/receipts/${row.id}`);drawer.value=true}
function serialText(value:unknown){if(!value)return '—';if(Array.isArray(value))return value.join('、');try{return JSON.parse(String(value)).join('、')}catch{return String(value)}}

async function openIssue(){
  if(!warehouses.value.length){
    ;[warehouses.value,materials.value]=await Promise.all([http.get('/warehouses'),http.get('/materials')])
  }
  Object.assign(issueForm,{warehouseId:warehouses.value.find(x=>x.enabled)?.id||'',materialId:'',quantity:1,batchNo:'',serialText:'',purpose:'',remark:''})
  issueDialog.value=true
}

async function submitIssue(){
  if(!issueForm.warehouseId||!issueForm.materialId) return ElMessage.warning('请选择仓库与物资')
  if(!issueForm.purpose?.trim()) return ElMessage.warning('请填写领用用途')
  const serials=String(issueForm.serialText||'').split(/[,\n]/).map(x=>x.trim()).filter(Boolean)
  if(selectedMaterial.value?.trackingMode==='SERIAL'){
    if(!Number.isInteger(Number(issueForm.quantity))) return ElMessage.warning('序列号物资数量必须为整数')
    if(serials.length!==Number(issueForm.quantity)) return ElMessage.warning(`需要填写 ${issueForm.quantity} 个序列号，当前 ${serials.length} 个`)
  }
  if(selectedMaterial.value?.trackingMode==='BATCH'&&!issueForm.batchNo?.trim()) return ElMessage.warning('批次物资必须填写批次号')
  issuing.value=true
  try{
    await http.post('/inventory/issues',{
      warehouseId:issueForm.warehouseId,materialId:issueForm.materialId,quantity:issueForm.quantity,
      batchNo:issueForm.batchNo||null,serialNumbers:serials,purpose:issueForm.purpose,remark:issueForm.remark
    })
    ElMessage.success('出库成功，库存与流水已更新')
    issueDialog.value=false
    await load()
  }catch(error:any){ElMessage.error(error?.message||'出库失败')}
  finally{issuing.value=false}
}
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h1>收货与库存</h1><p>数量、批次、序列号库存，完整出入库流水与领用登记</p></div>
      <div class="head-actions">
        <el-button v-if="auth.can('inventory:issue')" type="primary" @click="openIssue">出库/领用</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>
    <div class="panel panel-pad">
      <el-tabs v-model="tab">
        <el-tab-pane label="库存余额" name="balance">
          <el-table :data="balances"><el-table-column prop="warehouse" label="仓库"/><el-table-column prop="materialCode" label="物资编码"/><el-table-column prop="materialName" label="物资名称"/><el-table-column prop="trackingMode" label="跟踪方式"/><el-table-column prop="quantity" label="当前库存"/><el-table-column prop="minStock" label="安全库存"/><el-table-column label="状态"><template #default="s"><el-tag :type="s.row.lowStock?'danger':'success'">{{s.row.lowStock?'库存偏低':'正常'}}</el-tag></template></el-table-column></el-table>
        </el-tab-pane>
        <el-tab-pane label="库存流水" name="ledger">
          <el-table :data="ledger"><el-table-column prop="createdAt" label="时间" width="180"/><el-table-column prop="warehouse" label="仓库"/><el-table-column prop="materialName" label="物资"/><el-table-column prop="businessType" label="业务类型"/><el-table-column prop="direction" label="方向"/><el-table-column prop="quantity" label="变动数量"/><el-table-column prop="balanceAfter" label="结存"/><el-table-column prop="batchNo" label="批次"/></el-table>
        </el-tab-pane>
        <el-tab-pane label="收货记录" name="receipt">
          <el-table :data="receipts"><el-table-column prop="receiptNo" label="收货单号" width="180"/><el-table-column prop="orderNo" label="订单号" width="180"/><el-table-column prop="warehouse" label="仓库"/><el-table-column prop="receiver" label="收货人"/><el-table-column prop="receiptDate" label="收货时间" width="170"/><el-table-column prop="itemCount" label="明细数" width="80"/><el-table-column prop="remark" label="备注"/><el-table-column label="操作" width="80"><template #default="s"><el-button link type="primary" @click="openReceipt(s.row)">详情</el-button></template></el-table-column></el-table>
        </el-tab-pane>
        <el-tab-pane label="出库记录" name="issue">
          <el-table :data="issues"><el-table-column prop="issueNo" label="出库单号" width="180"/><el-table-column prop="warehouse" label="仓库"/><el-table-column prop="materialName" label="物资"/><el-table-column prop="quantity" label="数量" width="90"/><el-table-column prop="batchNo" label="批次"/><el-table-column prop="purpose" label="用途"/><el-table-column prop="department" label="领用部门"/><el-table-column prop="requester" label="领用人"/><el-table-column prop="issuedAt" label="出库时间" width="170"/></el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
    <el-drawer v-model="drawer" title="收货单详情" size="720px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="收货单号">{{detail.receiptNo}}</el-descriptions-item><el-descriptions-item label="订单号">{{detail.orderNo}}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{detail.warehouse}}</el-descriptions-item><el-descriptions-item label="收货人">{{detail.receiver}}</el-descriptions-item>
          <el-descriptions-item label="收货时间">{{detail.receiptDate}}</el-descriptions-item><el-descriptions-item label="备注">{{detail.remark||'—'}}</el-descriptions-item>
        </el-descriptions>
        <h3 class="section-title">收货明细</h3>
        <el-table :data="detail.items" border><el-table-column prop="materialCode" label="物资编码"/><el-table-column prop="materialName" label="物资名称"/><el-table-column prop="trackingMode" label="跟踪" width="90"/><el-table-column prop="quantity" label="数量" width="90"/><el-table-column prop="batchNo" label="批次号"/><el-table-column label="序列号" min-width="180"><template #default="s">{{serialText(s.row.serialNumbers)}}</template></el-table-column></el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="issueDialog" title="出库/领用" width="640px" destroy-on-close>
      <el-form :model="issueForm" label-width="90px">
        <div class="grid">
          <el-form-item label="仓库" required><el-select v-model="issueForm.warehouseId" style="width:100%"><el-option v-for="w in warehouses.filter(x=>x.enabled)" :key="w.id" :label="w.name" :value="w.id"/></el-select></el-form-item>
          <el-form-item label="物资" required><el-select v-model="issueForm.materialId" filterable style="width:100%"><el-option v-for="m in materials.filter(x=>x.enabled)" :key="m.id" :label="`${m.code} ${m.name}`" :value="m.id"/></el-select></el-form-item>
          <el-form-item label="数量" required><el-input-number v-model="issueForm.quantity" :min="0.0001" :precision="4" style="width:100%"/></el-form-item>
          <el-form-item label="批次号"><el-input v-model="issueForm.batchNo" :disabled="selectedMaterial?.trackingMode!=='BATCH'" placeholder="批次物资必填"/></el-form-item>
        </div>
        <el-form-item v-if="selectedMaterial?.trackingMode==='SERIAL'" label="序列号"><el-input v-model="issueForm.serialText" type="textarea" :rows="2" placeholder="每件一个唯一序列号，逗号或换行分隔"/></el-form-item>
        <el-form-item label="领用用途" required><el-input v-model="issueForm.purpose" maxlength="200"/></el-form-item>
        <el-form-item label="备注"><el-input v-model="issueForm.remark" type="textarea" :rows="2" maxlength="500"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="issueDialog=false">取消</el-button><el-button type="primary" :loading="issuing" @click="submitIssue">确认出库</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.section-title{font-size:16px;margin:24px 0 12px}
.head-actions{display:flex;gap:10px}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}
</style>
