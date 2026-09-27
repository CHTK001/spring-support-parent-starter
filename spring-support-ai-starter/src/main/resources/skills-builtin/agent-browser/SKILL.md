---
name: agent-browser
icon: WindowConsole20Regular
title: Agent Browser 浏览器自动化
description: 通过 agent-browser CLI 以 CDP 驱动真实浏览器，完成打开网页、读取无障碍快照、点击、填表、选择、下载、截图、抓取数据与端到端业务流程测试。
version: 1.0.0
author: system
tags:
  - browser
  - automation
  - testing
---

# Agent Browser 浏览器自动化

`agent-browser` 通过 CDP 直接驱动 Chrome/Chromium。安装：`npm i -g agent-browser`；首次用 `agent-browser install` 下载浏览器。

## 核心循环（务必遵守）

1. **打开**：`agent-browser open <url>`
2. **快照**：`agent-browser snapshot -i`，得到元素引用，如 `@e1 [input] "Email"`、`@e2 [button] "Submit"`
3. **交互**：用引用点击/填写/选择
4. **再快照**：导航或页面变化后重新获取引用，旧引用可能已失效

```bash
agent-browser open https://example.com/form
agent-browser snapshot -i
agent-browser fill @e1 "user@example.com"
agent-browser fill @e2 "password"
agent-browser click @e3
agent-browser wait --load networkidle
agent-browser snapshot -i
```

## 常用命令

- 点击：`click @e`；双击 `click @e --twice`；悬停 `hover @e`。
- 输入：`fill @e "text"`；逐键并回车 `type @e "text" --submit`。
- 下拉：`select @e --value "option"`。
- 按键：`press Enter` / `press Control+a`。
- 等待：`wait --load load|networkidle`、`wait --selector "#id"`、`wait 1000`。
- 滚动：`scroll down 3`、`scroll @e into-view`。
- 执行脚本（兜底）：`eval "document.title"`。
- 截图：`screenshot -f shot.png`；保存 PDF：`pdf -f page.pdf`。

## 链式与多标签

- 命令可在一次调用中用 `&&` 串联，后台守护进程保持浏览器状态。
- 多标签：`tabs`、`new-tab <url>`、`switch-tab @t`、`close-tab`。
- 导航：`back`、`forward`、`reload`。

## 下载与文件

- 触发下载后：`wait-download`，完成后给出文件路径；不要把 `.crdownload` 临时文件当结果。
- 上传：`set-input-files @e /abs/path`。

## 原则

- 优先用快照引用，不要凭坐标或可见文本猜测；引用失效（stale）就重新快照。
- 仅在普通命令无法表达时才用 `eval`，且保持只读、不绕过登录与安全校验。
- 遇到登录、验证码、人机校验时停下请用户接管，不要尝试绕过。
- 抓取数据要核对页面真实内容，不得用缓存或记忆代替。
