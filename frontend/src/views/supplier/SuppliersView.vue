<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Dict } from '@/types'

const auth = useAuthStore()
const rows = ref<Dict[]>([])
const keyword = ref('')
const editorVisible = ref(false)
const detailVisible = ref(false)
const editingId = ref<string>()
const detail = ref<Dict>()
const contactVisible = ref(false)
const qualificationVisible = ref(false)
const evaluationVisible = ref(false)
const editingContactId = ref('')
const editingQualificationId = ref('')
const editingEvaluationId = ref('')
const qualificationFile = ref<File>()
const form = reactive<Dict>({ code: '', name: '', unifiedCreditCode: '', level: 'B', status: 'PENDING', contactName: '', contactPhone: '', email: '', address: '' })
const contactForm = reactive<Dict>({ name: '', position: '', phone: '', email: '', primaryContact: false })
const qualificationForm = reactive<Dict>({ name: '', fileId: null, fileName: '', validFrom: '', validUntil: '' })
const evaluationForm = reactive<Dict>({ period: '2026-Q4', qualityScore: 90, deliveryScore: 90, serviceScore: 90, comment: '' })
const statusName: Dict = { PENDING: '待审核', QUALIFIED: '合格', SUSPENDED: '暂停', BLACKLISTED: '黑名单' }
const statusType: Dict = { PENDING: 'warning', QUALIFIED: 'success', SUSPENDED: 'info', BLACKLISTED: 'danger' }
const qualificationStatus: Dict = { VALID: '有效', EXPIRING: '即将到期', EXPIRED: '已过期', REVOKED: '已撤销' }
const qualificationType: Dict = { VALID: 'success', EXPIRING: 'warning', EXPIRED: 'danger', REVOKED: 'info' }
const editorTitle = computed(() => editingId.value ? '编辑供应商' : '供应商准入')
const canProfile = computed(() => auth.can('supplier:edit') || auth.can('supplier:profile:edit'))
const canQualification = computed(() => auth.can('supplier:edit') || auth.can('supplier:qualification:edit'))
const canEvaluation = computed(() => auth.can('supplier:edit') || auth.can('supplier:evaluation:edit'))

async function load() { rows.value = await http.get('/suppliers', { params: { keyword: keyword.value } }) }

function resetForm() {
  editingId.value = undefined
  Object.assign(form, { code: '', name: '', unifiedCreditCode: '', level: 'B', status: 'PENDING', contactName: '', contactPhone: '', email: '', address: '' })
}

function openCreate() { resetForm(); editorVisible.value = true }

async function openDetail(row: Dict) {
  detail.value = await http.get(`/suppliers/${row.id}`)
  detailVisible.value = true
}

async function refreshDetail() {
  if (detail.value?.id) detail.value = await http.get(`/suppliers/${detail.value.id}`)
  await load()
}

async function openEdit(row: Dict) {
  const data: Dict = await http.get(`/suppliers/${row.id}`)
  editingId.value = row.id
  Object.assign(form, data)
  editorVisible.value = true
}

async function save() {
  if (!form.code.trim() || !form.name.trim()) {
    ElMessage.warning('供应商编码和名称不能为空')
    return
  }
  if (editingId.value) await http.put(`/suppliers/${editingId.value}`, form)
  else await http.post('/suppliers', form)
  ElMessage.success(editingId.value ? '供应商资料已更新' : '供应商已创建')
  editorVisible.value = false
  await load()
}

async function changeStatus(row: Dict, status: string) {
  await ElMessageBox.confirm(`确认将“${row.name}”变更为${statusName[status]}状态吗？`, '供应商状态变更', { type: 'warning' })
  await http.patch(`/suppliers/${row.id}/status`, { status })
  ElMessage.success('供应商状态已更新')
  await load()
}

async function handleStatusCommand(row: Dict, command: string | number | object) {
  await changeStatus(row, String(command))
}

async function removeSupplier(row: Dict) {
  await ElMessageBox.confirm(`确认删除“${row.name}”吗？已有采购订单时后端将拒绝删除。`, '删除供应商', { type: 'warning' })
  await http.delete(`/suppliers/${row.id}`)
  ElMessage.success('供应商已删除')
  await load()
}

function openContact(row?: Dict) {
  editingContactId.value = row?.id || ''
  Object.assign(contactForm, row ? { name: row.name, position: row.position || '', phone: row.phone || '', email: row.email || '', primaryContact: !!row.primaryContact } : { name: '', position: '', phone: '', email: '', primaryContact: false })
  contactVisible.value = true
}

