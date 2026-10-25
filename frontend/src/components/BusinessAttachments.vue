<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import type { Dict } from '@/types'

const props = defineProps<{ businessType: 'REQUISITION' | 'PURCHASE_ORDER', businessId: string, editable?: boolean }>()
const rows = ref<Dict[]>([])
const dialog = ref(false)
const uploading = ref(false)
const selected = ref<File>()
const form = ref({ category: 'OTHER', note: '' })
const categories: Dict = { REQUEST: '需求说明', QUOTATION: '报价文件', CONTRACT: '合同文件', TECHNICAL: '技术资料', OTHER: '其他' }

async function load() {
  if (props.businessId) rows.value = await http.get(`/files/attachments/${props.businessType}/${props.businessId}`)
}

function choose(event: Event) {
  selected.value = (event.target as HTMLInputElement).files?.[0]
}

async function upload() {
  if (!selected.value) return ElMessage.warning('请选择文件')
  uploading.value = true
  try {
    const file = selected.value
    const contentType = ['application/pdf', 'image/jpeg', 'image/png'].includes(file.type) ? file.type : 'application/octet-stream'
    const signed: Dict = await http.post('/files/presign', { fileName: file.name, contentType, size: file.size })
    const response = await fetch(signed.uploadUrl, { method: 'PUT', headers: { 'Content-Type': contentType }, body: file })
    if (!response.ok) throw new Error(`文件上传失败（HTTP ${response.status}）`)
    await http.post(`/files/attachments/${props.businessType}/${props.businessId}`, { fileId: signed.fileId, ...form.value })
    ElMessage.success('附件上传成功')
    dialog.value = false
    selected.value = undefined
    form.value = { category: 'OTHER', note: '' }
    await load()
  } catch (error: any) {
    ElMessage.error(error?.message || '附件上传失败')
  } finally {
    uploading.value = false
  }
}

async function download(row: Dict) {
  const result: Dict = await http.get(`/files/${row.fileId}`)
  window.open(result.downloadUrl, '_blank', 'noopener')
}

async function remove(row: Dict) {
  await ElMessageBox.confirm(`确认移除附件“${row.fileName}”？`, '移除附件', { type: 'warning' })
  await http.delete(`/files/attachments/${row.id}`)
  try { await http.delete(`/files/${row.fileId}`) } catch { /* 关联已移除，文件清理由文件管理策略兜底 */ }
  ElMessage.success('附件已移除')
  await load()
}

watch(() => [props.businessType, props.businessId], load, { immediate: true })
</script>

<template>
  <section class="attachments">
    <div class="section-head">
      <h3>业务附件 <el-tag size="small" type="info">{{ rows.length }}</el-tag></h3>
      <el-button v-if="editable" type="primary" plain size="small" @click="dialog=true">上传附件</el-button>
    </div>
    <el-table :data="rows" border empty-text="暂无附件">
      <el-table-column prop="fileName" label="文件名" min-width="190" show-overflow-tooltip />
      <el-table-column label="类型" width="100"><template #default="s">{{ categories[s.row.category] || s.row.category }}</template></el-table-column>
      <el-table-column prop="note" label="说明" min-width="130" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="上传时间" width="170" />
      <el-table-column label="操作" width="120"><template #default="s"><el-button link type="primary" @click="download(s.row)">下载</el-button><el-button v-if="editable" link type="danger" @click="remove(s.row)">移除</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="dialog" title="上传业务附件" width="520px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="文件" required><input type="file" accept=".pdf,.jpg,.jpeg,.png,.bin" @change="choose" /></el-form-item>
        <el-form-item label="资料类型"><el-select v-model="form.category" style="width:100%"><el-option v-for="(name,key) in categories" :key="key" :label="name" :value="key" /></el-select></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.note" maxlength="300" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="uploading" @click="upload">上传并关联</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.attachments{margin-top:24px}.section-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.section-head h3{font-size:16px;margin:0;display:flex;align-items:center;gap:8px}
</style>
