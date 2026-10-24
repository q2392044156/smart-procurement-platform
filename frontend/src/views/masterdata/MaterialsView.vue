<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Dict } from '@/types'

const auth = useAuthStore()
const rows = ref<Dict[]>([])
const categories = ref<Dict[]>([])
const units = ref<Dict[]>([])
const brands = ref<Dict[]>([])
const keyword = ref('')
const tab = ref('materials')
const materialDialog = ref(false)
const categoryDialog = ref(false)
const form = reactive<Dict>({ code: '', name: '', categoryId: '', unitId: '1', brandId: null, specification: '', trackingMode: 'QUANTITY', minStock: 0, attributes: {} })
const categoryForm = reactive<Dict>({ parentId: null, code: '', name: '', description: '', sortOrder: 0, attributes: [] })
const selectedCategory = computed(() => categories.value.find(x => x.id === String(form.categoryId)))
const trackingName: Dict = { QUANTITY: '数量', BATCH: '批次', SERIAL: '序列号' }
const trackingType: Dict = { QUANTITY: 'info', BATCH: 'warning', SERIAL: 'success' }

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

async function load() {
  ;[rows.value, categories.value, units.value, brands.value] = await Promise.all([
    http.get('/materials', { params: { keyword: keyword.value } }),
    http.get('/material-categories'), http.get('/units'), http.get('/brands')
  ])
}

function openMaterial() {
  Object.assign(form, { code: '', name: '', categoryId: '', unitId: units.value[0]?.id || '', brandId: null, specification: '', trackingMode: 'QUANTITY', minStock: 0, attributes: {} })
  materialDialog.value = true
}

async function saveMaterial() {
  if (!form.code.trim() || !form.name.trim() || !form.categoryId || !form.unitId) {
    ElMessage.warning('请完整填写物资编码、名称、分类和单位')
    return
  }
  await http.post('/materials', form)
  ElMessage.success('物资已创建')
  materialDialog.value = false
  await load()
}

function openCategory() {
  Object.assign(categoryForm, { parentId: null, code: '', name: '', description: '', sortOrder: 0, attributes: [] })
  categoryDialog.value = true
}

function addAttribute() {
  categoryForm.attributes.push({ code: '', name: '', valueType: 'TEXT', required: false, optionsText: '', sortOrder: categoryForm.attributes.length + 1 })
}

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
  await http.post('/material-categories', payload)
  ElMessage.success('物资分类已创建')
  categoryDialog.value = false
  await load()
}

function attributeSummary(row: Dict) {
  return (row.attributes || []).map((item: Dict) => `${item.name}·${item.valueType}${item.required ? '·必填' : ''}`).join('；') || '—'
}

watch(() => form.categoryId, () => { form.attributes = {} })
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h1>物资主数据</h1><p>统一管理IT设备、MRO备件与工厂设备</p></div>
      <div v-if="auth.can('masterdata:edit')" class="actions"><el-button @click="openCategory">新增分类</el-button><el-button type="primary" @click="openMaterial">新增物资</el-button></div>
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
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="分类与动态属性" name="categories">
          <el-table :data="categoryTree" row-key="id" default-expand-all>
            <el-table-column prop="code" label="分类编码" width="150" />
            <el-table-column prop="name" label="分类名称" width="180" />
            <el-table-column prop="description" label="说明" min-width="180" />
            <el-table-column label="动态属性" min-width="320"><template #default="s">{{ attributeSummary(s.row) }}</template></el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="80" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="materialDialog" title="新增物资" width="700px">
      <el-form :model="form" label-width="110px">
        <div class="grid">
          <el-form-item label="物资编码" required><el-input v-model="form.code" /></el-form-item>
          <el-form-item label="物资名称" required><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="分类" required><el-select v-model="form.categoryId" filterable style="width:100%"><el-option v-for="item in categories" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="计量单位" required><el-select v-model="form.unitId" style="width:100%"><el-option v-for="item in units" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="品牌"><el-select v-model="form.brandId" clearable style="width:100%"><el-option v-for="item in brands" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
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

    <el-dialog v-model="categoryDialog" title="新增物资分类" width="860px">
      <el-form :model="categoryForm" label-width="90px">
        <div class="grid">
          <el-form-item label="上级分类"><el-select v-model="categoryForm.parentId" clearable style="width:100%"><el-option v-for="item in categories" :key="item.id" :label="`${item.code} ${item.name}`" :value="item.id" /></el-select></el-form-item>
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
.actions{display:flex;gap:10px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:0 14px}.full{grid-column:1/-1}.attribute-head{display:flex;justify-content:space-between;align-items:center;margin:10px 0}
</style>
