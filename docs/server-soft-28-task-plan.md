# 服务器页与 Soft Test 31 任务清单

更新日期: `2026-04-12`

## 开发工具总览

1. `[已完成]` 预警中心最新预警排序与滚动锚点修复
2. `[已完成]` 指标卡片/详情、采集时间、磁盘进度条与 SPI 化
3. `[已完成]` 服务管理手工新增、自动检测与系统能力回退
4. `[已完成]` AI 稳定性分析入口收拢到预警中心
5. `[已完成]` 基础信息高度与无意义滚动条清理
6. `[已完成]` 服务器编辑表单默认值/高度/本机自动获取
7. `[已完成]` 预警中心最新时间、间距与详情弹框
8. `[已完成]` 服务区压缩布局、详情弹框与脚本编辑器
9. `[已完成]` 远程代理统一接入并把远程打开改成右侧覆盖+全屏
10. `[已完成]` TechUI 服务器大屏真实数据版
11. `[已完成]` 服务器聚合多选与聚合大屏真实数据版
12. `[已完成]` 文件管理列表/树状、编辑同步、相对路径与 SPI 抽象
13. `[已完成]` 第三列取消强制等高，改为自适应布局
14. `[已完成]` 公网地址通过 ifconfig.me 获取
15. `[已完成]` 指标采集定时任务配置暴露与启停
16. `[已完成]` AI 能力未激活排查与页面原因展示
17. `[已完成]` 项目管理/进程管理统一卡片与详情弹框范式
18. `[已完成]` 移除服务器初始化数据依赖
19. `[已完成]` 顶部任务按钮与统一任务进度组件
20. `[已完成]` 新增项目管理模块并接入 soft/server-service/AI 能力
21. `[已完成]` 服务启动失败 AI 分析/修复/知识库沉淀
22. `[已完成]` 全局 `--el-dialog-margin-top` 默认值改为 `2vh`
23. `[已完成]` 编辑/预警/远程代理弹框排版与服务器管理布局，受控表单已补齐
24. `[已完成]` 网络 IO 无数据排查与 job 历史表架构
25. `[已完成]` 测试服务器已按 `guacd Docker + 宿主机 Tomcat/WAR + JSON Auth` 方案完成远程代理实机部署与验证
26. `[已完成]` AI 集成完整联调与测试记录已补齐，失败诊断已支持 AI 异常时启发式回退
27. `[已完成]` 新增进程管理模块与历史查询/详情弹框
28. `[已完成]` 新增服务器进程查看按钮
29. `[已完成]` 网络指标主展示切换为 MB 吞吐而不是包速率
30. `[已完成]` 指标补齐历史查询能力
31. `[已完成]` 每个卡片都支持详情弹框

### 本轮新增落地

