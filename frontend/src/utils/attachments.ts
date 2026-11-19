/**
 * 回复内容里的附件识别
 *
 * 模型生成的图表/文档会以 Markdown 链接或图片的形式出现在正文里（沙箱回传后由
 * `download_sandbox_file` 落盘），浏览器无法直接带鉴权头访问 harness 的下载地址，
 * 所以这里先把这类链接拆出来，交给 <ChatAttachment> 用带 Authorization 的请求取
 * Blob 再展示/下载。
 */

const IMAGE_EXT = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp'])
const FILE_EXT = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'csv', 'md', 'txt', 'json', 'html', 'zip'])

const IMAGE_LINK = /!\[([^\]]*)\]\(([^)\s]+)\)/g
const PLAIN_LINK = /\[([^\]]+)\]\(([^)\s]+)\)/g
// 裸链接末尾常紧跟中文标点、反引号、表格竖线或说明文字，字符集要收窄，否则会把后面的内容吞进来
const BARE_URL = /(?:https?:\/\/[^\s<>()]+)?(?:\/api\/download\/|\/api\/v1\/ai\/files\/|\/data\/download\/)[^\s<>()[\]{}"'`|，。；：！？、）】]+/g

export interface Attachment {
  /** 沙箱回传后的文件名，用于下载接口 */
  name: string
  /** 展示名：优先用链接文字，其次用文件名 */
  label: string
  kind: 'image' | 'file'
  /** 原始 URL，仅用于兜底提示 */
  url: string
}

export type Segment = { type: 'text'; text: string } | { type: 'attachment'; attachment: Attachment }

export function fileExtension(value: string): string {
  const match = value.trim().match(/\.([A-Za-z0-9]+)$/)
  return match ? match[1].toLowerCase() : ''
}

export function fileNameFromUrl(url: string): string {
  const path = url.split('?')[0].split('#')[0]
  const last = path.split('/').filter(Boolean).pop() || ''
  try {
    return decodeURIComponent(last)
  } catch {
    return last
  }
}

function isAttachmentUrl(url: string, label = ''): boolean {
  if (/\/api\/download\//.test(url) || /\/api\/v1\/ai\/files\//.test(url) || /\/data\/download\//.test(url)) return true
  return FILE_EXT.has(fileExtension(fileNameFromUrl(url))) || FILE_EXT.has(fileExtension(label))
}

/** 把一条回复拆成「正文片段 + 附件片段」 */
export function splitAttachments(text: string): Segment[] {
  if (!text) return []
  const hits: { start: number; end: number; attachment: Attachment }[] = []
  const collect = (pattern: RegExp) => {
    pattern.lastIndex = 0
    let match: RegExpExecArray | null
    while ((match = pattern.exec(text)) !== null) {
      const label = match[1] || ''
      const url = match[2] || ''
      if (!isAttachmentUrl(url, label)) continue
      const name = fileNameFromUrl(url) || label
      if (!name) continue
      hits.push({
        start: match.index,
        end: match.index + match[0].length,
        attachment: { name, label: label || name, kind: IMAGE_EXT.has(fileExtension(name)) ? 'image' : 'file', url },
      })
    }
  }
  collect(IMAGE_LINK)
  collect(PLAIN_LINK)
  // 模型有时只给一个裸链接（「下载链接: /api/download/x.png」），也要能认出来
  BARE_URL.lastIndex = 0
  let bare: RegExpExecArray | null
  while ((bare = BARE_URL.exec(text)) !== null) {
    const raw = bare[0].replace(/[,.;:!?，。；：！？)\]】]+$/, '')
    if (!isAttachmentUrl(raw)) continue
    const name = fileNameFromUrl(raw)
    if (!name) continue
    hits.push({
      start: bare.index,
      end: bare.index + raw.length,
      attachment: { name, label: name, kind: IMAGE_EXT.has(fileExtension(name)) ? 'image' : 'file', url: raw },
    })
  }
  hits.sort((a, b) => a.start - b.start)

  const segments: Segment[] = []
  let cursor = 0
  for (const hit of hits) {
    if (hit.start < cursor) continue
    // 裸链接常见写法是「下载链接：`/api/download/x.png`」，链接被摘走后会剩下一个
    // 落单的反引号，顺手去掉
    const body = text.slice(cursor, hit.start).replace(/`+\s*$/, '')
    if (body.trim()) segments.push({ type: 'text', text: body })
    segments.push({ type: 'attachment', attachment: hit.attachment })
    cursor = hit.end
  }
  const rest = text.slice(cursor).replace(/^`/, '')
  if (rest.trim()) segments.push({ type: 'text', text: rest })
  return segments.length ? segments : [{ type: 'text', text }]
}
