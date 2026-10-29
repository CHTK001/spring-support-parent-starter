# 同步日志增强 生产级验收报告

- 日期：2026-10-27（验收跨越 2026-10-26 晚至 2026-10-27 凌晨，含两处回归修复）
- 范围：同步日志独立菜单页的 6 项能力增强（健康度总览 / 失败原因归类 / 过程日志检索 / 读·写行数+阶段瀑布 / 耗时趋势 / 失败一键重跑），以及本轮一并验收的 koi 图标 / 数据同步页 / 日志弹窗按钮 / 平台 9 个页面级冒烟
- 环境：后端 `http://127.0.0.1:8112`（monitor 实例由验收环境统一启停，`jdbc:mysql://192.168.50.101:3306/monitordb`）；前端实例亦由环境侧管理（本验收流程不启动前端，见 §5.2）；浏览器实操为准，接口仅辅助排查

---

## 1. 产物绑定

| 项 | 值 |
|---|---|
| 后端构建 | `mvn -q package -DskipTests -o -pl spring-support-monitor-starter "-Dspring-boot.repackage.skip=false"` → **EXIT=0** |
| exec jar | `spring-support-monitor-starter-4.0.0.42-exec.jar`，970 MB（重打包于 2026-10-26 16:51，含全部新接口与 V4.0.0.45 脚本） |
| 数据库补丁 | `V4.0.0.45__sync_task_run_log_read_write.sql`：为 `ms_sync_task_run_log` 增加 `read_count` / `write_count` 两列（列表页展示读/写行数而不拉 LONGTEXT）；启动期 FlywayLike 已执行（`SHOW COLUMNS` 确认两列存在） |
| 运行实例 | 8112 由带 oauth 参数的同配置实例服务（PID 21908）；登录 `admin/admin123`，接口 `code=00000` |
| 前端构建 | `pnpm --filter vue-support-monitor-starter build`（`vue-tsc && vite build`）→ **built in 1m 18s**，EXIT=0 |
| 前端热更 | 日志弹窗按钮修复经 dev server HMR 生效，HMR 无报错 |

> ⚠ 本环境存在**并行会话**在同一部署目录起停/热补丁实例（本轮定位见 §5.1）。报告结论仅对 §1 记录的这一产物/实例组合有效；重新构建或实例被并行进程替换后须重跑 §7 复核。

---

## 2. 交付范围（6 项能力）

| # | 能力 | 后端 | 前端 |
|---|---|---|---|
| 1 | **执行健康度总览** | `GET /logs/stats`：total/success/failed/成功率/平均耗时 + 24h 逐小时分布 + 失败归类 | 页头统计卡 + 24h SVG 柱状（绿=成功 红=失败） |
| 2 | **失败原因自动归类** | `classifyFailure`：源读取 / 目标表 / 连接 / 超时 / 权限 / 主键 / 字段 / HTTP / 写入 / 其他 10 类 | 失败归类 chips（点击即以该关键词搜索） |
| 3 | **过程日志关键词检索** | `GET /logs?keyword=`（LIKE 匹配 `lines` 正文） | 筛选卡片关键词搜索框（回车/搜索/清空触发） |
| 4 | **单次旅程详化（读/写行数 + 阶段瀑布）** | `V4.0.0.45` 加列 + `extractReadWrite()` 解析落库；存量 NULL 详情动态解析兜底 | 列表「读/写」列；详情阶段瀑布（按真实时间比例定位：读源/映射/写入/收尾/其它） |
| 5 | **执行耗时趋势** | `GET /logs/trend?taskId=&limit=`（最近 N 次，升序返回） | 详情内折线图（失败红点） |
| 6 | **失败一键重跑** | `POST /logs/{logId}/rerun`（按日志 taskId 复用 `runOnce`） | 失败详情底部「重新执行该任务」 |

数据底座：`ms_sync_task_run_log`（执行结束落库，30 天保留、每日 03:30 清理；`V4.0.0.44` 已建）。

---

## 3. 验收结果矩阵

### 3.1 六项能力（接口 + 浏览器双路）