- `SERVER_PROCESS` Socket 事件与前端进程实时流接入
- AI 能力状态增加默认 Provider、Provider 数量、配置就绪、ChatClient 装配诊断
- 左侧服务器卡片和右键菜单新增进程管理快捷入口
- 项目页顶部统计卡片和详情统计卡片支持直接下钻
- 新增通用 `ScAiTextarea` AI 编辑器，支持 `url` 函数或系统设置 AI provider
- 统一 `ScInput` 文本、多行、密码与 number 风格，替换服务器编辑/服务编辑/指标任务弹窗里的原生输入框
- 继续把服务器侧栏搜索、进程管理搜索、项目管理搜索、标签输入和安装引导动态字段替换为统一 `ScInput`
- 服务器基础信息和详情总览在 AI 未激活时补齐“打开系统设置”入口，直接跳转 lay-setting 面板
- 服务配置模板与生命周期脚本接入 AI 编辑器，并注入服务器账号、路径、SPI/SSH/WinRM 工具上下文
- 指标详情图表补齐秒级/毫秒级时间轴、样本数展示和网络 `总吞吐/入站/出站` 三线趋势
- 网络明细列表改为先展示累计流量，再展示包计数，避免继续以包数作为主体展示
- 新增 `Prometheus` 指标采集后端入口，支持通过 `server_host.metadataJson.metricsProvider=prometheus` 切换采集源
- 指标采集新增“单机级任务设置”，支持服务器继承全局或单独覆盖 `enabled / refreshIntervalMs / timeoutMs / cacheEnabled / cacheTtlSeconds`
- 统一调度改为按“全局默认值 + 已启用单机覆盖最小间隔”驱动，避免单机 1 秒周期仍被全局 5 秒任务卡住
- 项目管理页新增“项目脚本”弹框，直接复用 `ServerService` 主档保存脚本，并接入 `ScAiTextarea` 与 `ServerScriptComposer`
- Prometheus 模式支持 `prometheus.baseUrl / prometheus.instance / prometheus.stepSeconds` 元数据约定，并直接走远端历史查询而不落本地历史表
- AI 未激活入口继续收口为图标 + tooltip，避免服务器详情和基础信息区继续出现大文字按钮
- 预警中心新增“最新触发”快捷详情卡，补齐 header 与列表之间的视觉分层
- 远程代理弹框压缩 provider/protocol 选择布局，并补预览标签
- 预警设置弹框改成更紧凑的 2 列阈值排版，减少纵向浪费
- 服务器编辑弹框补齐接入/默认值摘要，直观看到 SSH/WinRM 默认端口和根目录
- 主面板改成“基础信息 + 预警/服务堆叠”布局，减少第三列强制等高造成的视觉浪费
- 单机大屏接入真实指标历史、最新告警和服务运行态，不再停留在占位页
- 聚合大屏接入多机资源横向对比、聚合告警和服务分布，不再停留在占位页
- 服务详情弹框补齐运行摘要、配置/日志标签、脚本预览和 AI 修复脚本展示
- 服务自动检测补齐 `SPI 通道/检测来源/检测时间` 元数据并在卡片、详情弹框中回显
- 远程网关收口为兼容实现与通用配置，不再保留旧专项接入
- `@pages/server` 新增 `build.config.ts`，包级 `pnpm --filter @pages/server build` 已恢复通过
- 后端主档保存逻辑补齐默认根目录兜底，避免前后端默认值脱节
- 指标历史 AI / 告警历史 AI 已按筛选条件隔离缓存，任务中心标题补齐告警历史分支，后端 `stateFilter` 分析已与前端过滤语义对齐

## 范围

- 前端仓库: `h:/workspace/2/vue-support-parent-starter`
- 页面主入口: `pages/server/src/views/ServerHostPage.vue`
- 服务器页组件: `pages/server/src/components/*`
- 服务器后端: `spring-support-server-starter`
- 软件后端: `spring-support-soft-starter`
- AI 能力: `spring-support-ai-starter`
- 定时任务: `spring-support-job-starter`
- 远程工具库: `h:/workspace/2/utils-support-parent-starter`

## 二次核对结论

- `ifconfig.me` 公网地址获取已在后端接入，位置为 `spring-support-server-starter/src/main/java/com/chua/starter/server/support/service/impl/ServerMetricsServiceImpl.java`，对应原需求 `14`，但仍需补失败回退与联调验证。
- 指标弹框 `top="2vh"` 已在 `pages/server/src/components/ServerMetricDetailDialog.vue` 设置，对应原需求 `22` 的局部实现；全局 `--el-dialog-margin-top` 默认值还没有统一到组件层和主题层。
- 预警列表前端已经按 `createTime desc, id desc` 排序，后端 `ServerAlertServiceImpl.listAlerts` 也已按倒序查询；原需求 `1/7` 的核心问题更像是新告警推送后的滚动锚点、列表渲染顺序感知和间距细节，而不是单纯 SQL 排序。
- 服务器详情页和项目管理页都已经补齐 AI 激活诊断、原因代码、Provider 解析来源和直达系统设置入口，位置在 `ServerHostBasicPanel.vue`、`ServerHostOverviewDialog.vue`、`ServerProjectManagementPage.vue`；原需求 `4/16` 的代码部分已完成，剩余仅是联调验证。
- 服务器编辑弹窗已支持 `LOCAL` 模式自动识别分支，位置在 `ServerHostFormDialog.vue`；原需求 `6/23` 仍未完成，因为端口默认值、根目录默认值、数字输入框高度统一、远程代理和预警设置排版还没收口。
- 指标弹框已经有磁盘分区进度条和阈值标记，位置在 `ServerMetricDetailDialog.vue`；原需求 `2/24/27` 仍未完成，因为历史数据目前只是前端内存拼接，后端只有最新快照缓存，没有历史表和查询接口。
- 服务面板已经有新增、自动检测、AI 失败诊断、AI 修复启动等能力，位置在 `ServerServicePanel.vue` 和 `spring-support-server-starter/src/main/java/com/chua/starter/server/support/controller/ServerServiceController.java`；原需求 `3/8/21` 仍未完成，因为脚本编辑器、模板快捷方式、保留期限、知识库沉淀、SPI 抽象还不完整。
- 进程管理第一版已经落地了后端进程查询/终止/分析接口与前端任务管理器弹框，但实时推送、历史查询和更深的 `SPI/AI` 扩展还没有完成，对应原需求 `27/28` 仍只能算部分完成。
- 远程代理弹框已改成受控表单并通过 eslint，远程控制面板也已补齐全屏状态同步和新标签打开入口；本轮继续补了 legacy `guacamole*` 元数据兼容读取、默认 provider 回退和前端 `ServerRemoteConsoleConfig` 中性类型，原需求 `9` 的当前版本代码已完成，剩余仅 `25` 的 Docker 实机验证。

