<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import ChatAttachment from '@/components/ChatAttachment.vue'
import { SseParser, type AgentEvent, type AgentInterrupt, type AgentMessage, type AgentTodo } from '@/types/agent'
import { splitAttachments, type Segment } from '@/utils/attachments'
import { renderMarkdown } from '@/utils/markdown'
import type { Dict } from '@/types'

const available = ref(false)
const input = ref('')
const loading = ref(false)
const threadId = ref<string>(crypto.randomUUID())
const phase = ref('idle')
const phaseLabel = ref('等待输入')
const todos = ref<AgentTodo[]>([])
const interrupt = ref<AgentInterrupt | null>(null)
const supplement = ref('')
const conversations = ref<Dict[]>([])
const showHistory = ref(false)
const messageBox = ref<HTMLElement | null>(null)
const controller = ref<AbortController | null>(null)

const WELCOME = '你好，我是 ProcureMind AI 采购助手。你可以让我查询物资、供应商、订单与库存；涉及写操作时，我会等待你的确认。'
const messages = ref<AgentMessage[]>([{ role: 'assistant', content: WELCOME }])
const phaseSteps = [['thinking', '思考'], ['planning', '规划'], ['executing', '执行'], ['reviewing', '审查'], ['done', '完成']]
const phaseIndex = computed(() => phaseSteps.findIndex(x => x[0] === phase.value))
const beijingTime = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })

