import { describe, expect, it } from 'vitest'
import { SseParser } from '@/types/agent'

describe('SseParser',()=>{
  it('keeps event names and supports split chunks',()=>{
    const parser=new SseParser()
    expect(parser.parse('event: phase\ndata: {"phase":"exec')).toEqual([])
    expect(parser.parse('uting","label":"执行中"}\n\nevent: token\ndata: {"content":"完成"}\n\n')).toEqual([
      {type:'phase',phase:'executing',label:'执行中'},
      {type:'token',content:'完成'}
    ])
  })
})