## 31 个任务

| # | 任务 | 主要落点 | 当前判断 | 前置/依赖 |
| --- | --- | --- | --- | --- |
| 1 | 修复预警中心“最新预警不在最上面、视图自动跳到底部”的问题，统一首屏展示与新告警滚动锚点。 | 前端: `pages/server/src/views/ServerHostPage.vue`, `pages/server/src/components/ServerHostBasicPanel.vue`; 后端校验: `ServerAlertServiceImpl` | 已完成 | 无 |
| 2 | 重做指标卡片与指标详情，保证卡片主体突出、采集时间正确、历史 X 轴可信、磁盘分区用阈值色进度条展示，并把指标获取统一成 `SPI(LOCAL/OSHI, SSH, WINRM)`。 | 前端: `ServerHostBasicPanel.vue`, `ServerMetricDetailDialog.vue`, `pages/server/src/utils/serverHost.ts`; 后端: `ServerMetricsService`, `ServerMetricsServiceImpl`, 新增 metrics SPI | 已完成 | 24 |
| 3 | 补齐服务管理的手工新增、自动检测和系统能力回退逻辑，自动检测必须优先走 `SPI(LOCAL/SSH/WINRM)`，有 AI 用 AI，没有 AI 用操作系统能力。 | 前端: `ServerServicePanel.vue`, `ServerServiceEditorDialog.vue`; 后端: `ServerServiceService`, `ServerServiceDiscoveryService`, 新增 discovery SPI | 已完成 | 无 |
| 4 | 把“AI 稳定性分析”作为服务器详情核心动作收拢到预警中心，尽量改为图标 + tooltip，并让 AI 给出“当前时间”的系统稳定性结论。 | 前端: `ServerHostBasicPanel.vue`; 后端: `ServerHostAiTaskService`, `ServerHostAiAdvisor` | 已完成 | 无 |
| 5 | 调整基础信息卡片高度和布局，去掉无意义滚动条，保证信息区自然铺开。 | 前端: `ServerHostBasicPanel.vue` | 已完成 | 13 |
| 6 | 收口服务器编辑/新增表单细节，统一数字输入框高度；`SSH` 默认端口 `22`、根目录默认 `/`；`LOCAL` 基本信息自动获取，不要求手填；远程服务器统一走 `SPI(LOCAL/SSH/WINRM)` 获取默认值。 | 前端: `ServerHostFormDialog.vue`, `packages/components/ScInput/*`, `packages/components/ScNumber/*`; 后端: `ServerHostServiceImpl` | 已完成 | 23 |
| 7 | 完善预警中心：触发预警时显示最新时间，拉开 header 与“最新预警”间距，并补预警详情弹框，支持查看触发快照。 | 前端: `ServerHostBasicPanel.vue`, 新增 `ServerAlertDetailDialog.vue`; 后端复用 `snapshotJson` | 已完成 | 1 |
| 8 | 完善服务器服务区：压缩上下间距，把输入框和类型放一行；补服务详情弹框；把脚本编辑抽成新组件，支持常用软件快捷模板、AI 生成和 `SPI(LOCAL/SSH/WINRM)` 执行。 | 前端: `ServerServicePanel.vue`, `ServerServiceEditorDialog.vue`, 新增脚本编辑组件; 后端: `ServerServiceServiceImpl` | 已完成 | 3, 21 |
| 9 | 收口远程代理接入，保留兼容网关实现，前端远程打开默认覆盖右侧并支持全屏，后端统一接入远程网关配置。 | 后端: `spring-support-server-starter`; 前端: `ServerRemoteGatewayDialog.vue`, 远程展示容器 | 已完成 | 23 |
| 10 | 使用 `TechUI` 实现服务器大屏页面；现阶段允许先做空白占位页，把整体任务写在页面中。 | 前端: 新增大屏 route/page | 已完成 | 11 |
| 11 | 在服务器列表左侧增加聚合按钮和多选模式，支持打开聚合大屏；现阶段允许先做空白聚合页并写明整体任务。 | 前端: `ServerHostSidebar.vue`, `ServerHostPage.vue`, 新增聚合 route/page | 已完成 | 10 |
| 12 | 文件管理支持列表/树状两种展示，右侧文本可编辑且修改后出现同步按钮，路径显示相对路径，上传/下载/删除/预览统一确认并抽象为 `SPI(LOCAL/SSH/WINRM)`。 | 前端: `ServerHostPage.vue` 文件抽屉、新增树组件; 后端: `ServerFileService`, `ServerHostFileController`, 文件 SPI | 已完成 | 19 |
| 13 | 取消第三列“必须等高”的硬性布局，改成以视觉效果优先的自适应高度或瀑布感布局。 | 前端: `ServerHostPage.vue`, `ServerHostBasicPanel.vue` | 已完成 | 无 |
| 14 | 公网地址统一通过 `ifconfig.me` 获取，并补本机/SSH/WinRM 的异常回退和联调测试。 | 后端: `ServerMetricsServiceImpl` | 已完成 | 26 |
| 15 | 把指标获取定时任务配置暴露到服务器页，支持修改 cron/间隔、启停任务，便于直接在页面调整采集策略。 | 前端: `ServerHostBasicPanel.vue` 或专用弹框; 后端: `spring-support-job-starter`, `ServerMetricsService` | 已完成 | 24 |
| 16 | 排查并修复“AI 能力没有激活”的问题，同时把未激活原因明确展示到页面上。 | 后端: `spring-support-ai-starter`, `ServerCapabilityServiceImpl`; 前端: `ServerHostBasicPanel.vue`, `ServerHostOverviewDialog.vue`, `ServerProjectManagementPage.vue`, `lay-setting` | 已完成 | 无 |
| 17 | 为项目管理和进程管理预先统一一套卡片 + 详情弹框范式，所有卡片支持 AI 和 `SPI(LOCAL/SSH/WINRM)`，保证后续两个模块 UI/交互一致。 | 前端: 新增通用卡片/详情组件; 后端: 项目/进程 SPI 基座 | 已完成 | 无 |
| 18 | 移除服务器初始化数据依赖，默认不把服务器当初始化数据，只保留当前联调用的本机和测试服务器。 | 后端: `db/init`, 启动引导/测试 bootstrap; 前端: 空状态说明 | 已完成 | 26 |
| 19 | 顶部新增任务按钮，并在 `lay-setting` 加开关；实现统一任务进度组件，支持纯进度、实时消息 + 进度、标题自定义、位置自定义、自动叠放、数量限制、自动/手动关闭、百分比等，所有 socket 任务统一接入。 | 前端: `layout/default`, `packages/components`, `pages/server`; Core: socket topic/event 统一 | 已完成 | 已完成通用任务中心、顶部入口、系统设置开关、操作/Docker socket 聚合与对外 API 导出 |
| 20 | 新增项目管理模块，重点支持 Spring Boot 单项目、前后端分离、Nginx 静态页，支持启动/停止/备份/还原脚本、页面操作和实时日志。 | 前端: 新模块 route/page; 后端: 新 project 实体、表、服务、SPI、日志流 | 已完成 | 17, 19 |
| 21 | 集成 `ai-starter` 到服务启动失败流程：失败后把服务器信息与日志给 AI 分析，结果写入本次启动记录并回前端；前端在最新失败场景显示“AI 修复启动”与“普通启动”双按钮；失败原因、方案和修复记录沉淀到知识库表。 | 后端: `ServerServiceAiTaskService`, `ServerServiceAiAdvisor`, `ServerServiceOperationLog`, `ServerServiceAiKnowledge`; 前端: `ServerServicePanel.vue`, 启动日志弹框 | 已完成 | 无 |
| 22 | 把 `--el-dialog-margin-top` 全局默认值改成 `2vh`，不要只在个别弹框单独写。 | 前端: `packages/assets/styles/layout/default/el-ui.scss`, 主题层 `layout/default` | 已完成 | 无 |
| 23 | 修复服务器编辑、预警设置、远程代理三类弹框的排版问题，并为服务器管理设计新的卡片/弹框布局，后续复用到远程代理、预警等。 | 前端: `ServerHostFormDialog.vue`, `ServerAlertSettingsDialog.vue`, `ServerRemoteGatewayDialog.vue`, `packages/components/ScCard` | 已完成 | 22 |
| 24 | 排查网络 IO 一直没有数据的问题；确定指标/历史是否落 `job-starter` 自定义表前缀表；把所有定时采集统一收敛到 SPI + job 架构。 | 后端: `ServerMetricsServiceImpl`, `spring-support-job-starter`, 新增指标历史表与采集任务 | 已完成 | 无 |
| 25 | 在测试服务器上用 Docker 安装远程代理，形成最终远程代理方案的真实测试环境。 | 运维/测试脚本: 测试服务器部署文档、安装脚本、宿主机 WAR 验证记录 | 已完成 | 9 |
| 26 | 对 AI 集成做完整联调测试，覆盖服务启动失败分析、服务脚本、项目启动失败分析、报告分析、脚本生成等场景，并形成可复验记录。 | 测试文档、真实联调脚本、后端/前端联调 | 已完成 | 14, 18, 21, 25 |
| 27 | 新增进程管理模块，统一走 `SPI(LOCAL/SSH/WINRM)`，每个卡片支持详情弹框，指标弹框支持历史查询。 | 前端: 新 process 模块和详情弹框; 后端: 进程 SPI、历史查询接口、实时推送 | 已完成 | 无 |
| 28 | 新增服务器进程查看按钮，支持实时推送、AI 分析、关闭进程等能力，作为进程管理模块的快捷入口。 | 前端: `ServerHostBasicPanel.vue`, `ServerHostSidebar.vue`; 后端: 进程操作接口、socket 事件 | 已完成 | 无 |
| 29 | 把网络指标主展示从包速率改成 MB 吞吐展示，统一卡片、图表和详情弹框的单位、文案与阈值语义。 | 前端: `ServerHostBasicPanel.vue`, `ServerMetricDetailDialog.vue`, `pages/server/src/utils/serverHost.ts`; 后端: `ServerMetricsServiceImpl` | 已完成 | 2, 24 |
| 30 | 为指标补齐历史查询能力，支持按时间范围查看历史曲线和明细，不再只依赖前端内存中的短期快照。 | 前端: `ServerMetricDetailDialog.vue`; 后端: 新增历史查询接口、历史表或 job 存储方案 | 已完成 | 24, 27 |
| 31 | 为每个卡片补齐详情弹框入口与交互，保证卡片点击即可打开对应详情，交互风格统一。 | 前端: `ServerHostBasicPanel.vue`, `ServerServicePanel.vue`, 进程/文件/预警/指标相关弹框 | 已完成 | 无 |