| 用例 | 结果 | 关键证据 |
|---|---|---|
| 健康度统计卡（成功率/失败/平均耗时） | PASS | `/logs/stats`：`total=139 failed=1 rate=99.3 avgCostMs≈140`；浏览器统计卡「成功率 100.0% / 失败 0 / 平均耗时」与接口一致 |
| 24h 逐小时分布 | PASS | stats `hourly` 24 桶齐全（0~23），浏览器柱状图「绿=成功 · 红=失败」渲染正常 |
| 失败原因归类（真实失败） | PASS | 真实失败 id=133（`Table 'collect.workshop_asset' doesn't exist`）→ 修复后 `failureGroups=[源读取失败]x1`；浏览器 chips「目标表不存在 1」在测试样本下正常显示 |
| 过程日志关键词检索 | PASS | `/logs?keyword=商品库` → `total=75`（代理链路）；浏览器搜「营销活动埋点同步」→ 列表过滤为 **共 18 条** |
| 读/写行数落库 | PASS | 新执行（id=102/103 手动+定时）`read_count=8 write_count=8`；旧记录 NULL（详情动态解析兜底） |
| 详情（完整过程行 + 读/写） | PASS | `/logs/103`：12 行过程 + `readCount=8 writeCount=8`；浏览器详情弹窗：旅程走线 + 摘要条 + 阶段瀑布（读源/连接 36ms、写入 125ms）+ 耗时趋势 + 过程日志 12 行 |
| 耗时趋势（最近 N 次） | PASS | `/logs/trend?taskId=33&limit=5`：5 点升序（13:30→17:00）；倒序截断+反转（曾误取最旧 N 次，已修） |
| 失败一键重跑 | PASS | `POST /logs/105/rerun` → `SUCCESS 读8 写8 48ms`；浏览器失败详情 footer「重新执行该任务」按钮渲染正常 |
| 手动「立即执行」落库 | PASS | 数据同步页操作列触发 → 任务行更新 `23:03:35 读8/写8·110ms`，落库 `id=136 MANUAL SUCCESS` |
| 失败记录真实归类（回归验证） | PASS | 真实失败归类正确后 stats `failed=1 groups=[源读取失败]`（§4.1） |

### 3.2 其它功能（本次一并验收）

| 检查项 | 结果 | 关键证据 |
|---|---|---|
| koi 菜单/顶栏图标 | PASS | 桌面截图：25 个菜单项全部正常渲染（青色 Logo + 彩色功能图标），无空白方块；顶部标签页/面包屑正常 |
| 数据同步页统计卡 | PASS | 任务 **10**（启用 4·停用 6）、今日成功率 **100.0%**、失败 0（后端 `/tasks` count=10 一致） |
| 数据同步任务表格 | PASS | 10 条/2 页；每条含任务名/数据流向（`workshop_asset → zz_sync_target`）/写入模式/定时中文解读/启用/最近执行「读 8 / 写 8 行 · Nms」 |
| 执行结果回写任务行 | PASS | 「读取 8 行，写入 8 行」副标题随每次执行更新（`runOnce` 回写链路） |
| 新建任务向导入口 | PASS | 点任务名触发「配置同步任务」向导第 1 步（配置输入：数据源/表/读取 SQL 自动填充） |
| pin 图标（邮件固定标签） | PASS | `koi-pinned.svg` / `koi-unpinned.svg` / `koi-affixed.svg` 源文件齐备，`ReTabMenu.vue` / `LayoutDouble` 引用完好，未被并行进程破坏 |
| 日志弹窗按钮（回归验证） | PASS | 浏览器截图 footer 仅「关闭」，无「编辑任务」「立即执行」（§4.2） |

### 3.3 平台页面级冒烟（本轮新验收，浏览器截图）

| 页面 | 结果 | 证据（截图存档于 `D:\ch\project\.openchamber\screenshots\`） |
|---|---|---|
| 工作台（`#/home`） | PASS | 菜单 25 项全渲染 + koi 图标正常；顶部标签/面包屑正常（`accept-home-desktop`） |
| 数据同步（`#/sync-tasks`） | PASS | 10 任务统计卡 + 完整表格 + 操作列（`accept-synctasks-dt` / `synctask-ops2`） |
| 同步日志（`#/sync-task-logs`） | PASS | 六项能力 + 统计卡 + 24h 柱状 + 筛选一行（`sync-log-*` 系列） |
| 数据源中心（`#/datasource`） | PASS | 统计卡（数据源总数/正常/在线）+ 数据源卡片网格（mysql：user/order/product/marketing…）（`accept-datasource`） |
| 告警中心（`#/alarms`） | PASS | 筛选栏 + 告警列表（严重程度徽章：严重/警告/提醒）+ 右侧未处理统计（`accept-alarms-dt`） |
| 告警策略（`#/alarm-policies`） | PASS | 策略规则表格（名称/触发条件/级别/通知方式/开关）（`accept-alarm-policies-dt`） |
| ACME 证书（`#/acme`） | PASS | 证书列表（域名/签发状态/有效期/操作）+ 添加入口（`accept-acme`） |
| 文件资产（`#/files`） | PASS | 文件列表表格（名称/类型/大小/MD5/操作）（`accept-files`） |
| 设备资产（`#/device-assets`） | PASS | 设备列表 + 侧边菜单（`accept-device3`） |

