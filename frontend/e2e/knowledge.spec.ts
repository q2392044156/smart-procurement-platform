import { expect, test } from '@playwright/test'
import path from 'node:path'

const sample = path.resolve(process.cwd(), '..', 'samples', 'knowledge', 'internal-procurement-policy.docx')
const title = '[E2E] 采购制度上传验证'

test('管理员可以上传、发布、查看版本并归档知识库文档', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: '登录平台' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)

  await page.getByRole('menuitem', { name: '知识库' }).click()
  await expect(page.getByRole('heading', { name: '知识库', level: 1 })).toBeVisible()
  await expect(page.getByRole('button', { name: '上传文档' })).toBeVisible()

  await page.getByRole('button', { name: '上传文档' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('上传知识库文档')).toBeVisible()
  await dialog.locator('.el-form-item').filter({ hasText: '标题' }).locator('input').fill(title)
  await dialog.locator('input[type=file]').setInputFiles(sample)
  await dialog.getByRole('button', { name: '上传', exact: true }).click()
  await expect(dialog).toBeHidden()

  const row = page.getByRole('row').filter({ hasText: title })
  await expect(row).toBeVisible()
  await expect(row.getByText('草稿')).toBeVisible()

  await row.getByRole('button', { name: '发布' }).click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(row.getByText('已发布')).toBeVisible()

  await row.getByRole('button', { name: '详情' }).click()
  const drawer = page.getByRole('dialog')
  await expect(drawer.getByText('文档详情')).toBeVisible()
  await expect(drawer.getByText('版本历史')).toBeVisible()
  await expect(drawer.getByText('internal-procurement-policy.docx').first()).toBeVisible()
  await expect(drawer.getByRole('button', { name: '下载当前版本' })).toBeVisible()
  await page.keyboard.press('Escape')

  await row.getByRole('button', { name: '归档' }).click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(row.getByText('已归档')).toBeVisible()

  await row.getByRole('button', { name: '删除' }).click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(row).toHaveCount(0)
})

test('申请人只能查看已发布文档，看不到维护按钮', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: '申请人', exact: true }).click()
  await page.getByRole('button', { name: '登录平台' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)

  await page.getByRole('menuitem', { name: '知识库' }).click()
  await expect(page.getByRole('heading', { name: '知识库', level: 1 })).toBeVisible()
  await expect(page.getByRole('button', { name: '上传文档' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '发布' })).toHaveCount(0)
})