async function saveContact() {
  if (!contactForm.name) { ElMessage.warning('联系人姓名不能为空'); return }
  const base = `/suppliers/${detail.value!.id}/contacts`
  if (editingContactId.value) await http.put(`${base}/${editingContactId.value}`, contactForm)
  else await http.post(base, contactForm)
  ElMessage.success('联系人已保存'); contactVisible.value = false; await refreshDetail()
}

async function removeContact(row: Dict) {
  await ElMessageBox.confirm(`确认删除联系人“${row.name}”吗？`, '删除联系人', { type: 'warning' })
  await http.delete(`/suppliers/${detail.value!.id}/contacts/${row.id}`); await refreshDetail()
}

function openQualification(row?: Dict) {
  editingQualificationId.value = row?.id || ''
  qualificationFile.value = undefined
  Object.assign(qualificationForm, row ? { name: row.name, fileId: row.fileId || null, fileName: row.fileName || '', validFrom: row.validFrom || '', validUntil: row.validUntil || '' } : { name: '', fileId: null, fileName: '', validFrom: '', validUntil: '' })
  qualificationVisible.value = true
}

function chooseQualificationFile(file: any) { qualificationFile.value = file.raw }

async function uploadQualificationFile() {
  if (!qualificationFile.value) return qualificationForm.fileId || null
  const file = qualificationFile.value
  const contentType = ['application/pdf', 'image/jpeg', 'image/png'].includes(file.type) ? file.type : 'application/octet-stream'
  const presign: Dict = await http.post('/files/presign', { fileName: file.name, contentType, size: file.size })
  const response = await fetch(presign.uploadUrl, { method: 'PUT', headers: { 'Content-Type': contentType }, body: file })
  if (!response.ok) throw new Error('资质附件上传失败')
  return presign.fileId
}

async function saveQualification() {
  if (!qualificationForm.name) { ElMessage.warning('资质名称不能为空'); return }
  const payload = { name: qualificationForm.name, fileId: await uploadQualificationFile(), validFrom: qualificationForm.validFrom || null, validUntil: qualificationForm.validUntil || null }
  const base = `/suppliers/${detail.value!.id}/qualifications`
  if (editingQualificationId.value) await http.put(`${base}/${editingQualificationId.value}`, payload)
  else await http.post(base, payload)
  ElMessage.success('资质记录已保存'); qualificationVisible.value = false; await refreshDetail()
}

async function removeQualification(row: Dict) {
  await ElMessageBox.confirm(`确认删除资质“${row.name}”吗？合格供应商必须保留至少一项有效资质。`, '删除资质', { type: 'warning' })
  await http.delete(`/suppliers/${detail.value!.id}/qualifications/${row.id}`); await refreshDetail()
}

async function downloadQualification(row: Dict) {
  const file: Dict = await http.get(`/files/${row.fileId}`)
  window.open(file.downloadUrl, '_blank', 'noopener')
}

function openEvaluation(row?: Dict) {
  editingEvaluationId.value = row?.id || ''
  Object.assign(evaluationForm, row ? { period: row.period, qualityScore: Number(row.qualityScore), deliveryScore: Number(row.deliveryScore), serviceScore: Number(row.serviceScore), comment: row.comment || '' } : { period: '2026-Q4', qualityScore: 90, deliveryScore: 90, serviceScore: 90, comment: '' })
  evaluationVisible.value = true
}

async function saveEvaluation() {
  const base = `/suppliers/${detail.value!.id}/evaluations`
  if (editingEvaluationId.value) await http.put(`${base}/${editingEvaluationId.value}`, evaluationForm)
  else await http.post(base, evaluationForm)
  ElMessage.success('绩效评价已保存并重新计算供应商评分'); evaluationVisible.value = false; await refreshDetail()
}

