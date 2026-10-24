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

test('管理员可使用补齐后的主数据、供应商档案和系统视图', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: '登录平台' }).click()

  await page.getByRole('menuitem', { name: '物资主数据' }).click()
  await page.getByRole('tab', { name: '分类与动态属性' }).click()
  await expect(page.getByText('IT设备', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '新增分类' })).toBeVisible()

  await page.getByRole('menuitem', { name: '供应商管理' }).click()
  await page.getByRole('button', { name: '档案' }).first().click()
  await expect(page.getByRole('heading', { name: '资质证照' })).toBeVisible()
  await page.keyboard.press('Escape')

  await page.getByRole('menuitem', { name: '系统与审计' }).click()
  await page.getByRole('tab', { name: '权限清单' }).click()
  await expect(page.getByText('dashboard:view', { exact: true })).toBeVisible()
})

test('申请人可打开采购申请新建表单并看到预算合计', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: '申请人', exact: true }).click()
  await page.getByRole('button', { name: '登录平台' }).click()
  await page.getByRole('menuitem', { name: '采购申请' }).click()
  await page.getByRole('button', { name: '新建申请' }).click()
  await expect(page.getByText('预算合计', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '+ 添加明细' })).toBeVisible()
})
