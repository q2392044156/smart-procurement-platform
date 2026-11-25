<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { Dict } from '@/types'

/**
 * 部门预算页：按年度展示各部门的预算额度、已占用、可用额度与使用率。
 * 数据来源：GET /budgets?year=；调整走 PUT /budgets/{departmentId}?year=&amount=。
 * 权限：budget:view 可查看；budget:edit 才出现「调整」按钮并允许改额度。
 * 关键交互：切换年份查询、弹窗调整额度（保存后关闭并刷新，使用率超阈值告警配色）。
 */

const auth = useAuthStore()
const canEdit = computed(() => auth.can('budget:edit'))
const rows = ref<Dict[]>([])
const year = ref(new Date().getFullYear())
const dialog = ref(false)
const editing = reactive<Dict>({ departmentId: '', department: '', amount: 0 })

// 按当前年份取预算列表；金额由后端算好，前端只负责展示与标签着色
async function load() { rows.value = await http.get('/budgets', { params: { year: year.value } }) }

// 打开调整弹窗：回填部门与已分配额度（不是可用额度），金额转数值再进输入框
function openEdit(row: Dict) {
  Object.assign(editing, { departmentId: row.departmentId, department: row.department, amount: Number(row.allocated) })
  dialog.value = true
}

// 保存额度：先做非负校验并提示，成功后关弹窗并刷新，避免与后端计算值不一致
async function save() {
  if (!editing.amount || Number(editing.amount) < 0) return ElMessage.warning('请输入非负预算金额')
  await http.put(`/budgets/${editing.departmentId}?year=${year.value}&amount=${editing.amount}`)
  ElMessage.success('预算已更新')
  dialog.value = false
  await load()
}

// 预算金额固定两位小数并带千分位，避免「10000.0」这类展示口径不一致
function money(value: unknown) { return `¥ ${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
// 使用率配色：≥100% 危险、≥80% 预警、其余正常，与列表标签语义一致
function utilizationType(row: Dict) {
  const value = Number(row.utilization)
  return value >= 100 ? 'danger' : value >= 80 ? 'warning' : 'success'
}
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h1>部门预算</h1><p>年度预算额度与采购占用情况，提交申请与直采下单时校验</p></div>
      <div class="head-actions">
        <el-input-number v-model="year" :min="2000" :max="2100" :controls="false" style="width:100px" />
        <el-button @click="load">查询</el-button>
      </div>
    </div>
    <div class="panel panel-pad">
      <el-table :data="rows" empty-text="暂无预算数据">
        <el-table-column prop="department" label="部门" min-width="150" />
        <el-table-column prop="fiscalYear" label="年度" width="90" />
        <el-table-column label="预算额度" width="160"><template #default="s">{{ money(s.row.allocated) }}</template></el-table-column>
        <el-table-column label="已占用" width="160"><template #default="s">{{ money(s.row.committed) }}</template></el-table-column>
        <el-table-column label="可用额度" width="160"><template #default="s">{{ money(s.row.available) }}</template></el-table-column>
        <el-table-column label="使用率" width="120"><template #default="s"><el-tag :type="utilizationType(s.row)">{{ s.row.utilization }}%</el-tag></template></el-table-column>
        <el-table-column v-if="canEdit" label="操作" width="100"><template #default="s"><el-button link type="primary" @click="openEdit(s.row)">调整</el-button></template></el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="调整部门预算" width="440px">
      <el-form label-width="90px">
        <el-form-item label="部门">{{ editing.department }}</el-form-item>
        <el-form-item label="年度">{{ year }}</el-form-item>
        <el-form-item label="预算额度" required><el-input-number v-model="editing.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head-actions{display:flex;gap:10px;align-items:center}
</style>
