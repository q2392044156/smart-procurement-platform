/**
 * 回复内容里的附件识别
 *
 * 模型生成的图表/文档会以 Markdown 链接或图片的形式出现在正文里（沙箱回传后由
 * `download_sandbox_file` 落盘），浏览器无法直接带鉴权头访问 harness 的下载地址，
 * 所以这里先把这类链接拆出来，交给 <ChatAttachment> 用带 Authorization 的请求取
 * Blob 再展示/下载。
 */

// 图片扩展名：命中则按图片内联预览，否则走文件下载卡片
const IMAGE_EXT = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp'])
// 可识别的附件扩展名，需与后端文件白名单一致，否则会出现「识别得到但取不到」
const FILE_EXT = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'csv', 'md', 'txt', 'json', 'html', 'zip'])

const IMAGE_LINK = /!\[([^\]]*)\]\(([^)\s]+)\)/g
const PLAIN_LINK = /\[([^\]]+)\]\(([^)\s]+)\)/g
// 裸链接末尾常紧跟中文标点、反引号、表格竖线或说明文字，字符集要收窄，否则会把后面的内容吞进来
const BARE_URL = /(?:https?:\/\/[^\s<>()]+)?(?:\/api\/download\/|\/api\/v1\/ai\/files\/|\/data\/download\/)[^\s<>()[\]{}"'`|，。；：！？、）】]+/g

/** 回复正文里识别出的单个附件；只存元数据，二进制由 ChatAttachment 取回 */
export interface Attachment {
  /** 沙箱回传后的文件名，用于下载接口 */
  name: string
  /** 展示名：优先用链接文字，其次用文件名 */
  label: string
  kind: 'image' | 'file'
  /** 原始 URL，仅用于兜底提示 */
  url: string
}

/** 拆分结果：文本与附件按原文顺序交替，模板据此逐段渲染 */
export type Segment = { type: 'text'; text: string } | { type: 'attachment'; attachment: Attachment }

/**
 * 取小写扩展名；无扩展名返回空串。只看最后一个点后的字母数字，不解析查询串。
 */
export function fileExtension(value: string): string {
  const match = value.trim().match(/\.([A-Za-z0-9]+)$/)
  return match ? match[1].toLowerCase() : ''
}

/**
 * 从下载地址取文件名：先去掉 ?query 与 #hash，再 decodeURIComponent。
 * 解码失败（地址里有非法百分号）时退回原始片段，保证仍能下载。
 */
export function fileNameFromUrl(url: string): string {
  const path = url.split('?')[0].split('#')[0]
  const last = path.split('/').filter(Boolean).pop() || ''
  try {
    return decodeURIComponent(last)
  } catch {
    return last
  }
}

/**
 * 判定是否附件：三个下载前缀直接命中，否则看 URL 或链接文字的扩展名。
 * 普通正文链接（如 https://example.com）不会被误判成附件。
 */
function isAttachmentUrl(url: string, label = ''): boolean {
  if (/\/api\/download\//.test(url) || /\/api\/v1\/ai\/files\//.test(url) || /\/data\/download\//.test(url)) return true
  return FILE_EXT.has(fileExtension(fileNameFromUrl(url))) || FILE_EXT.has(fileExtension(label))
}

/** 把一条回复拆成「正文片段 + 附件片段」 */
/**
 * 把一条回复拆成「正文片段 + 附件片段」。
 * 依次识别图片语法、普通链接、裸下载链接，按起点排序后用 cursor 跳过重叠命中；
 * 若最后一个片段都没拆出来，返回整段原文，避免正文被吞掉。
 */
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
  // 三类正则可能命中同一段文本，按起点排序后由下面的 cursor 去重
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
  // 收尾：链接被摘走后可能残留一个落单的反引号，顺手去掉
  const rest = text.slice(cursor).replace(/^`/, '')
  if (rest.trim()) segments.push({ type: 'text', text: rest })
  return segments.length ? segments : [{ type: 'text', text }]
}
