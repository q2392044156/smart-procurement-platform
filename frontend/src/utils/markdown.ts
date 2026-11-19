/**
 * 轻量 Markdown 渲染器
 *
 * 只做 AI 回复实际会用到的子集：标题、表格、有序/无序列表、代码块、引用、分隔线、
 * 粗体、斜体、行内代码、链接。故意不引入第三方依赖——渲染结果先转义再拼接，
 * 所以模型输出里的 HTML 不会被当成标签执行。
 */

const ESCAPE: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, ch => ESCAPE[ch])
}

function safeHref(raw: string): string {
  const url = raw.trim()
  if (/^(https?:|mailto:|\/|#)/i.test(url) || /^[\w.-]+(\/|$)/.test(url)) return escapeHtml(url)
  return '#'
}

/** 行内语法：行内代码 → 链接 → 粗体 → 斜体；输入必须是已转义文本 */
function inline(escaped: string): string {
  return escaped
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_all, label: string, href: string) => `<a href="${safeHref(href)}" target="_blank" rel="noopener">${label}</a>`)
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>')
}

function renderTable(header: string, divider: string, rows: string[]): string {
  const cells = (line: string) => line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(cell => cell.trim())
  const columns = cells(header).length
  const aligns = cells(divider).map(spec => {
    if (/^:.*:$/.test(spec)) return 'center'
    if (/:$/.test(spec)) return 'right'
    return ''
  })
  const head = cells(header).map((cell, i) => `<th${aligns[i] ? ` style="text-align:${aligns[i]}"` : ''}>${inline(escapeHtml(cell))}</th>`).join('')
  const body = rows.map(row => {
    const values = cells(row)
    while (values.length < columns) values.push('')
    const tds = values.slice(0, columns).map((cell, i) => `<td${aligns[i] ? ` style="text-align:${aligns[i]}"` : ''}>${inline(escapeHtml(cell))}</td>`).join('')
    return `<tr>${tds}</tr>`
  }).join('')
  return `<table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`
}

const isTableDivider = (line: string) => /^\s*\|?[\s:-]*-[\s|:-]*\|?\s*$/.test(line) && line.includes('-') && line.includes('|')
const isTableRow = (line: string) => line.includes('|') && line.trim().length > 1

// 模型输出的列表项经常是「-文字」而不是「- 文字」，这里两种都认；
// 但「-5 元」「—」这类正文里的负号与破折号不算列表。
function bulletContent(line: string): string | null {
  const spaced = line.match(/^\s*[-*+]\s+(.*)$/)
  if (spaced) return spaced[1]
  const glued = line.match(/^\s*[-–—](?=[^\d\s.、，])(.*)$/)
  return glued ? glued[1] : null
}

