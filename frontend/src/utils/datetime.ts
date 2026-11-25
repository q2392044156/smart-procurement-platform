/**
 * 后端返回的是 UTC ISO 字符串（如 2026-11-25T11:59:28.723+00:00），
 * 直接渲染会把 UTC 时间连时区一起显示出来，跨两行且比本地时间早 8 小时。
 * 统一走这里转成本地时区再展示。
 */
export function formatTime(value: unknown): string {
  // 空值统一回退为「—」，避免表格出现空白或 undefined
  if (!value) return '—'
  const date = new Date(String(value))
  if (Number.isNaN(date.getTime())) return String(value)
  // hour12: false 固定 24 小时制，避免上午/下午文案在窄列里折行
  return date.toLocaleString('zh-CN', { hour12: false })
}

// 纯日期字段（到期日、交付日）只显示到天，不显示 00:00
/** 只保留日期部分，用于到期日、交付日一类的纯日期字段。 */
export function formatDate(value: unknown): string {
  if (!value) return '—'
  const text = String(value)
  const date = new Date(text)
  if (Number.isNaN(date.getTime())) return text
  // 与列表里的日期列保持 zh-CN 的 2026/11/25 形式，排序仍按后端原值
  return date.toLocaleDateString('zh-CN')
}

// 数量列统一走这里：后端 BigDecimal 序列化成 1222.0000，直接渲染会多出四位尾零，
// 与「1222」这类手工录入值混排时列宽也会跳动。
/**
 * 数量字段（BigDecimal 序列化成 1222.0000）去掉无意义的尾随零，
 * 1222.0000 → 1,222，1.5000 → 1.5。
 */
export function formatQty(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const num = Number(value)
  // 非数字（如「若干」）按原文展示，不强行转 0，避免误导库存
  if (Number.isNaN(num)) return String(value)
  // toLocaleString 负责千分位分组；4 位小数与后端数量精度对齐，超出部分四舍五入
  return num.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}
