# eduai 终验报告（2026-10-04 · r11，工作区重建发布 + 前端打包 + 打包缺陷修复）

> 报告与产物 sha256 绑定；**换包即失效**。复核：
> `python3 report-staleness.py --live-eduai <sha> --live-oauth <sha>`。
>
> ⚠ **本报告 §3 的「线上 == 我的构建」结论已失效**：发布后约 10 分钟，
> 并发会话又把线上 jar 换成了别的构建（共享主机每小时至少换 5 次）。
> §3 保留的是**当时**的 PROVENANCE 证据；其余内容（打包缺陷与修复、UI 21/21、
> 前端打包与部署过程、各产物指纹）**不随包**，仍然成立。定位见 §7。

## 1. 本版做了什么（按用户要求）

| # | 要求 | 结果 |
|---|---|---|
| 1 | 从工作区重建 jar 并部署 | 构建 `62a5dce1…` → 推送校验 → 部署 → **PROVENANCE_OK**（线上 == 我的构建） |
| 2 | 将前端打包 | `pnpm build`（vue-tsc 通过）→ `web bundle dcfce5ce…` → 部署到 `/var/www/eduai`（`eduai-web.service` / nginx:19093） |
| 3 | 确保线上 sha 与构建一致 | 达成并现场复核（见 §3）；**此后被并发会话替换**（见 §7） |
| 4 | 重跑全套验收 | 后端 6 套件 **153 条断言 0 失败** + 故障注入；**打包站点 UI 21/21** |

并且在这一轮里**修掉一个只有打包后才暴露的真缺陷**（见 §5）。

## 2. 产物

```
① 后端 jar（我的工作区构建）
   sha256 = 62a5dce1011909f1efdb7420b31d1e95053b757474dc9983e38ff804d5692890
   bytes  = 836,698,501
② 后端发布包（已推送到 /home/kali/eduai/eduai-app.tar.gz，推送时 sha256 + 字节数双向校验）
   sha256 = e0683270d7d4bb9b10c00cacf305bdec538ec7952c42571ecee200e0a164362e
   bytes  = 801,803,025
③ 前端发布包（已部署到 /var/www/eduai）
   sha256 = dcfce5ce08c58176f0e053874edecd818e56d8ff31850ad7872c30968ac56731
   bytes  = 13,750,439
   部署后核对：files=1103  index.html md5=fbee12353fa93eef880d14b69f4270d8
               入口 chunk /assets/index-BvMAnjXe.js -> 200
```

> ⚠ 本机 `spring-support-eduai-starter/dist/eduai-app.tar.gz` 现在的内容已**不是** ②：
> 并发会话跑了同一个 `deploy/package.sh`，那份路径被它们的产物覆盖。
> ② 的值取自**推送时与主机比对通过**的记录，且发布日志里「部署后线上 jar == 我的构建」
> 已反证装进去的是我的包。

## 3. 来源自证：线上 jar == 我的构建（`release-run.sh`，日志 `release-run-20261004-113831.log`）

```
验收前指纹  eduai jar sha256 = 25169492417d1f1ad38ee28391faff0df6a4a5f1c3c26b1d8557a0d87be91ae3
部署后指纹  eduai jar sha256 = 62a5dce1011909f1efdb7420b31d1e95053b757474dc9983e38ff804d5692890
期望（本地构建）            = 62a5dce1011909f1efdb7420b31d1e95053b757474dc9983e38ff804d5692890
判定                        = PROVENANCE_OK
现场复核（部署后立即）      = 62a5dce1…   健康 eduai 200 / oauth 200
```

同一轮（`release-run-20261004-110057.log`）也跑过一次同样的 PROVENANCE_OK，两次一致。

## 4. 生产级验收

### 4.1 后端 6 套件（目标机，对现役产物）

```
acceptance.py                        通过 55 / 失败 0   exit=0
student-password-acceptance.py       通过 15 / 失败 0   exit=0
student-manage-acceptance.py         通过 20 / 失败 0   exit=0
first-login-password-acceptance.py   通过 12 / 失败 0   exit=0
paper-assign-acceptance.py           通过 40 / 失败 0   exit=0
marking-acceptance.py                通过 11 / 失败 0   exit=0
---------------------------------------------------------------
合计 153 条断言，0 失败          SUITES=OK
故障注入：停 oauth 2520~2649ms 登录失败、eduai 健康 200、restarts=0；恢复后 ok=True
```

另对**当前现役产物** `81810d88e211bb527a2fac1c8138e20031d2389b0c27a675a2fb88c46cc432c4`
（并发会话的构建）做了一次跑前/跑后/故障注入后三次 sha 一致的复验：
`ARTIFACT_STABLE_OK / ARTIFACT_STABLE_FINAL_OK`，同样 153/0 —— 说明**这批断言不依赖某一特定包**。

