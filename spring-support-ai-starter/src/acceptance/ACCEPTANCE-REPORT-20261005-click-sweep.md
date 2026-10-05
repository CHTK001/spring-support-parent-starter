# 生产级交付验收报告 · AI 门户（vue-support-ai-guada + spring-support-ai-starter）

- 日期：2026-10-05
- 交付环境：`192.168.50.198:8787`（jar sha256 前 16 位 `1f3643bf17e74a7a`，`jar-watch.expected` 双向一致，`systemctl is-active=active`）
- 前端：本机 `pnpm dev`（15689），未打包
- 数据库：MySQL `ai_test`（非 H2）
- 豁免：多浏览器兼容性（按约定不测）

---

## 1. 验收范围：全站每一个可点击元素

按 `main.js` 注册的**全部 24 个路由**铺开，把每个页面上每一个可点元素
（`button` / `[role=button]` / `.el-button` / `.re-button` / `.rs-btn` / `a[href]` /
`[class*=icon-btn]`）逐个断言四件事：

1. **可见可点** —— 尺寸非零，未被 `display/visibility/opacity` 隐藏；
2. **有可访问名** —— `aria-label` / `title` / 文字内容三选一；
3. **点得动** —— 非 disabled 挡死、无遮挡（命中测试 + 必要时 force 兜底并单列）；
4. **点完不炸** —— 无 `pageerror`、无控制台错误、无 5xx、无失败请求。

验收器：`apps/vue-support-ai-guada/src/acceptance/ui-click-sweep.cjs`（长期资产，可重复执行）

### 结果

```
可点元素总数 913
  ok               353   点击成功且有可观测副作用
  dup-chrome       156   侧栏/标题栏常驻元素（跨页同一批，只扫一次）
  hidden           387   采集时不可见（折叠、未激活 tab 等）
  disabled          14   本身处于禁用态
  state-dependent    2   点击前自行隐藏（正常 UI 行为，见 §4）
  FAIL               0
```

**路由加载期错误：0 个**（此前每页必有的 404、酒馆 500、AppKey 500 全部消除）。

---

## 2. 本轮修复的真实缺陷

全站遍历暴露出 **2 个页面级 500、3 类必然 404、1 处整页刷新跳转、12 个无名图标按钮**。

### 2.1 AppKey 管理页 500（`NumberFormatException: For input string: "agnes"`）

根因是**两个同名实体**。项目里同时存在
`entity.settings.SysAiProvider` 与 `manage.store.SysAiProvider`；
前者把 `providerId` 声明成 `Long` 且未加 `@TableField`，
而真实主键列 `sys_ai_provider_id` 是 `varchar(64)`，存的是
`openai` / `agnes` / `onnx` 这类**编码**。AppKey 链路恰好用了错的那份，
`providerMapper.selectList(null)` 一读就抛。

处理：
- `AiAppKeyServiceImpl` 改用 `manage.mapper.SysAiProviderMapper` + `manage.store.SysAiProvider`
  （`providerId` 是 `String`，字段名是 `getName()` 而非 `getProviderName()`）；
- `providerId` 在 `SysAiAppKey` / `AiAppKeyDTO` 上由 `Long` 改为 `String`
  ——前端提交的就是字符串编码，库里其它 provider 引用列也全是 varchar；
- 删除无引用的 `entity.settings.SysAiProvider`、`mapper.settings.AiProviderMapper`，
  以及同样重复的 `entity.settings.SysAiAppKey`、`mapper.settings.AiAppKeyMapper`，
  免得再被接回去。

### 2.2 酒馆页 500（`Table 'ai_test.sys_ai_tavern_group' doesn't exist`）

三个实体（group / member / message）都在线，表却从未建过，init 脚本里也没有。
新增 `V1.2__fix_appkey_provider_and_add_tavern_tables.sql`（`CREATE TABLE IF NOT EXISTS`），
并把 `sys_ai_app_key_provider_id` 在 `V1.0` 里改成 `VARCHAR(64)`，使全新部署不再缺表。

迁移机制实测有效：V1.2 在服务启动时自动应用，`sys_database_version` 记录 `success=true`。

### 2.3 三类必然 404（靠 try/catch 兜底掩盖，功能从未工作过）

