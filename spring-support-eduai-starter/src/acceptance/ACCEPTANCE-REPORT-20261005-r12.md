# eduai 终验报告（2026-10-05 · r12，前端交付物补齐 + 当前状态全量验收）

> 报告与产物 sha256 绑定；**换包即失效**。复核：
> `python3 report-staleness.py --live-eduai <sha> --live-oauth <sha>`。

## 1. 本版补的是什么（上一版遗留的交付缺口）

r11 之后做完成度审计，发现**前端没有可复现的交付物**：后端早有
`spring-support-eduai-starter/deploy/`（package/deploy/README），前端却一直靠会话里的临时命令
打包部署 —— 换台机器就复现不了，等于「前端交付不可复现」。本版把它补成与后端同一形态：

```
apps/vue-support-eduai-starter/deploy/
  package.sh   开发机：pnpm build（含 vue-tsc 类型门禁）→ 打 dist 成 eduai-web.tar.gz
               打印 bundle sha256 / 文件数 / **dist/index.html 的 md5**（第 4 步要做来源自证）
  package.ps1  同上，Windows 版（ASCII-only，仓库规约：5.1 会按 ANSI 解析非 ASCII 脚本）
  deploy.sh    目标机：备份 → staging → **原子 rename 换目录** → 重载 nginx → 自检
  verify.sh    目标机：来源自证 —— 线上站点 == 本地构建的 dist（判据见下）
  README.md    一次完整交付的五步命令
```

两个刻意的设计决定：

1. **原子换目录而不是就地覆盖**：就地解包会有一段「index.html 已换、assets 还没换」的窗口，
   用户正好在那一刻刷新就是 404。rename 没有这个窗口（并且旧目录被备份成可回滚的 tar）。
2. **部署脚本单独传，不放进 web bundle**：web root 是公开目录，把 `deploy/*.sh`
   打进去会被 HTTP 直接下载。README 与 RELEASE-CHECKLIST §2.3 都写明了这点。

### 前端「来源自证」的判据

后端 PROVENANCE 比的是 jar sha256；前端没有单一 jar，所以比**构建产物的内容指纹**：

```
index.html 的 md5     构建时把 asset 名写死进 index.html，换构建必变（本次 6730b738…）
入口 chunk 可取到     从 index.html 里解出 /assets/index-*.js 并请求，必须 200
app.yaml 内容正确     运行时读 apps/<appName>/app.yaml；若被 SPA 兜底成 index.html，
                      即使 HTTP 200 也算失败（按磁盘字节数与服务字节数比对）
关键路径 200          / 与 /edu/student-login（SPA 兜底）
```

## 2. 本轮实跑（不是"应该能跑"）

```
package.ps1   → dist/eduai-web.tar.gz  文件数 1104  13.1 MB
                sha256 = a04c40663e2b71b1ba82fd1d9b32d51e260d7ef035688c3531e81fec02e07467
                index.html md5 = 6730b738ee89944840be055c3982781d
                传输到目标机后 sha256 + 字节数逐一比对通过
deploy.sh     → 备份 web-20261004-124824.tar.gz(13,940,140 B) → staged 1104 →
                原子替换 → nginx 重载 → 自检（index md5 与本地一致、5 条路径全 200）
verify.sh     → INDEX_MD5_MATCH  WEB_PROVENANCE_OK  文件数=1104  index.md5=6730b738…
                exit=0
```

## 3. 当前状态的验收快照（对现役产物）

### 3.1 后端 6 套件（目标机，跑前/跑后/故障注入后 sha 三次一致）

```
现役 jar sha256 = 81810d88e211bb527a2fac1c8138e20031d2389b0c27a675a2fb88c46cc432c4
oauth jar sha256 = ca4ce0b3d929c96e6f607950c24ac235a7b69e5266ef23df63ac6e88c6c6a470

acceptance.py                        通过 55 / 失败 0   exit=0
student-password-acceptance.py       通过 15 / 失败 0   exit=0
student-manage-acceptance.py         通过 20 / 失败 0   exit=0
first-login-password-acceptance.py   通过 12 / 失败 0   exit=0
paper-assign-acceptance.py           通过 40 / 失败 0   exit=0
marking-acceptance.py                通过 11 / 失败 0   exit=0
--------------------------------------------------------------
合计 153 条断言 0 失败      ARTIFACT_STABLE_OK / ARTIFACT_STABLE_FINAL_OK
故障注入：停 oauth 2522ms 登录失败、eduai 健康 200、restarts=0；恢复后 ok=True
表数 eduai/oauth = 107 / 72
```

> 这个 jar **不是我的构建**（并发会话从含我全部提交的 HEAD 构建）。
> 我对它做的是「产物绑定 + 全量复验」，不是 PROVENANCE。

### 3.2 前端：打包站点上的浏览器业务验收 21/21

```
pwsh -File ui-paper-assign-acceptance.ps1 -Base http://192.168.50.198:19093
  [PASS] pointer click reached the assign button / assign dialog opened
  [PASS] class dropdown filtered to the fixture class / fixture class selected
  [PASS] assignment list shows the fixture class as assigned
  [PASS] my-papers card present / list rendered / exactly one paper
  [PASS] view paper: questions rendered            qCount=10
  [PASS] rendered question count equals the paper question count   ui=10 api=10
  ===== passed 21 / failed 0 =====   （夹具自建自删，截图落 %TEMP%）
```

## 4. 顺带修掉的验收工具自身问题

`ui-paper-assign-acceptance.ps1` 原先把截图写到 `$PSScriptRoot`，会在仓库验收目录里
丢下 3 个 PNG（换机器看是「来路不明的散落文件」）。现加 `-OutDir`，**默认落系统临时目录**，
并删除了那 3 个散落文件。

## 5. 明确边界

1. **共享主机上 jar 与 web root 都会被并发会话替换**。本报告 §3.1 绑定的
   `81810d88…` 与 §2/3.2 的前端 `index.html md5=6730b738…` 都只在**当时**成立；
   任一变化即需重跑（后端 6 套件约 6 分钟 / 前端 package+deploy+verify 约 3 分钟 / UI 约 3 分钟）。
2. **前端部署未接入自动门禁**：`ui-paper-assign-acceptance.ps1` 需要真实浏览器，
   只能在开发机跑；目标机上能自动跑的只有 `verify.sh`（来源自证）与 HTTP 检查。
3. 本次前端产物仍是**从工作区**构建：工作区含他会话未提交的前端改动
   （18 改 + 3 新视图等），一并进了产物，未随本报告评审。