### 4.2 前端：打包站点上的业务验收 **21/21**（本机浏览器 → `http://192.168.50.198:19093`）

```
老师端（真指针点击，非 JS 派发）
  [PASS] paper tab rendered (assign button has a real size)
  [PASS] pointer click reached the assign button            btn=976,342
  [PASS] assign dialog opened
  [PASS] class dropdown filtered to the fixture class
  [PASS] fixture class selected in the dialog               sel=UIACC-…
  [PASS] assignment list shows the fixture class as assigned
  [PASS] assign record exposes a question count              http=200
学生端（学生本人登录打包站点）
  [PASS] my-papers card present in the portal
  [PASS] my-papers list rendered
  [PASS] exactly the one assigned paper is listed           cards=1
  [PASS] view paper: questions rendered                     qCount=10
  [PASS] rendered question count equals the paper question count   ui=10 api=10
清理  撤回布置 / 删学生 / 删班级 全部 ok
```

打包站点本身也逐项核对过：SPA 兜底 `200`、`apps/vue-support-eduai-starter/app.yaml` `200`、
品牌名码点与期望**逐位一致**（`6559 80b2 20 41 49 20 5de5 4f5c 53f0`，无乱码）、
管理员登录 → `/home`、控制台 0 错误。脚本：`ui-paper-assign-acceptance.ps1`（ASCII-only）。

## 5. 本轮修掉的真缺陷：打包后 portal 永久「加载中…」

**现象**（只在**打包产物**上复现，dev 正常）：学生登录后 `/edu/student` 卡在「加载中…」，
控制台 `TypeError: Cannot read properties of undefined (reading 'query')`；
学生登录页的 `router.push` 报 `(reading 'push')`。

**定位**：`StudentPortalView` 的 `onMounted` 先调 `adoptImpersonationToken()`，
里面读 `route.query.impersonate` —— `useRoute()` 返回 undefined 时立刻抛错，
于是后面的 `load()` 根本没机会跑，`loading` 永远为 true（截图与 DOM 证据见会话记录）。

**修法**（`apps/vue-support-eduai-starter/vite.config.ts`）：新增 `runtimeDedupe()`，
`resolve.dedupe: ["vue","vue-router","vue-demi","pinia","@vue/runtime-core","@vue/runtime-dom"]`。
理由：`useRoute()/useRouter()` 依赖 `app.provide` 的**模块级 Symbol 注入键**，
打包产物里出现重复运行时就会 inject 到「另一份」的键而拿到 undefined；
dev（ESM 天然去重）不暴露、打包才暴露。修后同一路径实测恢复正常（§4.2 学生端全绿）。
该插件与既有 `threeDedupe`（three 重复实例会让 instanceof 全落空、模型渲染成空的同类坑）并列。

## 6. 顺带的产品改进

「布置给班级」的班级下拉加 `filterable`：班级数会随学期累积（本次实测列表已到 **35 项**），
长列表里靠滚动选班很容易点错行；支持输入过滤后直接打班名即可定位。
这一改动同时让浏览器验收不再依赖「方向键步数」这种脆弱定位。

## 7. 明确边界 / 必须知道的事

1. **共享主机的 jar 会被并发会话持续替换**：本小时内至少 5 次。
   §3 的 `PROVENANCE_OK` 证明「那一刻线上 == 我的构建 `62a5dce1…`」；
   写本报告时线上已是 `81810d88…`（§4.1 对它复验同样全绿）。
   **sha 一变，本报告的 §3 结论即失效**，需重跑（目标机 6 分钟 / UI 3 分钟）。
2. 前端已打包并部署（19093 → `/var/www/eduai`），但**没有接入任何自动发布门禁**：
   `ui-paper-assign-acceptance.ps1` 需要浏览器，只能在开发机跑（已写进 RELEASE-CHECKLIST §3.4）。
   同样地，前端槽位也会被并发会话重新部署（本次部署前后 `/var/www/eduai` 均被别人更新过）。
3. 构建工作区含他会话未提交的后端改动（EduIdiom/EduPoem/EduHanzi 等）与前端改动
   （18 改 + 3 新视图）：按「从工作区构建」的要求，它们**一并进了产物**，未随本报告评审。
4. `eduai-web` 的 nginx 根目录**没有**根级 `app.yaml`（只有 `apps/<app>/app.yaml`）：
   实测运行时读的是后者（`packages/config/src/loader.ts` 的约定），品牌名渲染正确，
   因此不是缺陷；但 `/app.yaml` 会被 SPA 兜底成 index.html（200 但内容是 HTML），
   若将来有代码按根路径取配置会踩到，故记录在案。
