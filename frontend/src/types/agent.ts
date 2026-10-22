export interface AgentEvent { type:string; [key:string]:unknown }
export interface AgentToolCall { id:string; name:string; args:string; result?:string; status:'running'|'done'|'error' }
export interface AgentTodo { id:string; content:string; status:string }
export interface AgentInterrupt {
  interrupt_type:'hitl_approval'|'order_info_supplement'
  tool_name?:string
  tool_args?:Record<string,unknown>
  order_data?:Record<string,unknown>
  missing_fields?:string[]
  extracted_data?:Record<string,unknown>
  message?:string
}
export interface AgentMessage { role:'user'|'assistant'; content:string; toolCalls?:AgentToolCall[] }

export class SseParser {
  private buffer=''
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