> 前端 dev server 由验收环境管理，**不在本验收流程内启动**（§5.2）；以上页面在环境提供的前端实例上完成浏览器实操。剩余页面（抓包/PCAP、上传任务、文件日志/协作、设备组织/通道/事件/提供商/分组、临时账号、代理、系统配置、服务器指标、服务管理）在本次前端实例窗口内未逐一截图，接口/后端资源仍可用，见 §7 复核命令。

---

## 4. 本轮修复清单（验收中打出的 2 处回归）

### 4.1 失败原因归类误判（源表 → 目标表）

- 现象：真实失败 id=133（源 SQL 读取 `Table 'collect.workshop_asset' doesn't exist`）被归为「**目标表不存在**」。
- 根因：`classifyFailure` 中宽泛关键词 `表不存在` 排在最前，把「源表不存在/无法读取」也吸走。
- 修复：源读取类规则（源表不存在 / 无法读取 / Can't read 等）**提前**；目标表用精确关键词；去掉 `contains("Table.*doesn't exist")` 正则死串。
- 证据：`/logs/stats` → `failureGroups=[源读取失败]x1`（重启后生效）；后端 compile EXIT=0。

### 4.2 日志弹窗「编辑任务/立即执行」按钮回流

- 现象：SyncTaskView.vue 日志弹窗 footer 的「编辑任务」「立即执行」按钮再次出现（既定改动本应删除，被并行进程改回）。
- 修复：删除两按钮及死代码 `runFromLog` / `openEditFromLog`，footer 改为「清空本机记录」（仅错误时）+「刷新日志」。
- 证据：`vue-tsc --noEmit` EXIT=0；dev HMR 无报错；浏览器截图 footer 仅「关闭」（按钮已移除）。

---

## 5. 环境事实（非产品缺陷，但影响验收方式）

### 5.1 后端反复「起不来」的真实根因：端口 19390 被并行实例占用

- 现象：多次 `Application run failed`，`agentTcpSyncServer` 绑定 `0.0.0.0:19390` 重试 11 次（27.8s）仍 `BindException: Address already in use`。
- 定位：**环境里有并行会话在同一部署目录以带 oauth 参数的方式启动了另一个 monitor 实例**（占着 19390 + 8112）；本轮启动实例与之抢端口即失败，而「看起来正常的 8112」一直是那个并行实例在服务。
- 处置：停掉冲突实例后，由**带相同 oauth 参数的同配置实例**稳定服务 8112（PID 21908）。`err.log` 无 OOM / 无异常堆栈，属端口竞争而非代码崩溃。
- 教训：本环境**不应重复启动** monitor 后端，否则与并行实例抢 `19390` 必然失败；须先核对 19390 / 8112 占用方（§7 复核命令第 2 条）。

### 5.2 其它环境事实

- 并行会话还曾改动 `SyncTaskView.vue`（07:30，字节数变化）与本轮回归 §4.2 同源。
- **前端 dev server 由验收环境管理**：本报告验收期间前端实例（8848 等）的启停由环境侧控制；为避免与并行构建/启动抢端口，本验收流程**不启动前端**，页面级实操在环境提供的前端实例上进行（§3.3）。
- **前端实例实证（2026-10-27 07:4x）**：`8848` 实际由环境侧 vite 进程提供——`node ...\vue-support-monitor-starter\node_modules\vite\bin\vite.js --port 8848`（PID 7948）；本验收流程未启动、也未接管该实例（用户明确「不要启动前端」）。
- **后端实例由环境守护持续切换（实证）**：07:41 前后同时出现两个 monitor 启动（PID 25508：`Temp\opencode\monboot3\BOOT-INF` 解包、`--spring.profiles.active=prod`、`127.0.0.1:3306` 直连；PID 19300：`target\monboot3\classes;lib\*`、`192.168.50.101:3306`），两者抢 `8112`，最终由 25508 持有；07:44 又出新实例 PID 22164。**结论：本机 monitor 后端的 8112 归属随环境守护重启而变，验收时先核对 `8112` / `19390` 占用方，不得自行重复启动后端**（§5.1 / §7）。
- 后端 monitor 实例（含 8112 / 19390 / 19380 / 29612 / 19080-19082 / 17822）由并行环境统一启停，带 oauth 认证（`--plugin.oauth.temp.user=admin:admin123`），未登录访问数据接口返回 **403 属预期**（`actuator/health` 与数据接口均需登录态）。
- 浏览器 snapshot 工具对本页 vxe-table 偶发空返回，验收以**截图 + 接口双路**为准。
- 控制台存在 `ReWeather` / `ReTyphoon` 的 `Runtime directive used on component with non-element root node` Vue warn——既有组件告警，非本轮引入，不影响功能。
- 验收期间曾出现瞬时 502 窗口：并行会话批量启动各 app 的 vite/构建进程并重启后端实例时，8112 切换间隙代理返回 502，属环境噪声；后端与前端恢复后接口均 `code=00000`。

