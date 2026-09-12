import { defineConfig } from "@playwright/test";

/**
 * AiGateway E2E 验收配置。
 *
 * 生产级 API 验收：直连 AI 网关（默认 9898，可用 AI_GATEWAY_BASE 覆盖）。
 * webServer 自动拉起本地网关（start-gateway.cmd），测试结束自动回收；
 * 若 9898 已被占用（已有一个网关在跑），reuseExistingServer 直接复用。
 * 覆盖：插件市场对接（/api/skills*）+ Bot 绑定链路（/v2/ai/bot-binding*）。
 */
export default defineConfig({
  testDir: "./",
  timeout: 120_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never", outputFolder: "playwright-report" }]],
  use: {
    baseURL: process.env.AI_GATEWAY_BASE || "http://127.0.0.1:9898",
    extraHTTPHeaders: {
      "Content-Type": "application/json",
      "User-Agent": "ai-gateway-e2e",
    },
  },
  webServer: {
    command: "cmd /c start-gateway.cmd",
    url: process.env.AI_GATEWAY_BASE || "http://127.0.0.1:9898",
    reuseExistingServer: true,
    timeout: 120_000,
    // 网关日志量大（DEBUG 级），不接入 Playwright 输出流，避免刷屏
    stdout: "ignore",
    stderr: "ignore",
  },
});