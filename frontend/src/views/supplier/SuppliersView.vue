<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
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
const form = reactive<Dict>({ code: '', name: '', unifiedCreditCode: '', level: 'B', status: 'PENDING', contactName: '', contactPhone: '', email: '', address: '' })
const statusName: Dict = { PENDING: '待审核', QUALIFIED: '合格', SUSPENDED: '暂停', BLACKLISTED: '黑名单' }
const statusType: Dict = { PENDING: 'warning', QUALIFIED: 'success', SUSPENDED: 'info', BLACKLISTED: 'danger' }
const editorTitle = computed(() => editingId.value ? '编辑供应商' : '供应商准入')

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

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h1>供应商管理</h1><p>供应商准入、分级、资质与绩效档案</p></div>
      <el-button v-if="auth.can('supplier:edit')" type="primary" @click="openCreate">新增供应商</el-button>
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
        <el-table-column label="操作" width="130" fixed="right"><template #default="s"><el-button link @click="openDetail(s.row)">档案</el-button><el-button v-if="auth.can('supplier:edit')" link type="primary" @click="openEdit(s.row)">编辑</el-button></template></el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="editorVisible" :title="editorTitle" width="660px">
      <el-form :model="form" label-width="110px">
        <div class="grid">
          <el-form-item label="供应商编码" required><el-input v-model="form.code" :disabled="!!editingId" /></el-form-item>
          <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="统一信用代码"><el-input v-model="form.unifiedCreditCode" /></el-form-item>
          <el-form-item label="等级"><el-select v-model="form.level" style="width:100%"><el-option v-for="x in ['A','B','C','D']" :key="x" :label="x" :value="x" /></el-select></el-form-item>
          <el-form-item label="状态"><el-select v-model="form.status" style="width:100%"><el-option v-for="(value,key) in statusName" :key="key" :label="value" :value="key" /></el-select></el-form-item>
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
        <h3>资质证照</h3>
        <el-table :data="detail.qualifications" border empty-text="暂无资质记录">
          <el-table-column prop="name" label="资质名称" />
          <el-table-column prop="validFrom" label="生效日期" />
          <el-table-column prop="validUntil" label="有效期至" />
          <el-table-column prop="status" label="状态" />
        </el-table>
        <h3>绩效评价</h3>
        <el-table :data="detail.evaluations" border empty-text="暂无评价记录">
          <el-table-column prop="period" label="期间" />
          <el-table-column prop="qualityScore" label="质量" />
          <el-table-column prop="deliveryScore" label="交付" />
          <el-table-column prop="serviceScore" label="服务" />
          <el-table-column prop="totalScore" label="总分" />
          <el-table-column prop="comment" label="评价" min-width="160" />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.grid{display:grid;grid-template-columns:1fr 1fr;gap:0 12px}.full{grid-column:1/-1}h3{font-size:16px;margin:24px 0 12px}
</style>
