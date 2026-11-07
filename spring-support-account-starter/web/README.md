# 账号管理前端（`@account/web`）

本目录是 `spring-support-account-starter` 的**前端源码**。账号管理与模板管理已经合并成
同一个项目：

- 后端：`../src/main/java`（`com.chua.starter.account.*` + `com.chua.starter.template.support.*`）
- 前端：本目录（账号资产 + 模板管理 + 消息模板）

宿主（monitor / system）通过 `@account/web` 直接取页面源码，不再各自维护一份模板库代码。

## 目录

| 目录 | 内容 |
| --- | --- |
| `src/account/` | 账号资产页（`ScAccountAssets.vue`）、账号表单、AppKey / Cookie 弹窗、账号 API |
| `src/template/` | 模板管理（`TemplateView.vue`）、设计器、预览、打印机、调用日志、消息模板 |
| `src/shared/` | 两个域共用的小工具（`confirmThen`、`clipboard`） |
| `app.yaml` | 独立运行时的应用配置（标题、`openAuth: false`、`/api/template` 前缀） |

## 运行

本目录是 `vue-support-parent-starter` 的**外部 workspace 成员**（见那边的
`pnpm-workspace.yaml`），依赖版本统一写 `catalog:`。因此 `pnpm install` 与脚本都要
通过 workspace 根执行：

```powershell
# 安装依赖（在 vue-support-parent-starter 根目录）
cd D:\ch\project\vue-support-parent-starter
pnpm install

# 开发（默认 19093，代理到 127.0.0.1:8082）
pnpm --filter @account/web dev

# 类型检查（等价于本包的 pnpm build）
pnpm --filter @account/web exec vue-tsc --noEmit
```

不要在本目录直接 `pnpm dev`：本目录向上找不到 `pnpm-workspace.yaml`，pnpm 会先做一次
依赖校验并对 `catalog:` 报
`ERR_PNPM_CATALOG_ENTRY_NOT_FOUND_FOR_SPEC`。需要单独跑时用 workspace 根的
`pnpm --filter @account/web dev`。

后端地址用环境变量覆盖（代理三路：`/api/accounts`、`/api/template`、`/api`）：

```powershell
$env:ACCOUNT_API_TARGET = "http://192.168.1.10:8082"
pnpm --filter @account/web dev
```

## 与宿主的接线

monitor 侧已经接好，关键点：

- `package.json`：`"@account/web": "workspace:*"`
- `vite.config.ts`：alias `@account/web` → 本目录，并把本目录加进 `server.fs.allow`
- `tsconfig.json`：`@account/web` 的 paths，`include` 里补本目录的 `*.d.ts`
  （`template/types/hiprint.d.ts` 声明了 `jquery` / `vue-plugin-hiprint` / `window.hiprint`）
- 路由：`/account-assets` 挂 `ScAccountAssets.vue`，并把 `templateChildRoutes`
  （设计器 / 预览 / 打印机 / 调用日志）以隐藏路由合并进去