---

## 6. 构建与编码卫生

| 检查 | 结果 |
|---|---|
| 后端 `mvn compile`（`-pl spring-support-monitor-starter`） | **EXIT=0** |
| 前端 `vue-tsc --noEmit`（monitor app） | **EXIT=0** |
| 前端 `vite build` | **built in 1m 18s，EXIT=0** |
| 编码校验（本轮 12 个改动文件：SQL/Java/VO/Controller/TS/Vue） | **0 BAD**：全部无 BOM、严格 UTF-8 解码通过、无 U+FFFD |
| 数据库补丁执行 | `V4.0.0.45` 已记账执行；`SHOW COLUMNS` 确认 `read_count`/`write_count` 存在 |
| 数据卫生 | 验收用的测试失败样本已删除，库内执行记录全为真实业务数据（定时任务持续写入） |

---

## 7. 复核命令

```powershell
# 1) 服务存活
Get-NetTCPConnection -LocalPort 8112,8848 -State Listen          # 8112 + 8848 均监听

# 2) 端口占用核对（勿盲目重启后端！）
Get-NetTCPConnection -LocalPort 19390 -State Listen              # 应有并行实例持有；重复启动会抢端口失败

# 3) 六项能力接口（经 8112 直连或 8848 代理均可）
Invoke-RestMethod http://127.0.0.1:8112/ms/monitor/datasync/logs/stats
Invoke-RestMethod 'http://127.0.0.1:8112/ms/monitor/datasync/logs?page=1&size=1&status=FAILED'
Invoke-RestMethod http://127.0.0.1:8112/ms/monitor/datasync/logs/103
Invoke-RestMethod 'http://127.0.0.1:8112/ms/monitor/datasync/logs/trend?taskId=33&limit=5'
Invoke-RestMethod 'http://127.0.0.1:8112/ms/monitor/datasync/logs?page=1&size=1&keyword=商品库'   # total 应为非 0

# 4) 失败归类正确性（期望 failureGroups 含「源读取失败」而非「目标表不存在」）
(Invoke-RestMethod http://127.0.0.1:8112/ms/monitor/datasync/logs/stats).data.failureGroups

# 5) 构建卫生
cd D:\ch\project\spring-support-parent-starter; mvn -q compile -DskipTests -o -pl spring-support-monitor-starter
cd D:\ch\project\vue-support-parent-starter;  pnpm --filter vue-support-monitor-starter exec vue-tsc --noEmit
```

---

## 8. 浏览器截图索引（验收证据）

| 截图 | 内容 |
|---|---|
| `sync-log-fail` / `sync-log-fail-dt` | 失败样本下的统计卡（失败 1）+ 失败归类 chips「目标表不存在 1」 |
| `sync-log-fail-detail` / `fail-detail-rerun` | 失败详情：旅程走线 + 摘要（读/写）+ 阶段瀑布 + 过程日志 ERROR 高亮 + footer「重新执行该任务」 |
| `sync-log-detail` | 成功详情：旅程 + 阶段瀑布（读源 36ms/写入 125ms）+ 耗时趋势 + 12 行过程日志 |
| `synctask-restored` / `synctask-ops2` | 数据同步页：10 任务统计卡 + 完整表格 + 操作列 |
| `accept-home-desktop` | 工作台：koi 菜单图标正常渲染 + 顶部标签/面包屑 |
| `log-dlg-fixed` / `log-footer` | 日志弹窗：footer 仅「关闭」（§4.2 修复后） |

截图存档：`D:\ch\project\.openchamber\screenshots\`。
