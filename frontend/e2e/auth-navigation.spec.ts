import { expect, test } from '@playwright/test'

test('管理员可登录并打开经营看板', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: '登录平台' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByRole('heading', { name: '经营看板', level: 1 })).toBeVisible()
  await expect(page.getByText('系统管理员')).toBeVisible()
})

test('申请人只显示获授权菜单且直接访问订单页会被拦截', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: '申请人', exact: true }).click()
  await page.getByRole('button', { name: '登录平台' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByRole('menuitem', { name: '采购申请' })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: '采购订单' })).toHaveCount(0)
  await page.goto('/orders')
  await expect(page).toHaveURL(/\/dashboard$/)
})
