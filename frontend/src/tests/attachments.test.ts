import { describe, expect, it } from 'vitest'
import { fileNameFromUrl, splitAttachments } from '@/utils/attachments'

describe('splitAttachments', () => {
  it('turns an image link into an inline image attachment', () => {
    const segments = splitAttachments('图表已生成：\n![柱状图](http://localhost:8000/api/download/%E9%94%80%E5%94%AE%E5%9B%BE.png)')
    expect(segments).toHaveLength(2)
    expect(segments[0]).toEqual({ type: 'text', text: '图表已生成：\n' })
    expect(segments[1].type).toBe('attachment')
    if (segments[1].type === 'attachment') {
      expect(segments[1].attachment.name).toBe('销售图.png')
      expect(segments[1].attachment.kind).toBe('image')
      expect(segments[1].attachment.label).toBe('柱状图')
    }
  })

  it('detects plain download links produced by the sandbox tool', () => {
    const segments = splitAttachments('-图表下载：[供应商采购金额TOP5_2026.png](http://localhost:8000/api/download/供应商采购金额TOP5_2026.png)')
    const attachment = segments.find(s => s.type === 'attachment')
    expect(attachment).toBeDefined()
    if (attachment && attachment.type === 'attachment') {
      expect(attachment.attachment.name).toBe('供应商采购金额TOP5_2026.png')
      expect(attachment.attachment.kind).toBe('image')
    }
  })

  it('treats non-image documents as file attachments', () => {
    const segments = splitAttachments('已生成报告：[采购分析.md](/api/v1/ai/files/采购分析.md)')
    const attachment = segments.find(s => s.type === 'attachment')
    expect(attachment && attachment.type === 'attachment' && attachment.attachment.kind).toBe('file')
  })

  it('leaves ordinary links in the text segment', () => {
    const segments = splitAttachments('参考 [华为官网](https://www.huawei.com) 的说明')
    expect(segments).toHaveLength(1)
    expect(segments[0].type).toBe('text')
  })

  it('falls back to a single text segment for plain answers', () => {
    const segments = splitAttachments('当前有 3 个物资低于安全库存。')
    expect(segments).toEqual([{ type: 'text', text: '当前有 3 个物资低于安全库存。' }])
  })

  it('recognises a bare download url without markdown syntax', () => {
    const segments = splitAttachments('图表生成完成。\n下载链接: /api/download/2026年度前5大供应商采购金额对比.png\n文件大小: 87.1 KB')
    const attachment = segments.find(s => s.type === 'attachment')
    expect(attachment).toBeDefined()
    if (attachment && attachment.type === 'attachment') {
      expect(attachment.attachment.name).toBe('2026年度前5大供应商采购金额对比.png')
      expect(attachment.attachment.kind).toBe('image')
    }
    const tail = segments[segments.length - 1]
    expect(tail.type === 'text' ? tail.text.includes('文件大小') : false).toBe(true)
  })

  it('drops the trailing punctuation of a bare url', () => {
    const segments = splitAttachments('见 /api/download/report.pdf，有问题再问我')
    const attachment = segments.find(s => s.type === 'attachment')
    if (attachment && attachment.type === 'attachment') {
      expect(attachment.attachment.name).toBe('report.pdf')
      expect(attachment.attachment.url).toBe('/api/download/report.pdf')
    }
    const tail = segments[segments.length - 1]
    expect(tail.type === 'text' ? tail.text.startsWith('，') : false).toBe(true)
  })

  it('handles a url wrapped in inline code backticks', () => {
    const segments = splitAttachments('下载链接：`/api/download/top5_supplier_amount_2026.png` 请查收')
    const attachment = segments.find(s => s.type === 'attachment')
    expect(attachment).toBeDefined()
    if (attachment && attachment.type === 'attachment') {
      expect(attachment.attachment.name).toBe('top5_supplier_amount_2026.png')
      expect(attachment.attachment.kind).toBe('image')
    }
  })

  it('stops the bare url at a table pipe glued right after it', () => {
    const segments = splitAttachments('下载链接：/api/download/2026年度采购订单金额Top5供应商.png|排名 |供应商 |\n|---|---|')
    const attachment = segments.find(s => s.type === 'attachment')
    expect(attachment).toBeDefined()
    if (attachment && attachment.type === 'attachment') {
      expect(attachment.attachment.name).toBe('2026年度采购订单金额Top5供应商.png')
      expect(attachment.attachment.kind).toBe('image')
    }
    const tail = segments[segments.length - 1]
    expect(tail.type === 'text' ? tail.text.startsWith('|排名') : false).toBe(true)
  })
})

describe('fileNameFromUrl', () => {
  it('decodes chinese names and strips query strings', () => {
    expect(fileNameFromUrl('/api/v1/ai/files/%E6%8A%A5%E5%91%8A.pdf?v=1')).toBe('报告.pdf')
  })
})
