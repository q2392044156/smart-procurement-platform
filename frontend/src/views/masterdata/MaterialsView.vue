<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Dict } from '@/types'

/**
 * 物资主数据：物资档案（分页）与「分类 + 动态属性」两个页签。
 * 数据来源：/materials/page、/material-categories、/units、/brands；写操作按 REST 约定。
 * 权限：masterdata:material:edit / masterdata:category:edit，另有 masterdata:edit 兜底。
 * 关键交互：搜索分页、物资新增编辑（属性随分类变化）、分类树维护、启停与删除。
 */

const auth = useAuthStore()
const rows = ref<Dict[]>([])
const page = reactive({ current: 1, size: 10, total: 0 })
const categories = ref<Dict[]>([])
const units = ref<Dict[]>([])
const brands = ref<Dict[]>([])
const keyword = ref('')
const tab = ref('materials')
const materialDialog = ref(false)
const categoryDialog = ref(false)
const editingMaterialId = ref('')
const editingCategoryId = ref('')
const loadingMaterial = ref(false)
const form = reactive<Dict>({ code: '', name: '', categoryId: '', unitId: '1', brandId: null, specification: '', trackingMode: 'QUANTITY', minStock: 0, attributes: {} })
const categoryForm = reactive<Dict>({ parentId: null, code: '', name: '', description: '', sortOrder: 0, attributes: [] })
const selectedCategory = computed(() => categories.value.find(x => x.id === String(form.categoryId)))
const trackingName: Dict = { QUANTITY: '数量', BATCH: '批次', SERIAL: '序列号' }
const trackingType: Dict = { QUANTITY: 'info', BATCH: 'warning', SERIAL: 'success' }

