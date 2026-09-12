# AiGateway E2E 验收（Playwright）

生产级 API 验收，覆盖：
- **A 插件市场前后端对接**（`/api/skills*`）：市场源配置、已安装技能、排行同步、市场启停、内容预览降级、删除幂等
- **B Bot 绑定 + IM 链路**（`/v2/ai/bot-binding*`）：创建 → 列表 → models/config → **messages 分页** → runtime-status → polish/summarize → commands/contacts → 清理
- **C 基础协议面**：OpenAI 协议鉴权（无/假 Key 拒绝 401）、本地信任头放行、会话 CRUD

## 运行

```bash
# 从本目录执行
npx playwright test --config playwright.config.ts
```

`webServer` 自动拉起本地 AI 网关（`start-gateway.cmd`），就绪后执行测试，结束后自动回收进程。
若 9898 已有一个网关在跑（`reuseExistingServer: true`），直接复用不重复启动。

## 前置要求

- 网关可部署产物位于 `ai-exploded`（BOOT-INF/classes + BOOT-INF/lib），默认路径见 `start-gateway.cmd`
- H2 测试库与 schema 脚本就绪（默认路径同上，可用 `AI_GATEWAY_*` 环境变量覆盖）
- 网络可达 SkillsMP（A03 排行同步依赖外网；不可达时后端返回诚实空态，测试断言 source_status 存在）

## 环境变量

| 变量 | 作用 |
|---|---|
| `AI_GATEWAY_BASE` | 网关地址（默认 `http://127.0.0.1:9898`） |
| `AI_GATEWAY_JAVA` / `AI_GATEWAY_CLASSES` / `AI_GATEWAY_LIB` / `AI_GATEWAY_H2` / `AI_GATEWAY_DB` / `AI_GATEWAY_SCHEMA` | 覆盖网关启动路径 |

## 验收标准

- 全部断言基于真实后端真实数据往返（非 mock）
- 测试自带清理：B 组删除创建的 Bot 绑定、C 组删除创建的会话，不污染数据
- 鉴权边界显式验证：协议面无 Key 401，本地信任头放行
