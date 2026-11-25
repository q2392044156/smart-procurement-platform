/**
 * AI 助手的事件与消息模型，字段与后端 /ai/chat/stream 的 SSE 契约一一对应。
 * 前端只按 type 分派，未识别的事件类型直接忽略，便于后端灰度新增事件。
 */

export interface AgentEvent { type:string; [key:string]:unknown }
/** 一次工具调用的记录；status 驱动折叠面板的状态圆点，args/result 为流式拼接的原文 */
export interface AgentToolCall { id:string; name:string; args:string; result?:string; status:'running'|'done'|'error' }
/** Agent 规划出的待办项，status 取 pending/in_progress/completed，用于「执行计划」卡片 */
export interface AgentTodo { id:string; content:string; status:string }
/**
 * 中断请求：hitl_approval 表示写操作待人工确认，order_info_supplement 表示下单信息缺失。
 * 两种中断在 resume 时组装的 payload 不同，前端只负责原样回传。
 */
export interface AgentInterrupt {
  interrupt_type:'hitl_approval'|'order_info_supplement'
  tool_name?:string
  tool_args?:Record<string,unknown>
  order_data?:Record<string,unknown>
  missing_fields?:string[]
  extracted_data?:Record<string,unknown>
  message?:string
}
/** 一条会话消息；assistant 消息可携带工具调用与「已停止生成」标记 */
export interface AgentMessage { role:'user'|'assistant'; content:string; toolCalls?:AgentToolCall[]; stopped?:boolean }

/**
 * text/event-stream 增量解析器：按空行切事件，event: 取类型、data: 取载荷。
 * 不足一个事件的残片留在 buffer，等下一个数据块到达后继续拼。
 */
export class SseParser {
  private buffer=''
  // 只消费完整的块；JSON.parse 失败时把原文放进 content，避免整条流中断
  parse(chunk:string):AgentEvent[]{
    this.buffer+=chunk.replace(/\r\n/g,'\n')
    const blocks=this.buffer.split('\n\n')
    this.buffer=blocks.pop()||''
    const events:AgentEvent[]=[]
    for(const block of blocks){
      let type='message';const data:string[]=[]
      for(const line of block.split('\n')){if(line.startsWith('event:'))type=line.slice(6).trim();else if(line.startsWith('data:'))data.push(line.slice(5).trimStart())}
      if(!data.length)continue
      const raw=data.join('\n')
      try{events.push({type,...JSON.parse(raw)})}catch{events.push({type,content:raw})}
    }
    return events
  }
}