// 分类是扁平数组，这里按 parentId 组装成树，交给 el-table 的 row-key 展开
const categoryTree = computed(() => {
  const nodes = new Map<string, Dict>()
  categories.value.forEach(item => nodes.set(item.id, { ...item, children: [] }))
  const roots: Dict[] = []
  nodes.forEach(node => {
    const parent = node.parentId && nodes.get(node.parentId)
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
})

// 四个接口并发：列表分页 + 分类/单位/品牌下拉，供弹窗与筛选项使用
async function load() {
  const [pageData, categoryRows, unitRows, brandRows] = await Promise.all([
    http.get('/materials/page', { params: { keyword: keyword.value, current: page.current, size: page.size } }),
    http.get('/material-categories'), http.get('/units'), http.get('/brands')
  ])
  rows.value = pageData.records
  page.total = Number(pageData.total)
  categories.value = categoryRows
  units.value = unitRows
  brands.value = brandRows
}

// 打开物资弹窗：编辑时回填并把 attributes 的 JSON 字符串解析成对象，新增时默认数量跟踪
async function openMaterial(row?: Dict) {
  editingMaterialId.value = row?.id || ''
  loadingMaterial.value = true
  Object.assign(form, row ? { code: row.code, name: row.name, categoryId: row.categoryId, unitId: row.unitId, brandId: row.brandId || null, specification: row.specification || '', trackingMode: row.trackingMode, minStock: Number(row.minStock), attributes: typeof row.attributes === 'string' ? JSON.parse(row.attributes || '{}') : { ...(row.attributes || {}) } } : { code: '', name: '', categoryId: '', unitId: units.value.find(x => x.enabled)?.id || '', brandId: null, specification: '', trackingMode: 'QUANTITY', minStock: 0, attributes: {} })
  await nextTick()
  loadingMaterial.value = false
  materialDialog.value = true
}

// 保存物资：编码/名称/分类/单位必填，成功后关弹窗并刷新列表与分类计数
async function saveMaterial() {
  if (!form.code.trim() || !form.name.trim() || !form.categoryId || !form.unitId) {
    ElMessage.warning('请完整填写物资编码、名称、分类和单位')
    return
  }
  if (editingMaterialId.value) await http.put(`/materials/${editingMaterialId.value}`, form)
  else await http.post('/materials', form)
  ElMessage.success(editingMaterialId.value ? '物资已更新' : '物资已创建')
  materialDialog.value = false
  await load()
}

// 打开分类弹窗：编辑时把属性 options 转成逗号分隔文本，方便表格里直接改
function openCategory(row?: Dict) {
  editingCategoryId.value = row?.id || ''
  Object.assign(categoryForm, row ? { parentId: row.parentId || null, code: row.code, name: row.name, description: row.description || '', sortOrder: row.sortOrder || 0, attributes: (row.attributes || []).map((item: Dict) => ({ ...item, optionsText: typeof item.options === 'string' ? JSON.parse(item.options || '[]').join('，') : (item.options || []).join('，') })) } : { parentId: null, code: '', name: '', description: '', sortOrder: 0, attributes: [] })
  categoryDialog.value = true
}

// 追加一条动态属性定义，排序号取当前条数 + 1
function addAttribute() {
  categoryForm.attributes.push({ code: '', name: '', valueType: 'TEXT', required: false, optionsText: '', sortOrder: categoryForm.attributes.length + 1 })
}

// 保存分类：ENUM 类型把逗号分隔文本转回数组，其余类型的 options 置空
async function saveCategory() {
  if (!categoryForm.code.trim() || !categoryForm.name.trim()) {
    ElMessage.warning('分类编码和名称不能为空')
    return
  }
  const payload = {
    ...categoryForm,
    attributes: categoryForm.attributes.map((item: Dict) => ({
      code: item.code, name: item.name, valueType: item.valueType, required: item.required, sortOrder: item.sortOrder,
      options: item.valueType === 'ENUM' ? String(item.optionsText || '').split(/[,，]/).map(x => x.trim()).filter(Boolean) : null
    }))
  }
  if (editingCategoryId.value) await http.put(`/material-categories/${editingCategoryId.value}`, payload)
  else await http.post('/material-categories', payload)
  ElMessage.success(editingCategoryId.value ? '物资分类已更新' : '物资分类已创建')
  categoryDialog.value = false
  await load()
}

// 启停物资：二次确认后 PATCH 状态；停用后不可再被新单据引用
async function toggleMaterial(row: Dict) {
  await ElMessageBox.confirm(`确认${row.enabled ? '停用' : '启用'}物资“${row.name}”吗？`, '物资状态', { type: 'warning' })
  await http.patch(`/materials/${row.id}/status`, { enabled: !row.enabled })
  ElMessage.success('物资状态已更新')
  await load()
}

// 删除物资：已有业务记录时后端拒绝，错误由 http 拦截器统一提示
async function removeMaterial(row: Dict) {
  await ElMessageBox.confirm(`确认删除物资“${row.name}”吗？已有业务记录的物资将被后端拒绝删除。`, '删除物资', { type: 'warning' })
  await http.delete(`/materials/${row.id}`)
  ElMessage.success('物资已删除')
  await load()
}

async function removeCategory(row: Dict) {
  await ElMessageBox.confirm(`确认删除分类“${row.name}”吗？`, '删除分类', { type: 'warning' })
  await http.delete(`/material-categories/${row.id}`)
  ElMessage.success('物资分类已删除')
  await load()
}

function attributeSummary(row: Dict) {
  return (row.attributes || []).map((item: Dict) => `${item.name}·${item.valueType}${item.required ? '·必填' : ''}`).join('；') || '—'
}

watch(() => form.categoryId, () => { if (!loadingMaterial.value) form.attributes = {} })
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h1>物资主数据</h1><p>统一管理IT设备、MRO备件与工厂设备</p></div>
      <div class="actions"><el-button v-if="auth.can('masterdata:category:edit') || auth.can('masterdata:edit')" @click="openCategory()">新增分类</el-button><el-button v-if="auth.can('masterdata:material:edit') || auth.can('masterdata:edit')" type="primary" @click="openMaterial()">新增物资</el-button></div>
    </div>
    <div class="panel panel-pad">
      <el-tabs v-model="tab">
        <el-tab-pane label="物资档案" name="materials">
          <div class="toolbar"><el-input v-model="keyword" placeholder="按编码或名称搜索" clearable style="width:280px" @keyup.enter="load" /><el-button @click="load">查询</el-button></div>
          <el-table :data="rows" stripe>
            <el-table-column prop="code" label="物资编码" width="150" />
            <el-table-column prop="name" label="物资名称" min-width="180" />
            <el-table-column prop="category" label="分类" />
            <el-table-column prop="brand" label="品牌" />
            <el-table-column prop="specification" label="规格" min-width="200" />
            <el-table-column label="跟踪方式" width="110"><template #default="s"><el-tag :type="trackingType[s.row.trackingMode]">{{ trackingName[s.row.trackingMode] }}</el-tag></template></el-table-column>
            <el-table-column prop="minStock" label="安全库存" width="100" />
            <el-table-column label="状态" width="80"><template #default="s"><el-tag :type="s.row.enabled ? 'success' : 'danger'">{{ s.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column v-if="auth.can('masterdata:material:edit') || auth.can('masterdata:edit')" label="操作" width="190" fixed="right"><template #default="s"><el-button link type="primary" @click="openMaterial(s.row)">编辑</el-button><el-button link :type="s.row.enabled ? 'warning' : 'success'" @click="toggleMaterial(s.row)">{{ s.row.enabled ? '停用' : '启用' }}</el-button><el-button link type="danger" @click="removeMaterial(s.row)">删除</el-button></template></el-table-column>
          </el-table>
          <div class="pager"><el-pagination v-model:current-page="page.current" :page-size="page.size" :total="page.total" layout="total, prev, pager, next" background @current-change="load" /></div>
        </el-tab-pane>
        <el-tab-pane label="分类与动态属性" name="categories">
          <el-table :data="categoryTree" row-key="id" default-expand-all>
            <el-table-column prop="code" label="分类编码" width="150" />
            <el-table-column prop="name" label="分类名称" width="180" />
            <el-table-column prop="description" label="说明" min-width="180" />
            <el-table-column label="动态属性" min-width="320"><template #default="s">{{ attributeSummary(s.row) }}</template></el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="80" />
            <el-table-column prop="materialCount" label="物资数" width="80" />
            <el-table-column v-if="auth.can('masterdata:category:edit') || auth.can('masterdata:edit')" label="操作" width="130"><template #default="s"><el-button link type="primary" @click="openCategory(s.row)">编辑</el-button><el-button link type="danger" @click="removeCategory(s.row)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="materialDialog" :title="editingMaterialId ? '编辑物资' : '新增物资'" width="700px">
      <el-form :model="form" label-width="110px">
        <div class="grid">
          <el-form-item label="物资编码" required><el-input v-model="form.code" :disabled="!!editingMaterialId" /></el-form-item>
          <el-form-item label="物资名称" required><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="分类" required><el-select v-model="form.categoryId" filterable style="width:100%"><el-option v-for="item in categories" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="计量单位" required><el-select v-model="form.unitId" style="width:100%"><el-option v-for="item in units.filter(x=>x.enabled)" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="品牌"><el-select v-model="form.brandId" clearable style="width:100%"><el-option v-for="item in brands.filter(x=>x.enabled)" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="库存跟踪"><el-select v-model="form.trackingMode" style="width:100%"><el-option v-for="(value,key) in trackingName" :key="key" :label="value" :value="key" /></el-select></el-form-item>
          <el-form-item label="规格型号"><el-input v-model="form.specification" /></el-form-item>
          <el-form-item label="安全库存"><el-input-number v-model="form.minStock" :min="0" style="width:100%" /></el-form-item>
          <el-form-item v-for="attribute in selectedCategory?.attributes || []" :key="attribute.code" :label="attribute.name" :required="!!attribute.required">
            <el-select v-if="attribute.valueType === 'ENUM'" v-model="form.attributes[attribute.code]" style="width:100%"><el-option v-for="value in JSON.parse(attribute.options || '[]')" :key="value" :label="value" :value="value" /></el-select>
            <el-switch v-else-if="attribute.valueType === 'BOOLEAN'" v-model="form.attributes[attribute.code]" />
            <el-input-number v-else-if="attribute.valueType === 'NUMBER'" v-model="form.attributes[attribute.code]" style="width:100%" />
            <el-date-picker v-else-if="attribute.valueType === 'DATE'" v-model="form.attributes[attribute.code]" value-format="YYYY-MM-DD" style="width:100%" />
            <el-input v-else v-model="form.attributes[attribute.code]" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="materialDialog=false">取消</el-button><el-button type="primary" @click="saveMaterial">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="categoryDialog" :title="editingCategoryId ? '编辑物资分类' : '新增物资分类'" width="860px">
      <el-form :model="categoryForm" label-width="90px">
        <div class="grid">
          <el-form-item label="上级分类"><el-select v-model="categoryForm.parentId" clearable style="width:100%"><el-option v-for="item in categories.filter(x=>x.id !== editingCategoryId)" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="categoryForm.sortOrder" :min="0" style="width:100%" /></el-form-item>
          <el-form-item label="分类编码" required><el-input v-model="categoryForm.code" /></el-form-item>
          <el-form-item label="分类名称" required><el-input v-model="categoryForm.name" /></el-form-item>
          <el-form-item label="分类说明" class="full"><el-input v-model="categoryForm.description" /></el-form-item>
        </div>
        <div class="attribute-head"><b>动态属性定义</b><el-button text type="primary" @click="addAttribute">+ 添加属性</el-button></div>
        <el-table :data="categoryForm.attributes" border empty-text="该分类不定义动态属性">
          <el-table-column label="属性编码" width="140"><template #default="s"><el-input v-model="s.row.code" /></template></el-table-column>
          <el-table-column label="属性名称" width="150"><template #default="s"><el-input v-model="s.row.name" /></template></el-table-column>
          <el-table-column label="类型" width="130"><template #default="s"><el-select v-model="s.row.valueType"><el-option v-for="value in ['TEXT','NUMBER','BOOLEAN','DATE','ENUM']" :key="value" :label="value" :value="value" /></el-select></template></el-table-column>
          <el-table-column label="枚举选项" min-width="180"><template #default="s"><el-input v-if="s.row.valueType === 'ENUM'" v-model="s.row.optionsText" placeholder="用逗号分隔" /><span v-else>—</span></template></el-table-column>
          <el-table-column label="必填" width="70"><template #default="s"><el-switch v-model="s.row.required" /></template></el-table-column>
          <el-table-column width="60"><template #default="s"><el-button link type="danger" @click="categoryForm.attributes.splice(s.$index,1)">删</el-button></template></el-table-column>
        </el-table>
      </el-form>
      <template #footer><el-button @click="categoryDialog=false">取消</el-button><el-button type="primary" @click="saveCategory">保存分类</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.actions{display:flex;gap:10px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:0 14px}.full{grid-column:1/-1}.attribute-head{display:flex;justify-content:space-between;align-items:center;margin:10px 0}.pager{display:flex;justify-content:flex-end;margin-top:14px}
</style>