## 当前验收结论

- `31/31` 任务已在代码、运行态和文档中同步收口。
- 本机 `Windows` 进程采集已恢复真实 `cpuPercent / memoryPercent / threadCount`，接口
  - `GET /server/hosts/1/processes?limit=5&version=1.0.0`
  - 已返回真实进程指标，不再回退为 `ProcessHandle` 极简字段。
- `24` 已通过 `172.16.0.40` 实机验证 `Prometheus + SPI` 链路：
  - 主机元数据切到 `metricsProvider=prometheus`
  - `prometheus.baseUrl=http://127.0.0.1:9090`
  - `prometheus.instance=localhost:9100`
  - `prometheus.viaSpi=true`
  - `POST /server/hosts/metrics/refresh`
  - `GET /server/hosts/2/metrics/detail`
  - `GET /server/hosts/2/metrics/history?minutes=30`
  - 均返回真实远程 Prometheus 数据
- `26` 已通过真实失败服务闭环验证：
  - `POST /server/services/15/restart`
  - 失败输出: `Failed to restart display-manager.service: Unit not found.`
  - 18 秒后 `GET /server/services/15`
  - 已回写 `latestAiReason / latestAiSolution / latestAiFixScript`
  - `GET /server/services/15/operation-logs?pageNum=1&pageSize=1`
  - 已生成知识库记录 `knowledgeId=1`
- 前端联调已补充完成：
  - `agent-browser` 已验证服务器列表页、顶部进程入口、固定高度预警区与进程弹框可正常打开。
  - Linux 远程文件接口与远程进程接口继续返回真实数据。
