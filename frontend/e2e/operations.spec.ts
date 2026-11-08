import { expect, test } from '@playwright/test'

test('管理员可查看部门预算与合同台账', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: '登录平台' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)

  await page.getByRole('menuitem', { name: '部门预算' }).click()
  await expect(page.getByRole('heading', { name: '部门预算', level: 1 })).toBeVisible()
  await expect(page.getByRole('cell', { name: '研发部' })).toBeVisible()
  await expect(page.getByRole('button', { name: '调整' }).first()).toBeVisible()

  await page.getByRole('menuitem', { name: '合同管理' }).click()
  await expect(page.getByRole('heading', { name: '合同管理', level: 1 })).toBeVisible()
  await expect(page.getByText('CT-DEMO-001')).toBeVisible()
  await expect(page.getByRole('button', { name: '新建合同' })).toBeVisible()
})
