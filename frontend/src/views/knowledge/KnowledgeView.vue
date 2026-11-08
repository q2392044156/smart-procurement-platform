<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Dict } from '@/types'

const auth = useAuthStore()
const canManage = computed(() => auth.can('kb:manage'))

const rows = ref<Dict[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const filters = reactive({ keyword: '', category: '', status: '' })

const categories = [
  { code: 'POLICY', name: '制度规范' }, { code: 'PROCEDURE', name: '流程指引' },
  { code: 'CONTRACT', name: '合同协议' }, { code: 'SUPPLIER', name: '供应商资料' },
  { code: 'MANUAL', name: '产品手册' }, { code: 'TEMPLATE', name: '模板表单' },
  { code: 'OTHER', name: '其他资料' }
]
const categoryName: Dict = Object.fromEntries(categories.map(c => [c.code, c.name]))
const statusName: Dict = { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }
const statusType: Dict = { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }
const visibilityName: Dict = {
  ALL: '全员可见', DEPARTMENT: '本部门', DEPARTMENT_AND_CHILDREN: '本部门及下级',
  SELF: '仅创建人', CUSTOM: '指定用户/角色'
}

const mimeByExtension: Dict = {
  pdf: 'application/pdf',
  doc: 'application/msword',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xls: 'application/vnd.ms-excel',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  ppt: 'application/vnd.ms-powerpoint',
  pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  txt: 'text/plain',
  md: 'text/markdown'
}

const departments = ref<Dict[]>([])
const users = ref<Dict[]>([])
const roles = ref<Dict[]>([])

const editorVisible = ref(false)
const editingId = ref('')
const saving = ref(false)
const selectedFile = ref<File>()
const form = reactive<Dict>({
  code: '', title: '', category: 'POLICY', summary: '', tags: '', ownerDepartmentId: '',
  visibility: 'ALL', effectiveFrom: '', effectiveTo: '', acl: []
})

const detailVisible = ref(false)
const detail = ref<Dict>()
const versionFile = ref<File>()
const versionNote = ref('')
const uploadingVersion = ref(false)

async function load() {
  loading.value = true
  try {
    const data: Dict = await http.get('/kb/documents', {
      params: { keyword: filters.keyword || undefined, category: filters.category || undefined, status: filters.status || undefined, current: current.value, size: pageSize.value }
    })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function search() { current.value = 1; load() }
function resetFilters() { filters.keyword = ''; filters.category = ''; filters.status = ''; search() }

function resetForm() {
  Object.assign(form, {
    code: '', title: '', category: 'POLICY', summary: '', tags: '',
    ownerDepartmentId: auth.user?.departmentId || '', visibility: 'ALL',
    effectiveFrom: '', effectiveTo: '', acl: []
  })
  selectedFile.value = undefined
  editingId.value = ''
}

function openCreate() { resetForm(); editorVisible.value = true }

async function openEdit(row: Dict) {
  const data: Dict = await http.get(`/kb/documents/${row.id}`)
  editingId.value = row.id
  Object.assign(form, {
    code: data.code, title: data.title, category: data.category, summary: data.summary || '',
    tags: data.tags || '', ownerDepartmentId: data.ownerDepartmentId, visibility: data.visibility,
    effectiveFrom: data.effectiveFrom || '', effectiveTo: data.effectiveTo || '',
    acl: (data.acl || []).map((x: Dict) => ({ subjectType: x.subjectType, subjectKey: x.subjectKey }))
  })
  selectedFile.value = undefined
  editorVisible.value = true
}

function onFileChange(event: Event) { selectedFile.value = (event.target as HTMLInputElement).files?.[0] || undefined }
function onVersionFileChange(event: Event) { versionFile.value = (event.target as HTMLInputElement).files?.[0] || undefined }

function mimeOf(file: File) {
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  return mimeByExtension[ext] || ''
}

async function uploadToStorage(file: File) {
  const contentType = mimeOf(file)
  if (!contentType) throw new Error('仅支持 PDF、Word、Excel、PPT、TXT、Markdown 文件')
  const signed: Dict = await http.post('/kb/uploads/presign', { fileName: file.name, contentType, size: file.size })
  const response = await fetch(signed.uploadUrl, { method: 'PUT', headers: { 'Content-Type': contentType }, body: file })
  if (!response.ok) throw new Error(`文件上传失败（HTTP ${response.status}）`)
  return String(signed.fileId)
}

function addAcl() { form.acl.push({ subjectType: 'ROLE', subjectKey: '' }) }
function removeAcl(index: string | number) { form.acl.splice(Number(index), 1) }

async function save() {
  if (!form.title?.trim()) return ElMessage.warning('请填写文档标题')
  if (form.visibility === 'CUSTOM' && !form.acl.length) return ElMessage.warning('指定可见范围时至少添加一条授权')
  saving.value = true
  try {
    if (editingId.value) {
      await http.put(`/kb/documents/${editingId.value}`, form)
      ElMessage.success('文档信息已更新')
    } else {
      if (!selectedFile.value) { ElMessage.warning('请选择要上传的文档'); return }
      const fileId = await uploadToStorage(selectedFile.value)
      await http.post('/kb/documents', { ...form, fileId })
      ElMessage.success('文档已上传为草稿')
    }
    editorVisible.value = false
    await load()
  } catch (error: any) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function openDetail(row: Dict) {
  detail.value = await http.get(`/kb/documents/${row.id}`)
  versionFile.value = undefined
  versionNote.value = ''
  detailVisible.value = true
}

async function changeStatus(row: Dict, status: string) {
  const label = statusName[status]
  await ElMessageBox.confirm(`确认将“${row.title}”变更为「${label}」？`, '状态确认', { type: 'warning' })
  await http.patch(`/kb/documents/${row.id}/status`, { status })
  ElMessage.success(`文档已${label}`)
  detailVisible.value = false
  await load()
}

async function remove(row: Dict) {
  await ElMessageBox.confirm(`确认删除“${row.title}”？删除后不再出现在知识库中。`, '删除确认', { type: 'warning' })
  await http.delete(`/kb/documents/${row.id}`)
  ElMessage.success('文档已删除')
  detailVisible.value = false
  await load()
}

async function download(row: Dict) {
  const result: Dict = await http.get(`/kb/documents/${row.id}/download`)
  window.open(result.downloadUrl, '_blank', 'noopener')
}

async function addVersion() {
  if (!detail.value || !versionFile.value) return ElMessage.warning('请选择新版本文件')
  uploadingVersion.value = true
  try {
    const fileId = await uploadToStorage(versionFile.value)
    const result: Dict = await http.post(`/kb/documents/${detail.value.id}/versions`, { fileId, changeNote: versionNote.value })
    ElMessage.success(`已上传第 ${result.version} 版`)
    versionFile.value = undefined
    versionNote.value = ''
    detail.value = await http.get(`/kb/documents/${detail.value.id}`)
    await load()
  } catch (error: any) {
    ElMessage.error(error?.message || '版本上传失败')
  } finally {
    uploadingVersion.value = false
  }
}

function formatSize(value: unknown) {
  const size = Number(value || 0)
  if (!size) return '—'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

function formatTime(value: unknown) {
  if (!value) return '—'
  const date = new Date(String(value))
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

onMounted(async () => {
  await load()
  try { departments.value = await http.get('/departments') } catch { departments.value = [] }
  if (auth.can('system:user:view')) {
    try { users.value = await http.get('/users') } catch { users.value = [] }
    try { roles.value = await http.get('/roles') } catch { roles.value = [] }
  }
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h1>知识库</h1><p>内部制度、流程、合同与供应商资料的统一归档与分发</p></div>
      <el-button v-if="canManage" type="primary" @click="openCreate">上传文档</el-button>
    </div>

    <div class="panel panel-pad filters">
      <el-input v-model="filters.keyword" placeholder="标题 / 编码 / 标签 / 摘要" clearable style="width:240px" @keyup.enter="search" />
      <el-select v-model="filters.category" placeholder="资料分类" clearable style="width:150px">
        <el-option v-for="c in categories" :key="c.code" :label="c.name" :value="c.code" />
      </el-select>
      <el-select v-if="canManage" v-model="filters.status" placeholder="文档状态" clearable style="width:130px">
        <el-option v-for="(name, key) in statusName" :key="key" :label="name" :value="key" />
      </el-select>
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <div class="panel panel-pad">
      <el-table v-loading="loading" :data="rows" empty-text="暂无知识库文档">
        <el-table-column prop="code" label="文档编码" width="160" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="分类" width="100"><template #default="s">{{ categoryName[s.row.category] || s.row.category }}</template></el-table-column>
        <el-table-column label="可见范围" width="120"><template #default="s">{{ visibilityName[s.row.visibility] || s.row.visibility }}</template></el-table-column>
        <el-table-column label="版本" width="60"><template #default="s">v{{ s.row.currentVersion }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="s"><el-tag :type="statusType[s.row.status]">{{ statusName[s.row.status] }}</el-tag></template></el-table-column>
        <el-table-column label="更新时间" width="150"><template #default="s">{{ formatTime(s.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="s">
            <el-button link type="primary" @click="openDetail(s.row)">详情</el-button>
            <el-button link @click="download(s.row)">下载</el-button>
            <el-button v-if="canManage && s.row.status !== 'ARCHIVED'" link type="primary" @click="openEdit(s.row)">编辑</el-button>
            <el-button v-if="canManage && s.row.status === 'DRAFT'" link type="success" @click="changeStatus(s.row, 'PUBLISHED')">发布</el-button>
            <el-button v-if="canManage && s.row.status === 'PUBLISHED'" link type="warning" @click="changeStatus(s.row, 'ARCHIVED')">归档</el-button>
            <el-button v-if="canManage && s.row.status === 'ARCHIVED'" link type="success" @click="changeStatus(s.row, 'PUBLISHED')">重新发布</el-button>
            <el-button v-if="canManage && ['DRAFT','ARCHIVED'].includes(s.row.status)" link type="danger" @click="remove(s.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="current" :page-size="pageSize" :total="total" layout="total, prev, pager, next" background @current-change="load" />
      </div>
    </div>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑文档信息' : '上传知识库文档'" width="760px" destroy-on-close>
      <el-form :model="form" label-width="96px">
        <div class="form-grid">
          <el-form-item label="文档编码"><el-input v-model="form.code" :disabled="!!editingId" placeholder="留空自动生成" /></el-form-item>
          <el-form-item label="标题" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
          <el-form-item label="资料分类" required>
            <el-select v-model="form.category" style="width:100%">
              <el-option v-for="c in categories" :key="c.code" :label="c.name" :value="c.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="归属部门" required>
            <el-select v-model="form.ownerDepartmentId" style="width:100%">
              <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="可见范围" required>
            <el-select v-model="form.visibility" style="width:100%">
              <el-option v-for="(name, key) in visibilityName" :key="key" :label="name" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签"><el-input v-model="form.tags" placeholder="多个标签用逗号分隔" /></el-form-item>
          <el-form-item label="生效日期"><el-date-picker v-model="form.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="失效日期"><el-date-picker v-model="form.effectiveTo" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        </div>
        <el-form-item label="摘要"><el-input v-model="form.summary" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item v-if="!editingId" label="文档文件" required>
          <input type="file" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md" @change="onFileChange" />
        </el-form-item>
        <el-form-item v-else label="文档文件">
          <span class="hint">如需替换文件，请在详情页上传新版本，历史版本会保留。</span>
        </el-form-item>
        <el-form-item v-if="form.visibility === 'CUSTOM'" label="授权范围" required>
          <div class="acl-editor">
            <div v-for="(entry, index) in form.acl" :key="index" class="acl-row">
              <el-select v-model="entry.subjectType" style="width:110px">
                <el-option label="角色" value="ROLE" /><el-option label="用户" value="USER" />
              </el-select>
              <el-select v-if="entry.subjectType === 'ROLE' && roles.length" v-model="entry.subjectKey" filterable style="width:180px">
                <el-option v-for="r in roles" :key="r.code" :label="`${r.name}（${r.code}）`" :value="r.code" />
              </el-select>
              <el-select v-else-if="entry.subjectType === 'USER' && users.length" v-model="entry.subjectKey" filterable style="width:180px">
                <el-option v-for="u in users" :key="u.id" :label="`${u.displayName}（${u.username}）`" :value="u.id" />
              </el-select>
              <el-input v-else v-model="entry.subjectKey" :placeholder="entry.subjectType === 'ROLE' ? '角色编码，如 PURCHASER' : '用户ID'" style="width:180px" />
              <el-button link type="danger" @click="removeAcl(index)">移除</el-button>
            </div>
            <el-button text type="primary" @click="addAcl">+ 添加授权</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ editingId ? '保存' : '上传' }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="文档详情" size="760px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="文档编码">{{ detail.code }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusType[detail.status]">{{ statusName[detail.status] }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ categoryName[detail.category] || detail.category }}</el-descriptions-item>
          <el-descriptions-item label="归属部门">{{ detail.ownerDepartment }}</el-descriptions-item>
          <el-descriptions-item label="可见范围">{{ visibilityName[detail.visibility] || detail.visibility }}</el-descriptions-item>
          <el-descriptions-item label="当前版本">v{{ detail.currentVersion }}</el-descriptions-item>
          <el-descriptions-item label="生效日期">{{ detail.effectiveFrom || '—' }}</el-descriptions-item>
          <el-descriptions-item label="失效日期">{{ detail.effectiveTo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">{{ detail.tags || '—' }}</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ detail.summary || '—' }}</el-descriptions-item>
          <el-descriptions-item label="文件" :span="2">{{ detail.fileName }}（{{ formatSize(detail.sizeBytes) }}，{{ detail.contentType }}）</el-descriptions-item>
        </el-descriptions>

        <div class="drawer-actions">
          <el-button type="primary" plain @click="download(detail)">下载当前版本</el-button>
          <el-button v-if="canManage && detail.status === 'DRAFT'" type="success" plain @click="changeStatus(detail, 'PUBLISHED')">发布</el-button>
          <el-button v-if="canManage && detail.status === 'PUBLISHED'" type="warning" plain @click="changeStatus(detail, 'ARCHIVED')">归档</el-button>
          <el-button v-if="canManage && detail.status === 'ARCHIVED'" type="success" plain @click="changeStatus(detail, 'PUBLISHED')">重新发布</el-button>
        </div>

        <template v-if="canManage && detail.status !== 'ARCHIVED'">
          <h3 class="section-title">上传新版本</h3>
          <div class="version-form">
            <input type="file" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md" @change="onVersionFileChange" />
            <el-input v-model="versionNote" placeholder="版本说明（可选）" style="width:220px" />
            <el-button type="primary" :loading="uploadingVersion" @click="addVersion">上传版本</el-button>
          </div>
        </template>

        <h3 class="section-title">版本历史</h3>
        <el-table :data="detail.versions" border size="small">
          <el-table-column label="版本" width="80"><template #default="s">v{{ s.row.version }}</template></el-table-column>
          <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
          <el-table-column prop="changeNote" label="版本说明" min-width="140" />
          <el-table-column prop="createdByName" label="上传人" width="110" />
          <el-table-column label="时间" width="150"><template #default="s">{{ formatTime(s.row.createdAt) }}</template></el-table-column>
        </el-table>

        <template v-if="detail.acl?.length">
          <h3 class="section-title">授权范围</h3>
          <el-table :data="detail.acl" border size="small">
            <el-table-column label="类型" width="90"><template #default="s">{{ s.row.subjectType === 'ROLE' ? '角色' : '用户' }}</template></el-table-column>
            <el-table-column prop="subjectKey" label="对象" />
          </el-table>
        </template>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.filters{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:16px}
.pager{display:flex;justify-content:flex-end;margin-top:16px}
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}
.hint{color:#8a95a8;font-size:12px}
.acl-editor{display:flex;flex-direction:column;gap:8px;width:100%}
.acl-row{display:flex;gap:8px;align-items:center}
.section-title{font-size:15px;margin:22px 0 10px}
.drawer-actions{display:flex;gap:8px;margin-top:16px}
.version-form{display:flex;gap:10px;align-items:center;flex-wrap:wrap}
</style>
