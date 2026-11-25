import { expect, test, type Page } from '@playwright/test'

// AI 助手是可选的：只有 `docker compose --profile agent up -d` 且后端 AGENT_ENABLED=true 时，
// /api/v1/ai/status 才返回 enabled=true。CI 只启动平台本体（backend、frontend），
// 因此需要真实 Agent 的用例会自动跳过；本地接入 Agent 后这些用例会自动执行。
const NEED_AGENT = '未启用可选 Agent（后端 AGENT_ENABLED=false），跳过需要真实 Agent 的用例'

async function openAssistant(page: Page) {
  await page.goto('/login')
  await page.getByRole('button', { name: '登录平台' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  await page.getByRole('menuitem', { name: 'AI采购助手' }).click()
  await expect(page.getByRole('heading', { name: 'AI采购助手', level: 1 })).toBeVisible()
  const badge = page.locator('.head-actions .el-tag')
  await expect(badge).toBeVisible({ timeout: 15_000 })
  return (await badge.innerText()).includes('已连接')
}

async function ask(page: Page, text: string) {
  const box = page.getByPlaceholder('例如：查询低库存物资并给出采购建议')
  await box.fill(text)
  await page.keyboard.press('Control+Enter')
}

test('未启用 Agent 时给出明确提示且不会误发请求', async ({ page }) => {
  test.setTimeout(60_000)
  test.skip(await openAssistant(page), '当前环境已启用 Agent，该用例只在未启用时执行')
  await expect(page.locator('.head-actions .el-tag')).toHaveText('Agent 未启用')
  await expect(page.getByPlaceholder('请先在后端启用 Agent 集成')).toBeDisabled()
  await expect(page.getByRole('button', { name: '发送' })).toBeDisabled()
  await page.getByRole('button', { name: '历史会话' }).click()
  await expect(page.getByText('暂无历史')).toBeVisible()
})

test('图表与表格渲染：表格出 HTML 表格、图片内联并带下载按钮', async ({ page }) => {
  test.setTimeout(240_000)
  test.skip(!(await openAssistant(page)), NEED_AGENT)
  await ask(page, '把前 5 个供应商按订单金额做个柱状图')

  const bubble = page.locator('.bubble.markdown').last()
  await expect(page.getByRole('button', { name: '停止生成' })).toBeVisible({ timeout: 20_000 })
  await expect(bubble.locator('table')).toBeVisible({ timeout: 180_000 })
  await expect(bubble.locator('thead th').first()).toBeVisible()
  await expect(bubble.locator('img')).toBeVisible({ timeout: 60_000 })
  await expect(bubble.getByRole('button', { name: '下载' })).toBeVisible()
  await expect(page.getByText('已停止生成')).toHaveCount(0)
  await page.screenshot({ path: 'test-results/assistant-markdown.png', fullPage: false })
})

test('生成过程中可以中断 AI 回复', async ({ page }) => {
  test.setTimeout(120_000)
  test.skip(!(await openAssistant(page)), NEED_AGENT)
  await ask(page, '把前 5 个供应商按订单金额做个柱状图，并逐个分析采购集中度风险')

  const stopButton = page.getByRole('button', { name: '停止生成' })
  await expect(stopButton).toBeVisible({ timeout: 20_000 })
  await stopButton.click()
  await expect(page.getByText('已停止生成').first()).toBeVisible({ timeout: 20_000 })
  await expect(page.getByRole('button', { name: '发送' })).toBeVisible()
})

test('历史会话可以删除', async ({ page }) => {
  test.setTimeout(120_000)
  test.skip(!(await openAssistant(page)), NEED_AGENT)
  await ask(page, '你好，介绍一下你能做什么')
  await expect(page.getByRole('button', { name: '发送' })).toBeVisible({ timeout: 90_000 })

  await page.getByRole('button', { name: '历史会话' }).click()
  const items = page.locator('.history-item')
  await expect(items.first()).toBeVisible({ timeout: 20_000 })
  const before = await items.count()

  await items.first().hover()
  await items.first().locator('.history-delete').click()
  await page.getByRole('dialog').getByRole('button', { name: '删除' }).click()
  await expect(page.getByText('会话已删除')).toBeVisible()
  await expect(items).toHaveCount(before - 1)
})