| 端点 | 触发时机 | 后端实况 |
|---|---|---|
| `/v2/mcp-servers`（8 个方法） | 打开角色设置面板 | 路径从不存在；真端点是 `/api/mcp/servers` |
| `/api/sessions/{id}/agents`、`/sub-sessions` | **每次**展开会话、每次打开会话 | 后端整套未实现（无任何 `@RequestMapping("/api/sessions")`，库里也无绑定表） |
| `/v2/plugins/attachment-types` 等 6 个 | 每次打开会话、点开附件面板、进生图页 | 插件运行时未实现（插件市场页实际用 `/v2/plugins-config`） |

处理：
- `mcp.api.ts` 改为委托给已验证可用的 `javaApi.ts` 客户端，并把 `{success,data}`
  归一成调用方期望的 `{items,total}`（否则 `response.items.filter` 直接抛）；
  `refreshMcpTools` 后端确实无此端点，改为**明确报错**而非静默 404。
- 会话智能体、插件运行时两组调用**移除**，并在 `javaApi.ts` 写明契约与原因，
  后端补齐后按注释接回即可。

### 2.4 连接器「进入聊天」整页刷新

`window.location.href = ...` 会让整个 SPA 冷启动（该应用冷启数十秒）并丢掉全部状态，
还绕开 `router.beforeEach`。改为 `router.push`。

### 2.5 12 个无名图标按钮（可访问性）

侧栏折叠、聊天工作区、更多操作、会话输入区的
附件 / 运行模式 / 技能 / MCP / 深度思考 / 在线检索 / 优化 / 停止 / 发送、
生图发送、知识库问答提交。
开关类同时补 `aria-pressed`，纯 `div` 按钮补 `role` / `tabindex` / 键盘响应。

---

## 3. 后端回归（原有交付项）

| 项 | 结果 |
|---|---|
| `kb-accept-backend.sh` 验收套件 | **PASS=21 FAIL=0 / ALL_PASS** |
| `/v2/rag/status` | `available=true`、`embeddingReady=true` |
| 模型→引擎推导 | `embedding_model=bge-large-zh-embedding`、`embedding_engine=onnx`（自动推导） |
| 设置项暴露面 | `/v2/settings/rag` 中 `rag_embedding_model` 匹配数 **0**（只选模型、无引擎项） |
| 问答引用 | `citations=1`，答案带上下文依据 |
| 人脸 / 以图搜图 | 均 200 |
| 按 userId 隔离 | 已在上一轮跨账号实测通过，遗留共享索引已退役 |

---

## 4. 遗留项（如实记录，非本次交付阻塞）

1. **会话↔智能体绑定 / 主从子会话**：后端完全未实现。已停止发必然失败的请求，
   功能本身不存在。属于**新功能**范畴，需要建绑定表 + 6 个端点 + 事件广播，
   不在本次「修复既有缺陷」范围内。
2. **插件运行时（attachment-types / ui-pages / plugin data）**：同上，后端未实现。
3. **人脸超分辨率（gfpgan）**：模型未部署、开关默认关闭、UI 未暴露任何操作入口，
   用户不可达。日志里那条 DJL 加载失败是早期手工测试留下的。
4. **顶栏天气**：无外网 + 无地理定位，`/weather-api/v1/forecast` 404。
   该组件本就设计为「失败静默的增强信息」，不影响功能。
5. **2 个 `state-dependent` 按钮**：知识库问答面板的快捷提问带 `v-if="!hasTurns"`，
   一旦提问即消失——采集时可见、点击时不可见属正常 UI 行为，非坏按钮。
6. **磁盘**：根分区 94% → **88%**（清理 7 份过期 jar 备份，保留最新 3 份 +
   数据备份目录；`backup` 5.5G→1.7G）。后续每次构建产 590MB jar，仍需关注。

---

## 5. 复现方式

```powershell
# 前端遍历（需本机已 pnpm dev 起 ai-guada，默认 15689）
$env:NODE_PATH = "D:\ch\project\vue-support-parent-starter\node_modules"
cd D:\ch\project\vue-support-parent-starter\apps\vue-support-ai-guada
node src\acceptance\ui-click-sweep.cjs          # 全量
node src\acceptance\ui-click-sweep.cjs /models  # 只跑某几个路由
# 退出码 0 = 无 FAIL；报告落在 .ui-click-sweep/report.json

# 后端套件（服务器上）
bash /home/kali/kb-accept-backend.sh
```

> 注意：**遍历期间不要改前端源码**。dev server 的 HMR 会重载模块，
> 上一轮曾因此让 18 个连续路由只渲染出 1 个可点元素，整轮结果作废。