async function removeEvaluation(row: Dict) {
  await ElMessageBox.confirm(`确认删除${row.period}绩效评价吗？删除后将重新计算供应商评分。`, '删除评价', { type: 'warning' })
  await http.delete(`/suppliers/${detail.value!.id}/evaluations/${row.id}`); await refreshDetail()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h1>供应商管理</h1><p>供应商准入、分级、资质与绩效档案</p></div>
      <el-button v-if="canProfile" type="primary" @click="openCreate">新增供应商</el-button>
    </div>
    <div class="panel panel-pad">
      <div class="toolbar"><el-input v-model="keyword" clearable placeholder="供应商编码或名称" style="width:280px" @keyup.enter="load" /><el-button @click="load">查询</el-button></div>
      <el-table :data="rows">
        <el-table-column prop="code" label="编码" width="130" />
        <el-table-column prop="name" label="供应商" min-width="220" />
        <el-table-column prop="level" label="等级" width="80"><template #default="s"><el-tag>{{ s.row.level }}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="s"><el-tag :type="statusType[s.row.status]">{{ statusName[s.row.status] }}</el-tag></template></el-table-column>
        <el-table-column prop="contactName" label="联系人" />
        <el-table-column prop="contactPhone" label="电话" />
        <el-table-column prop="score" label="绩效评分" width="100" />
        <el-table-column prop="qualificationCount" label="资质" width="70" />
        <el-table-column label="操作" width="270" fixed="right"><template #default="s"><el-button link @click="openDetail(s.row)">档案</el-button><template v-if="canProfile"><el-button link type="primary" @click="openEdit(s.row)">编辑</el-button><el-dropdown trigger="click" @command="handleStatusCommand(s.row,$event)"><el-button link type="warning">状态</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="(value,key) in statusName" :key="key" :command="key" :disabled="key===s.row.status">{{ value }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown><el-button link type="danger" @click="removeSupplier(s.row)">删除</el-button></template></template></el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="editorVisible" :title="editorTitle" width="660px">
      <el-form :model="form" label-width="110px">
        <div class="grid">
          <el-form-item label="供应商编码" required><el-input v-model="form.code" :disabled="!!editingId" /></el-form-item>
          <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="统一信用代码"><el-input v-model="form.unifiedCreditCode" /></el-form-item>
          <el-form-item label="等级"><el-select v-model="form.level" style="width:100%"><el-option v-for="x in ['A','B','C','D']" :key="x" :label="x" :value="x" /></el-select></el-form-item>
          <el-form-item label="状态"><el-input :model-value="statusName[form.status]" disabled /><div class="form-tip">新供应商默认待审核，请在录入有效资质后通过列表状态操作设为合格。</div></el-form-item>
          <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
          <el-form-item label="地址" class="full"><el-input v-model="form.address" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="editorVisible=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="供应商档案" size="760px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="供应商编码">{{ detail.code }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="统一信用代码">{{ detail.unifiedCreditCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="等级/状态"><el-tag>{{ detail.level }}</el-tag> <el-tag :type="statusType[detail.status]">{{ statusName[detail.status] }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detail.contactName || '—' }} {{ detail.contactPhone }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detail.email || '—' }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ detail.address || '—' }}</el-descriptions-item>
        </el-descriptions>
        <div class="section-head"><h3>联系人</h3><el-button v-if="canProfile" type="primary" link @click="openContact()">新增联系人</el-button></div>
        <el-table :data="detail.contacts" border empty-text="暂无联系人">
          <el-table-column prop="name" label="姓名" /><el-table-column prop="position" label="职位" /><el-table-column prop="phone" label="电话" /><el-table-column prop="email" label="邮箱" min-width="180" />
          <el-table-column label="类型" width="80"><template #default="s"><el-tag v-if="s.row.primaryContact" type="success">主要</el-tag></template></el-table-column>
          <el-table-column v-if="canProfile" label="操作" width="110"><template #default="s"><el-button link type="primary" @click="openContact(s.row)">编辑</el-button><el-button link type="danger" @click="removeContact(s.row)">删除</el-button></template></el-table-column>
        </el-table>
        <div class="section-head"><h3>资质证照</h3><el-button v-if="canQualification" type="primary" link @click="openQualification()">新增资质</el-button></div>
        <el-table :data="detail.qualifications" border empty-text="暂无资质记录">
          <el-table-column prop="name" label="资质名称" />
          <el-table-column prop="validFrom" label="生效日期" />
          <el-table-column prop="validUntil" label="有效期至" />
          <el-table-column label="状态"><template #default="s"><el-tag :type="qualificationType[s.row.status]">{{ qualificationStatus[s.row.status] || s.row.status }}</el-tag></template></el-table-column>
          <el-table-column label="附件" width="100"><template #default="s"><el-button v-if="s.row.fileId" link @click="downloadQualification(s.row)">下载</el-button><span v-else>—</span></template></el-table-column>
          <el-table-column v-if="canQualification" label="操作" width="110"><template #default="s"><el-button link type="primary" @click="openQualification(s.row)">编辑</el-button><el-button link type="danger" @click="removeQualification(s.row)">删除</el-button></template></el-table-column>
        </el-table>
        <div class="section-head"><h3>绩效评价</h3><el-button v-if="canEvaluation" type="primary" link @click="openEvaluation()">新增评价</el-button></div>
        <el-table :data="detail.evaluations" border empty-text="暂无评价记录">
          <el-table-column prop="period" label="期间" />
          <el-table-column prop="qualityScore" label="质量" />
          <el-table-column prop="deliveryScore" label="交付" />
          <el-table-column prop="serviceScore" label="服务" />
          <el-table-column prop="totalScore" label="总分" />
          <el-table-column prop="comment" label="评价" min-width="160" />
          <el-table-column v-if="canEvaluation" label="操作" width="110"><template #default="s"><el-button link type="primary" @click="openEvaluation(s.row)">编辑</el-button><el-button link type="danger" @click="removeEvaluation(s.row)">删除</el-button></template></el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="contactVisible" :title="editingContactId ? '编辑联系人' : '新增联系人'" width="560px"><el-form :model="contactForm" label-width="90px"><el-form-item label="姓名" required><el-input v-model="contactForm.name" /></el-form-item><el-form-item label="职位"><el-input v-model="contactForm.position" /></el-form-item><el-form-item label="电话"><el-input v-model="contactForm.phone" /></el-form-item><el-form-item label="邮箱"><el-input v-model="contactForm.email" /></el-form-item><el-form-item label="主要联系人"><el-switch v-model="contactForm.primaryContact" /></el-form-item></el-form><template #footer><el-button @click="contactVisible=false">取消</el-button><el-button type="primary" @click="saveContact">保存</el-button></template></el-dialog>

    <el-dialog v-model="qualificationVisible" :title="editingQualificationId ? '编辑资质' : '新增资质'" width="620px"><el-form :model="qualificationForm" label-width="100px"><el-form-item label="资质名称" required><el-input v-model="qualificationForm.name" /></el-form-item><el-form-item label="有效期"><el-date-picker v-model="qualificationForm.validFrom" value-format="YYYY-MM-DD" type="date" placeholder="生效日期" style="width:190px" /><span class="date-separator">至</span><el-date-picker v-model="qualificationForm.validUntil" value-format="YYYY-MM-DD" type="date" placeholder="失效日期" style="width:190px" /></el-form-item><el-form-item label="证照附件"><el-upload :auto-upload="false" :limit="1" :on-change="chooseQualificationFile" :show-file-list="true" accept=".pdf,.jpg,.jpeg,.png"><el-button>选择文件</el-button></el-upload><span v-if="qualificationForm.fileName" class="current-file">当前：{{ qualificationForm.fileName }}</span></el-form-item></el-form><template #footer><el-button @click="qualificationVisible=false">取消</el-button><el-button type="primary" @click="saveQualification">保存</el-button></template></el-dialog>

    <el-dialog v-model="evaluationVisible" :title="editingEvaluationId ? '编辑绩效评价' : '新增绩效评价'" width="600px"><el-form :model="evaluationForm" label-width="100px"><el-form-item label="评价期间" required><el-input v-model="evaluationForm.period" placeholder="例如 2026-Q4" /></el-form-item><el-form-item label="质量评分" required><el-input-number v-model="evaluationForm.qualityScore" :min="0" :max="100" :precision="2" /></el-form-item><el-form-item label="交付评分" required><el-input-number v-model="evaluationForm.deliveryScore" :min="0" :max="100" :precision="2" /></el-form-item><el-form-item label="服务评分" required><el-input-number v-model="evaluationForm.serviceScore" :min="0" :max="100" :precision="2" /></el-form-item><el-form-item label="评价说明"><el-input v-model="evaluationForm.comment" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="evaluationVisible=false">取消</el-button><el-button type="primary" @click="saveEvaluation">保存</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.grid{display:grid;grid-template-columns:1fr 1fr;gap:0 12px}.full{grid-column:1/-1}h3{font-size:16px;margin:0}.section-head{display:flex;justify-content:space-between;align-items:center;margin:24px 0 12px}.form-tip{font-size:12px;color:#8a94a6;line-height:1.5;margin-top:4px}.date-separator{margin:0 10px;color:#8a94a6}.current-file{display:block;margin-top:8px;color:#637083;font-size:12px}
</style>