function formatBeijingTime(value: unknown) {
  if (!value) return ''
  let raw = String(value)
  if (!/([zZ]|[+-]\d{2}:?\d{2})$/.test(raw)) raw += 'Z'
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return raw
  return beijingTime.format(date).replace(/\//g, '-')
}

onMounted(async () => {
  try {
    const status: Dict = await http.get('/ai/status')
    available.value = !!status.enabled
    if (available.value) await loadHistory()
  } catch {
    available.value = false
  }
})

async function loadHistory() {
  try {
    const data: Dict = await http.get('/ai/history')
    conversations.value = data?.conversations || []
  } catch {
    conversations.value = []
  }
}

async function selectConversation(conversation: Dict) {
  try {
    const data: Dict = await http.get(`/ai/history/${conversation.thread_id}`)
    threadId.value = conversation.thread_id
    messages.value = (data.messages || [])
      .filter((m: Dict) => m.role === 'user' || m.role === 'assistant')
      .map((m: Dict) => ({ role: m.role, content: m.content || '', toolCalls: m.toolCalls || m.tool_calls || [] }))
    todos.value = []
    interrupt.value = null
    phase.value = 'done'
    phaseLabel.value = '历史会话'
    scrollToBottom()
  } catch {
    ElMessage.warning('无法加载该历史会话')
  }
}

async function removeConversation(conversation: Dict) {
  try {
    await ElMessageBox.confirm(`删除会话「${conversation.title || '新会话'}」？删除后无法恢复。`, '删除会话', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  } catch {
    return
  }
  try {
    await http.delete(`/ai/history/${conversation.thread_id}`)
    ElMessage.success('会话已删除')
    if (conversation.thread_id === threadId.value) newChat()
    await loadHistory()
  } catch {
    /* http 拦截器已提示 */
  }
}

function newChat() {
  threadId.value = crypto.randomUUID()
  messages.value = [{ role: 'assistant', content: WELCOME }]
  todos.value = []
  interrupt.value = null
  phase.value = 'idle'
  phaseLabel.value = '等待输入'
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  await startStream('/api/v1/ai/chat/stream', { message: text, thread_id: threadId.value })
}

async function resume(payload: Record<string, unknown>) {
  interrupt.value = null
  supplement.value = ''
  await startStream(`/api/v1/ai/chat/${threadId.value}/resume`, { resume: payload })
}

function stop() {
  if (!controller.value) return
  phaseLabel.value = '正在停止…'
  controller.value.abort()
}

function scrollToBottom() {
  nextTick(() => {
    const box = messageBox.value
    if (box) box.scrollTop = box.scrollHeight
  })
}

async function startStream(url: string, body: Dict) {
  const reply: AgentMessage = { role: 'assistant', content: '', toolCalls: [] }
  messages.value.push(reply)
  loading.value = true
  phase.value = 'thinking'
  phaseLabel.value = '思考中'
  scrollToBottom()
  const abortController = new AbortController()
  controller.value = abortController
  const parser = new SseParser()
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('pm_access_token')}` },
      body: JSON.stringify(body),
      signal: abortController.signal,
    })
    if (!response.ok) {
      const error = await response.json().catch(() => ({}))
      throw new Error(error.message || 'AI助手不可用')
    }
    const reader = response.body?.getReader()
    const decoder = new TextDecoder()
    while (reader) {
      const { done, value } = await reader.read()
      if (done) break
      for (const event of parser.parse(decoder.decode(value, { stream: true }))) handleEvent(event, reply)
    }
  } catch (error: any) {
    if (error?.name === 'AbortError') {
      reply.stopped = true
      phase.value = 'done'
      phaseLabel.value = '已停止生成'
    } else {
      if (!reply.content) reply.content = 'AI助手暂时不可用。普通采购业务可继续使用。'
      ElMessage.warning(error?.message || 'AI助手暂时不可用')
    }
  } finally {
    if (controller.value === abortController) controller.value = null
    loading.value = false
    if (phase.value !== 'done' && !interrupt.value) {
      phase.value = 'done'
      phaseLabel.value = reply.stopped ? '已停止生成' : '已结束'
    }
    scrollToBottom()
    await loadHistory()
  }
}

function handleEvent(event: AgentEvent, reply: AgentMessage) {
  switch (event.type) {
    case 'token':
      reply.content += String(event.content || '')
      scrollToBottom()
      break
    case 'thinking':
      phase.value = event.status === 'start' ? 'thinking' : phase.value
      phaseLabel.value = event.status === 'start' ? '思考中' : phaseLabel.value
      break
    case 'phase':
      phase.value = String(event.phase || 'executing')
      phaseLabel.value = String(event.label || event.phase || '执行中')
      break
    case 'todo_update':
      if (Array.isArray(event.todos)) todos.value = event.todos as AgentTodo[]
      break
    case 'tool_start':
      reply.toolCalls?.push({ id: String(event.id || crypto.randomUUID()), name: String(event.name || 'tool'), args: '', status: 'running' })
      phase.value = 'executing'
      phaseLabel.value = '调用业务工具'
      break
    case 'tool_args': {
      const tool = [...(reply.toolCalls || [])].reverse().find(x => x.status === 'running')
      if (tool) tool.args += String(event.args || '')
      break
    }
    case 'tool_result': {
      const tool = [...(reply.toolCalls || [])].reverse().find(x => x.status === 'running' && (!event.name || x.name === event.name))
      if (tool) {
        tool.result = String(event.content || '')
        tool.status = 'done'
      }
      break
    }
    case 'tool_end': {
      const tool = reply.toolCalls?.find(x => x.id === event.id)
      if (tool) tool.status = 'done'
      break
    }
    case 'interrupt':
      interrupt.value = event as unknown as AgentInterrupt
      break
    case 'review_result':
      phase.value = 'reviewing'
      phaseLabel.value = `审查：${String(event.verdict || '完成')}`
      break
    case 'error':
      reply.content += `\n${String(event.message || 'Agent执行错误')}`
      break
    case 'done':
      threadId.value = String(event.thread_id || threadId.value)
      if (!event.interrupted) {
        phase.value = 'done'
        phaseLabel.value = '已完成'
      }
      break
  }
}

function submitSupplement() {
  if (supplement.value.trim()) resume({ supplement: supplement.value.trim() })
}

function json(value: unknown) {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function segments(message: AgentMessage): Segment[] {
  return splitAttachments(message.content)
}

function markdown(text: string) {
  return renderMarkdown(text)
}
</script>

<template>
  <div class="page assistant">
    <div class="page-head">
      <div>
        <h1>AI采购助手</h1>
        <p>Agent 可插拔，身份由 Spring 从 JWT 注入，工具调用和人工确认全程可审计</p>
      </div>
      <div class="head-actions">
        <el-tag :type="available ? 'success' : 'info'">{{ available ? 'Agent 已连接' : 'Agent 未启用' }}</el-tag>
        <el-button @click="showHistory = !showHistory">历史会话</el-button>
        <el-button type="primary" plain @click="newChat">新会话</el-button>
      </div>
    </div>
    <div class="workspace">
      <aside v-if="showHistory" class="history panel">
        <b>历史会话</b>
        <div v-for="c in conversations" :key="c.thread_id" :class="['history-item', { active: c.thread_id === threadId }]">
          <button class="history-open" :title="c.title || '新会话'" @click="selectConversation(c)">
            {{ c.title || '新会话' }}
            <small>{{ formatBeijingTime(c.updated_at || c.updatedAt) }}</small>
          </button>
          <button class="history-delete" title="删除会话" @click.stop="removeConversation(c)">✕</button>
        </div>
        <div v-if="!conversations.length" class="empty-note">暂无历史</div>
      </aside>
      <main class="chat panel">
        <div v-if="loading || phase !== 'idle'" class="phase">
          <div v-for="(step, i) in phaseSteps" :key="step[0]" :class="['phase-step', { active: i === phaseIndex, done: i < phaseIndex }]">
            <i>{{ i < phaseIndex ? '✓' : i + 1 }}</i><span>{{ step[1] }}</span>
          </div>
          <em>{{ phaseLabel }}</em>
        </div>
        <div ref="messageBox" class="messages">
          <div v-for="(m, i) in messages" :key="i" :class="['message', m.role]">
            <div class="who">{{ m.role === 'user' ? '我' : 'ProcureMind AI' }}</div>
            <template v-if="m.role === 'user'">
              <div class="bubble">{{ m.content }}</div>
            </template>
            <template v-else>
              <div class="bubble markdown">
                <template v-for="(segment, si) in segments(m)" :key="si">
                  <div v-if="segment.type === 'text'" v-html="markdown(segment.text)"></div>
                  <ChatAttachment v-else :attachment="segment.attachment" :ready="!(loading && i === messages.length - 1)" />
                </template>
                <span v-if="loading && i === messages.length - 1 && !m.stopped" class="cursor">▍</span>
              </div>
              <div v-if="m.stopped" class="stopped-note">已停止生成</div>
            </template>
            <details v-for="tool in m.toolCalls || []" :key="tool.id" class="tool">
              <summary>
                <span :class="['tool-state', tool.status]"></span>{{ tool.name }}
                <small>{{ tool.status === 'running' ? '执行中' : '已完成' }}</small>
              </summary>
              <label>参数</label>
              <pre>{{ tool.args || '—' }}</pre>
              <template v-if="tool.result">
                <label>结果</label>
                <pre>{{ tool.result }}</pre>
              </template>
            </details>
          </div>
          <section v-if="todos.length" class="todo-card">
            <b>执行计划</b>
            <div v-for="t in todos" :key="t.id">
              <span :class="t.status">{{ t.status === 'completed' || t.status === 'complete' ? '✓' : t.status === 'in_progress' ? '→' : '·' }}</span>{{ t.content }}
            </div>
          </section>
          <section v-if="interrupt?.interrupt_type === 'hitl_approval'" class="interrupt approval">
            <h3>需要人工确认</h3>
            <p>Agent 请求执行写操作：<b>{{ interrupt.tool_name || '业务操作' }}</b></p>
            <pre>{{ json(interrupt.order_data || interrupt.tool_args) }}</pre>
            <div>
              <el-button type="success" @click="resume({ decisions: [{ type: 'approve' }] })">批准执行</el-button>
              <el-button type="danger" plain @click="resume({ decisions: [{ type: 'reject' }] })">拒绝</el-button>
            </div>
          </section>
          <section v-if="interrupt?.interrupt_type === 'order_info_supplement'" class="interrupt supplement">
            <h3>需要补充信息</h3>
            <p>{{ interrupt.message }}</p>
            <el-tag v-for="f in interrupt.missing_fields || []" :key="f" type="warning">{{ f }}</el-tag>
            <pre v-if="interrupt.extracted_data">{{ json(interrupt.extracted_data) }}</pre>
            <el-input v-model="supplement" placeholder="输入缺失的物资、数量、价格等信息" @keyup.enter="submitSupplement">
              <template #append><el-button @click="submitSupplement">继续</el-button></template>
            </el-input>
          </section>
        </div>
        <div class="composer">
          <el-input
            v-model="input"
            type="textarea"
            :rows="3"
            :disabled="!available || loading || !!interrupt"
            :placeholder="available ? '例如：查询低库存物资并给出采购建议' : '请先在后端启用 Agent 集成'"
            @keydown.ctrl.enter="send"
          />
          <div>
            <small>Ctrl + Enter 发送 · 写操作会触发人工确认<span v-if="loading"> · 正在生成，可随时停止</span></small>
            <el-button v-if="loading" type="danger" plain @click="stop">停止生成</el-button>
            <el-button v-else type="primary" :disabled="!available || !!interrupt" @click="send">发送</el-button>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.assistant{height:calc(100vh - 64px);display:flex;flex-direction:column}
.head-actions{display:flex;gap:9px;align-items:center}
.workspace{display:flex;gap:14px;flex:1;min-height:0}
.history{width:230px;padding:16px;overflow:auto}
.history>b{display:block;margin-bottom:12px}
.history-item{display:flex;align-items:center;gap:2px;border-radius:8px}
.history-item:hover,.history-item.active{background:#edf3ff}
.history-item.active .history-open{color:#0b5cff}
.history-open{flex:1;min-width:0;border:0;background:transparent;text-align:left;padding:10px;border-radius:8px;color:#435069;cursor:pointer;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.history-open small{display:block;color:#9aa4b5;font-size:10px;margin-top:4px}
.history-delete{flex:0 0 auto;border:0;background:transparent;color:#b6bece;cursor:pointer;padding:6px 8px;font-size:12px;border-radius:6px;opacity:0}
.history-item:hover .history-delete,.history-item.active .history-delete{opacity:1}
.history-delete:hover{color:#e4473d;background:#fdeceb}
.chat{flex:1;min-width:0;display:flex;flex-direction:column;overflow:hidden}
.phase{padding:11px 24px;border-bottom:1px solid #e8edf5;display:flex;align-items:center;gap:16px;background:#fbfcff}
.phase-step{display:flex;align-items:center;gap:6px;color:#a0a8b6;font-size:12px}
.phase-step i{font-style:normal;width:20px;height:20px;border-radius:50%;display:grid;place-items:center;background:#e8edf4}
.phase-step.active{color:#0b5cff;font-weight:600}
.phase-step.active i{background:#0b5cff;color:#fff}
.phase-step.done i{background:#e4f8ef;color:#15935a}
.phase em{margin-left:auto;color:#748098;font-size:12px;font-style:normal}
.messages{flex:1;overflow:auto;padding:28px;display:flex;flex-direction:column;align-items:flex-start;justify-content:flex-start}
.message{max-width:82%;margin-bottom:24px;display:block;height:auto;flex:0 0 auto}
.message.user{margin-left:auto}
.who{font-size:12px;color:#8994a6;margin-bottom:7px}
.user .who{text-align:right}
.bubble{white-space:pre-wrap;line-height:1.7;padding:14px 17px;border-radius:13px;background:#f0f4fa;min-height:20px}
.user .bubble{background:#0b5cff;color:white}
.bubble.markdown{white-space:normal;max-width:100%;overflow-x:auto}
.bubble.markdown :deep(p){margin:0 0 10px}
.bubble.markdown :deep(p:last-child){margin-bottom:0}
.bubble.markdown :deep(h1),.bubble.markdown :deep(h2),.bubble.markdown :deep(h3),.bubble.markdown :deep(h4){margin:14px 0 8px;font-size:15px;line-height:1.5;color:#1f2b40}
.bubble.markdown :deep(h1:first-child),.bubble.markdown :deep(h2:first-child),.bubble.markdown :deep(h3:first-child){margin-top:0}
.bubble.markdown :deep(strong){color:#101827}
.bubble.markdown :deep(table){border-collapse:collapse;margin:10px 0;font-size:12.5px;width:100%}
.bubble.markdown :deep(th),.bubble.markdown :deep(td){border:1px solid #dbe3f0;padding:7px 10px;text-align:left;white-space:nowrap}
.bubble.markdown :deep(th){background:#eaf1ff;color:#2b3852;font-weight:600}
.bubble.markdown :deep(tbody tr:nth-child(even)){background:#f7faff}
.bubble.markdown :deep(code){background:#e8eefb;color:#1f3a70;border-radius:4px;padding:1px 5px;font-size:12px}
.bubble.markdown :deep(pre){background:#101827;color:#d7e1ef;border-radius:8px;padding:11px;overflow:auto;margin:10px 0}
.bubble.markdown :deep(pre code){background:transparent;color:inherit;padding:0}
.bubble.markdown :deep(ul),.bubble.markdown :deep(ol){margin:8px 0;padding-left:22px}
.bubble.markdown :deep(li){margin:4px 0}
.bubble.markdown :deep(blockquote){margin:10px 0;padding:6px 12px;border-left:3px solid #c7d6f2;background:#f7faff;color:#4a5a75}
.bubble.markdown :deep(hr){border:0;border-top:1px solid #e2e9f5;margin:14px 0}
.bubble.markdown :deep(a){color:#0b5cff;text-decoration:none}
.bubble.markdown :deep(a:hover){text-decoration:underline}
.stopped-note{margin-top:6px;font-size:12px;color:#b26a00}
.tool{margin-top:8px;border:1px solid #dfe6f1;border-radius:9px;background:#fafcff;font-size:12px}
.tool summary{padding:9px 12px;cursor:pointer}
.tool summary small{float:right;color:#8d97a8}
.tool-state{display:inline-block;width:7px;height:7px;border-radius:50%;margin-right:7px;background:#16a064}
.tool-state.running{background:#f0a020;animation:blink 1s infinite}
.tool label{display:block;color:#8490a3;margin:5px 12px}
.tool pre,.interrupt pre{margin:0 12px 10px;padding:9px;background:#101827;color:#d7e1ef;border-radius:6px;white-space:pre-wrap;max-height:180px;overflow:auto}
.todo-card,.interrupt{max-width:82%;padding:15px;border:1px solid #dfe7f4;border-radius:11px;background:#fbfcff;margin-bottom:20px}
.todo-card b{display:block;margin-bottom:8px}
.todo-card div{padding:5px 0;color:#59657a}
.todo-card span{display:inline-grid;width:20px;margin-right:5px;color:#8f99a9}
.todo-card span.in_progress{color:#0b5cff}
.todo-card span.completed,.todo-card span.complete{color:#15a06a}
.interrupt h3{margin:0 0 8px}
.interrupt p{color:#5f6c81;font-size:13px}
.interrupt.approval{border-color:#bcd2ff;background:#f3f7ff}
.interrupt.supplement{border-color:#f1d298;background:#fffaf0}
.interrupt .el-tag{margin:0 6px 9px 0}
.composer{border-top:1px solid #e8edf5;padding:16px 20px}
.composer>div{display:flex;justify-content:space-between;align-items:center;margin-top:8px}
.composer small{color:#929bab}
.cursor{animation:blink 1s infinite}
@keyframes blink{50%{opacity:0}}
@media(max-width:900px){.history{display:none}.phase-step span{display:none}}
</style>
