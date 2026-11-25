/**
 * 后端返回的是 UTC ISO 字符串（如 2026-11-25T11:59:28.723+00:00），
 * 直接渲染会把 UTC 时间连时区一起显示出来，跨两行且比本地时间早 8 小时。
 * 统一走这里转成本地时区再展示。
 */
export function formatTime(value: unknown): string {
  if (!value) return '—'
  const date = new Date(String(value))
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

/** 只保留日期部分，用于到期日、交付日一类的纯日期字段。 */
export function formatDate(value: unknown): string {
  if (!value) return '—'
  const text = String(value)
  const date = new Date(text)
  if (Number.isNaN(date.getTime())) return text
  return date.toLocaleDateString('zh-CN')
}

/**
 * 数量字段（BigDecimal 序列化成 1222.0000）去掉无意义的尾随零，
 * 1222.0000 → 1,222，1.5000 → 1.5。
 */
export function formatQty(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const num = Number(value)
  if (Number.isNaN(num)) return String(value)
  return num.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}
