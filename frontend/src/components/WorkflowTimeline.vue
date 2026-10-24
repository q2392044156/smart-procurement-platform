<script setup lang="ts">
import { ref, watch } from 'vue'
import http from '@/api/http'
import type { Dict } from '@/types'

const props = defineProps<{ businessType: 'REQUISITION' | 'PURCHASE_ORDER'; businessId?: string }>()
const rows = ref<Dict[]>([])
const loading = ref(false)

function format(value: unknown) {
  return value ? new Date(String(value)).toLocaleString('zh-CN', { hour12: false }) : '处理中'
}

async function load() {
  if (!props.businessId) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    rows.value = await http.get(`/workflow/timeline/${props.businessType}/${props.businessId}`)
  } finally {
    loading.value = false
  }
}

watch(() => [props.businessType, props.businessId], load, { immediate: true })
</script>

<template>
  <section v-loading="loading" class="workflow-timeline">
    <h3>审批时间线</h3>
    <el-timeline v-if="rows.length">
      <el-timeline-item
        v-for="(row, index) in rows"
        :key="`${row.processInstanceId}-${index}`"
        :type="row.endTime ? 'success' : 'primary'"
        :hollow="!row.endTime"
        :timestamp="`${format(row.startTime)} → ${format(row.endTime)}`"
      >
        <b>{{ row.name }}</b>
        <span>{{ row.endTime ? '已处理' : '待处理' }}</span>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else :image-size="64" description="尚未产生审批记录" />
  </section>
</template>

<style scoped>
.workflow-timeline{margin-top:24px}.workflow-timeline h3{margin:0 0 18px;font-size:16px}.workflow-timeline span{margin-left:10px;color:var(--muted);font-size:13px}
</style>