// 模型经常写成「###二、准入前置条件」（# 后面没有空格）或「1.若需…」（点后没有空格），
// 过于严格的 CommonMark 规则会把它们当正文，这里放宽。
function headingContent(line: string): { level: number; text: string } | null {
  const match = line.match(/^\s*(#{1,6})(?!#)\s*(.*)$/)
  if (!match) return null
  const text = match[2].replace(/\s*#+\s*$/, '').trim()
  return text ? { level: match[1].length, text } : null
}

/**
 * 标题后面常常直接粘着下一块内容（「###三、材料提交-资质文件须上传…」、「###排名明细|排名 |…」），
 * 标题只能吃到自己该有的那几个字，其余部分要吐回给主循环按列表/表格重新解析。
 */
function splitHeadingText(text: string): { head: string; rest: string } {
  const cuts: number[] = []
  const pipe = text.indexOf('|')
  if (pipe > 0) cuts.push(pipe)
  const bullet = text.match(/[-–—](?=[^\s\d.、，])/)
  if (bullet?.index && bullet.index > 0) cuts.push(bullet.index)
  // 「统计口径- 数据源：xxx」这种带空格的分隔符，只有后面紧跟「标签：」才算断点，
  // 否则「供应商准入 - 绩效评价」这类正常标题会被切坏
  const spaced = text.match(/[-–—]\s+(?=[A-Za-z\u4e00-\u9fa5]{1,12}[：:])/)
  if (spaced?.index && spaced.index > 0) cuts.push(spaced.index)
  const numbered = text.match(/(?<!\d)\d{1,2}[.)]\s*(?=\S)/)
  if (numbered?.index && numbered.index > 0) cuts.push(numbered.index)
  if (!cuts.length) return { head: text, rest: '' }
  const cut = Math.min(...cuts)
  return { head: text.slice(0, cut).trim(), rest: text.slice(cut).trim() }
}

function orderedContent(line: string): string | null {
  const match = line.match(/^\s*\d{1,2}[.)](?![0-9])\s*(.+)$/)
  return match ? match[1] : null
}

function isBlockStart(line: string, next: string | undefined): boolean {
  if (headingContent(line)) return true
  if (/^\s*(```|>)/.test(line)) return true
  if (bulletContent(line) !== null) return true
  if (orderedContent(line) !== null) return true
  if (/^\s*([-*_])\1{2,}\s*$/.test(line)) return true
  return isTableRow(line) && next !== undefined && isTableDivider(next)
}

/** 把 Markdown 文本渲染成可直接 v-html 的 HTML 片段 */
export function renderMarkdown(source: string): string {
  if (!source) return ''
  // 模型偶尔把标题粘在上一句后面（「…已生成 ##结果柱状图…」），补一个换行让它成为独立块
  const normalized = source.replace(/([^\n#])(#{2,6})(?=[^\s#])/g, '$1\n$2')
  const lines = normalized.replace(/\r\n?/g, '\n').split('\n')
  const html: string[] = []
  let index = 0
  while (index < lines.length) {
    const line = lines[index]
    if (!line.trim()) { index += 1; continue }

    // 代码块
    const fence = line.match(/^\s*```(\w*)\s*$/)
    if (fence) {
      const body: string[] = []
      index += 1
      while (index < lines.length && !/^\s*```\s*$/.test(lines[index])) { body.push(lines[index]); index += 1 }
      index += 1
      const lang = fence[1] ? ` class="language-${escapeHtml(fence[1])}"` : ''
      html.push(`<pre><code${lang}>${escapeHtml(body.join('\n'))}</code></pre>`)
      continue
    }

    // 标题
    const heading = headingContent(line)
    if (heading) {
      const { head, rest } = splitHeadingText(heading.text)
      html.push(`<h${heading.level}>${inline(escapeHtml(head))}</h${heading.level}>`)
      if (rest) lines[index] = rest
      else index += 1
      continue
    }

    // 分隔线
    if (/^\s*([-*_])\1{2,}\s*$/.test(line)) { html.push('<hr>'); index += 1; continue }

    // 表格：表头 + 分隔行 + 若干行
    if (isTableRow(line) && index + 1 < lines.length && isTableDivider(lines[index + 1])) {
      const rows: string[] = []
      index += 2
      while (index < lines.length && isTableRow(lines[index])) { rows.push(lines[index]); index += 1 }
      html.push(renderTable(line, lines[index - rows.length - 1], rows))
      continue
    }

    // 引用
    if (/^\s*>\s?/.test(line)) {
      const body: string[] = []
      while (index < lines.length && /^\s*>\s?/.test(lines[index])) { body.push(lines[index].replace(/^\s*>\s?/, '')); index += 1 }
      html.push(`<blockquote>${renderMarkdown(body.join('\n'))}</blockquote>`)
      continue
    }

    // 无序列表
    if (bulletContent(line) !== null) {
      const items: string[] = []
      while (index < lines.length && bulletContent(lines[index]) !== null) {
        items.push(bulletContent(lines[index]) as string)
        index += 1
      }
      html.push(`<ul>${items.map(item => `<li>${inline(escapeHtml(item))}</li>`).join('')}</ul>`)
      continue
    }

    // 有序列表
    if (orderedContent(line) !== null) {
      const items: string[] = []
      while (index < lines.length && orderedContent(lines[index]) !== null) {
        items.push(orderedContent(lines[index]) as string)
        index += 1
      }
      html.push(`<ol>${items.map(item => `<li>${inline(escapeHtml(item))}</li>`).join('')}</ol>`)
      continue
    }

    // 段落：连续的普通行合并，行间用换行保留模型排版
    const paragraph: string[] = []
    while (index < lines.length && lines[index].trim() && !isBlockStart(lines[index], lines[index + 1])) {
      paragraph.push(lines[index])
      index += 1
    }
    html.push(`<p>${paragraph.map(item => inline(escapeHtml(item.trim()))).join('<br>')}</p>`)
  }
  return html.join('\n')
}
