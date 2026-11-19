<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fetchBlob } from '@/api/http'
import type { Attachment } from '@/utils/attachments'

const props = defineProps<{ attachment: Attachment; ready?: boolean }>()
const state = ref<'loading' | 'ready' | 'error'>('loading')
const objectUrl = ref('')
const size = ref(0)
const MAX_ATTEMPTS = 5
let attempts = 0
let timer: ReturnType<typeof setTimeout> | undefined

const downloadPath = computed(() => `/ai/files/${encodeURIComponent(props.attachment.name)}`)
const sizeText = computed(() => (size.value > 0 ? `${(size.value / 1024).toFixed(1)} KB` : ''))
const isReady = computed(() => props.ready !== false)

/**
 * 取回二进制。
 *
 * 两处竞态：①模型先把「下载链接」文字流式吐出来，工具往往是后一步才把文件写进
 * `data/download/`；②回复结束前文件还可能不存在。所以既要等 `ready`，也要重试。
 */
async function load() {
  if (state.value === 'ready' || !isReady.value) return
  try {
    const blob = await fetchBlob(downloadPath.value)
    size.value = blob.size
    objectUrl.value = URL.createObjectURL(blob)
    state.value = 'ready'
  } catch {
    attempts += 1
    if (attempts < MAX_ATTEMPTS) {
      state.value = 'loading'
      timer = setTimeout(load, 700 * attempts)
    } else {
      state.value = 'error'
    }
  }
}

onMounted(load)

watch(isReady, ready => {
  if (!ready) return
  attempts = 0
  void load()
})

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
})

function download() {
  if (!objectUrl.value) return
  const link = document.createElement('a')
  link.href = objectUrl.value
  link.download = props.attachment.name
  document.body.appendChild(link)
  link.click()
  link.remove()
}
</script>

<template>
  <figure v-if="attachment.kind === 'image'" class="chat-attachment image">
    <img v-if="state === 'ready'" :src="objectUrl" :alt="attachment.label" />
    <div v-else class="placeholder">{{ state === 'loading' ? '图表加载中…' : '图表暂时无法加载' }}</div>
    <figcaption>
      <span>{{ attachment.label }}<small v-if="sizeText">{{ sizeText }}</small></span>
      <el-button size="small" type="primary" plain :disabled="state !== 'ready'" @click="download">下载</el-button>
    </figcaption>
  </figure>
  <div v-else class="chat-attachment file">
    <div class="meta">
      <span class="name">{{ attachment.name }}</span>
      <small>{{ state === 'loading' ? '文件准备中…' : state === 'error' ? '文件暂时无法下载' : sizeText }}</small>
    </div>
    <el-button size="small" type="primary" plain :disabled="state !== 'ready'" @click="download">下载</el-button>
  </div>
</template>

<style scoped>
.chat-attachment{margin:12px 0;border:1px solid #dfe6f1;border-radius:11px;background:#fbfdff;overflow:hidden}
.chat-attachment.image img{display:block;width:100%;max-width:640px;height:auto;background:#fff}
.chat-attachment.image .placeholder{display:grid;place-items:center;height:160px;color:#9aa4b5;font-size:13px;background:#f6f9ff}
.chat-attachment figcaption{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:9px 13px;border-top:1px solid #e8eef8;font-size:12px;color:#5f6c81}
.chat-attachment figcaption span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.chat-attachment figcaption small{margin-left:8px;color:#9aa4b5}
.chat-attachment.file{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:11px 14px;max-width:420px}
.chat-attachment.file .meta{display:flex;flex-direction:column;gap:3px;min-width:0}
.chat-attachment.file .name{font-size:13px;color:#33415a;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.chat-attachment.file small{color:#9aa4b5;font-size:11px}
</style>
