---
name: playwright
icon: Rocket20Regular
title: Playwright 浏览器自动化
description: 使用 Playwright 编写可靠的浏览器自动化与端到端测试，覆盖定位器、断言、等待、表单、网络拦截、多标签、移动端模拟与追踪调试。
version: 1.0.0
author: system
tags:
  - browser
  - e2e
  - testing
---

# Playwright 浏览器自动化

优先使用 Playwright Test（`@playwright/test`）；一次性脚本可用 `playwright` 库。定位元素一律用 Locator，避免手写 XPath/长 CSS 与固定 sleep。

## 安装

```bash
npm init playwright@latest      # 含浏览器与示例
npx playwright install          # 仅安装浏览器
# Python：pip install playwright && playwright install
```

## 定位器（按优先级）

```ts
page.getByRole('button', { name: '提交' })
page.getByLabel('邮箱')
page.getByPlaceholder('请输入')
page.getByText('欢迎')
page.getByTestId('submit')
locator.first() / .filter({ hasText: '...' })
```

## 动作与自动等待

Playwright 会自动等待元素可操作：

```ts
await page.goto('/login')
await page.getByLabel('邮箱').fill('a@b.com')
await page.getByLabel('密码').fill('123')
await page.getByRole('button').click()
await page.locator('li').nth(2).click()
await page.selectOption('#city', 'sh')
await page.check('#agree')
```

## 断言（Web-first assertions）

```ts
await expect(page.getByRole('heading')).toHaveText('首页')
await expect(page.locator('.item')).toHaveCount(5)
await expect(page.locator('.msg')).toBeVisible()
await expect(locator).toHaveValue('...')
```

断言自带重试，优先用它而非手动 `waitForSelector`。

## 常用场景

- 显式等待：`await page.waitForLoadState('networkidle')`、`waitForURL(...)`。
- 网络拦截/Mock：`page.route('**/api/**', r => r.fulfill({ json: {...} }))`；监听响应 `page.waitForResponse(r => r.url().includes('/api') && r.status() === 200)`。
- 文件上传：`setInputFiles('#up', '/abs/a.png')`；下载：`waitForEvent('download')` 后 `download.saveAs(...)`。
- iframe：`frameLocator('#f').getByRole('button')`；多标签：`context.waitForEvent('page')`。
- 模拟设备/权限/地理位置：`devices['iPhone 14']`、`context.grantPermissions(['geolocation'])`。

## 调试与产物

- 调试：`--debug`、`page.pause()`；UI 模式：`npx playwright test --ui`。
- 失败自动截图/录像/追踪：在配置中开启 `use: { trace: 'on-first-retry', screenshot: 'only-on-failure', video: 'retain-on-failure' }`；查看追踪 `npx playwright show-trace trace.zip`。

## 原则

- 不依赖固定 `waitForTimeout`；用定位器与断言的自动等待保证稳定。
- 测试要隔离、可重复：用 `test.beforeEach` 准备状态，避免依赖其他用例。
- 不绕过登录/验证码；需要登录态时通过官方测试账号或注入受控 token。
