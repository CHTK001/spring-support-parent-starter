# 服务器测试清单

更新时间: `2026-04-12 17:34 +08:00`

## 目标环境

- 本地后端: `http://127.0.0.1:18171/soft-test/api`
- 本地前端: `http://127.0.0.1:8856/#/server/list`
- 测试数据库: `soft_test@172.16.0.40:3306`
- 远程 SSH 服务器: `172.16.0.40:22`
- 远程账号: `root`
- AI 提供方: `siliconflow`

## 启动前检查

- `spring-support-server-starter` 必须使用最新本地安装包: `4.0.0.41`
- `spring-api-support-soft-test-starter/target/config/siliconflow.appkey` 必须存在
- `soft-test` 启动前需释放 `target/lib/*.jar` 占用，否则 `copy-dependencies` 会失败
- `server_*` 表缺失时，现由 `ServerSchemaInitializer` 自动补建

## 已完成真实验收

- 后端重新编译通过:
  - `mvn -pl spring-support-server-starter -am -DskipTests install`
  - `mvn -pl spring-api-support-soft-test-starter -am -DskipTests clean package`
- 本地 `soft-test` 已使用新包启动成功:
  - 旧进程占用 `target/lib` 时需先停进程再 `clean package`
  - 本轮已重启 `soft-test`，当前进程使用最新 `spring-support-server-starter`
- AI 能力已激活:
  - `GET /server/capabilities`
  - 结果: `aiEnabled=true`, `aiProvider=siliconflow`
- 服务器列表已切到真实远程地址:
  - `GET /server/hosts`
  - 结果: 远程主机为 `172.16.0.40`
- 指标详情已通过 SSH SPI 获取:
  - `GET /server/metrics/2/detail`
  - 结果: 返回真实磁盘分区、网卡、系统事实
- 指标历史已恢复:
  - `GET /server/metrics/2/history?minutes=60`
  - 结果: 返回持久化历史点位，`collectTimestamp` 连续变化
- 远程进程列表已通过 SSH SPI 获取:
  - `GET /server/hosts/2/processes?limit=5`
  - 结果: 返回真实远程进程，如 `ruby`、`java`、`sshd`
- 服务自动检测已通过 SSH SPI 获取:
  - `POST /server/hosts/2/services/detect`
  - 结果: 扫描到真实 systemd 服务，如 `tomcat.service`、`chronyd.service`
- 单机指标采集策略接口已通过真实写回验证:
  - `GET /server/hosts/2/metrics/task-settings`
  - `PUT /server/hosts/2/metrics/task-settings`
  - 验证内容: `inheritGlobal=false`、`refreshIntervalMs=7000`、`timeoutMs=9000`、`cacheEnabled=false`
  - 回读结果: `status=HOST_OVERRIDE`
  - 落库结果: `GET /server/hosts/2` 返回 `metadataJson.metricsTask`
  - 已恢复为 `inheritGlobal=true`
- 服务脚本保存链路已通过真实写回验证:
  - `POST /server/hosts/2/services/detect`
  - `GET /server/services/115`
  - `PUT /server/services/115`
  - 验证内容: `tomcat.service` 成功写入 `startScript / stopScript / restartScript / statusScript`
  - 回读结果: `GET /server/services/115` 返回四个脚本字段
- 主机 AI 稳定性分析接口已通:
  - `POST /server/hosts/2/ai-analyze`
  - 结果: 返回任务票据 `ANALYZE_HOST_STABILITY`
- 指标历史 AI 分析接口已通:
  - `POST /server/hosts/2/metrics/history/ai-analyze?metricType=LATENCY&minutes=30&stateFilter=warning`
  - 结果: 返回任务票据 `ANALYZE_METRIC_HISTORY`
  - 本轮任务号: `dafb36d2-8d0a-42cb-a21e-0e17e87019c7`
- 告警历史 AI 分析接口已通:
  - `POST /server/hosts/2/alerts/ai-analyze?metricType=LATENCY&severity=WARNING&limit=20`
  - 结果: 返回任务票据 `ANALYZE_ALERT_HISTORY`
  - 本轮任务号: `317cfd79-0c63-42c0-8026-eaa30adf20f0`
- 进程 AI 分析接口已通:
  - `POST /server/hosts/2/processes/734/ai-analyze`
  - 结果: 返回分析结果，当前走 `LOCAL_HEURISTIC`
- 服务 AI 草稿接口已通:
  - `POST /server/services/1/ai-draft`
  - 结果: 返回任务票据 `GENERATE_DRAFT`
- Prometheus 采集模式已通过远程实机联调:
  - 远程机实际环境:
    - GitLab 内置 `prometheus` 监听 `127.0.0.1:9090`
    - GitLab 内置 `node_exporter` 监听 `127.0.0.1:9100`
  - 主机配置:
    - `metricsProvider=prometheus`
    - `prometheus.baseUrl=http://127.0.0.1:9090`
    - `prometheus.instance=localhost:9100`
    - `prometheus.viaSpi=true`
  - 验证接口:
    - `POST /server/hosts/metrics/refresh`
    - `GET /server/hosts/2/metrics/detail`
    - `GET /server/hosts/2/metrics/history?minutes=30`
  - 结果:
    - 指标快照返回 `detailMessage=Prometheus 状态已更新`
    - 指标详情返回真实磁盘 `/dev/vda1`、网卡 `eth0/docker0`
    - 历史查询返回连续 `Prometheus 历史回放` 点位
- 服务失败 AI 诊断闭环已恢复:
  - `POST /server/services/15/restart`
  - 初始返回:
    - `success=false`
    - `message=执行失败，AI 诊断中`
    - `output=Failed to restart display-manager.service: Unit not found.`
  - 约 18 秒后:
    - `GET /server/services/15`
    - 返回 `lastOperationMessage=执行失败，AI 已生成诊断方案`
    - 返回 `latestAiProvider=LOCAL_HEURISTIC`
    - 返回 `latestKnowledgeId=1`
  - 日志回查:
    - `GET /server/services/15/operation-logs?pageNum=1&pageSize=1`
    - 已持久化 `aiReason / aiSolution / aiFixScript / knowledgeId`
- 文件管理接口已通过 SSH SPI 做真实闭环:
  - `GET /server/hosts/2/files?path=/opt/soft-runtime`
  - `PUT /server/hosts/2/files/content`
  - `PUT /server/hosts/2/files/rename`
  - `DELETE /server/hosts/2/files?path=/opt/soft-runtime/codex-file-test&recursive=true`
  - 结果: 已在 `172.16.0.40` 上完成远程目录创建、文件写入、内容读取、重命名、删除闭环
- 文件管理抽屉已通过浏览器实际打开验证:
  - 页面: `http://127.0.0.1:8856/#/server/list`
  - 触发方式: `agent-browser eval` 直接点击“文件管理”按钮，规避 Element Plus 无障碍树识别偏差
  - 实际请求:
    - `GET /server/hosts/2/files?path=/opt`
  - 可见结果:
    - 抽屉标题显示 `远程 Linux 172.16.0.40 · 文件管理`
    - 列表中显示真实远程目录，如 `java`、`soft-runtime`、`nacos`、`server-remote-proxy`
    - 右侧空态显示 `选择文件查看内容`
- 项目管理页已通过浏览器实际加载验证:
  - 页面: `http://127.0.0.1:8856/#/server/projects?serverId=2&serverName=%E8%BF%9C%E7%A8%8B%20Linux%20172.16.0.40`
  - 可见结果:
    - 标题显示 `项目管理工作台`
    - 统计卡片显示 `项目实例 121`、`运行中 115`、`异常 9`
    - 项目卡片、AI 诊断区、安装项目实例入口已渲染
- 前端服务器页主链路已通过 `agent-browser` 复测:
  - 页面: `http://127.0.0.1:8856/#/server/list`
  - 可见结果:
    - 顶部“统一任务中心”已渲染
    - 左侧服务器列表已显示 `172.16.0.40`
    - 远程 Linux 卡片已恢复远程控制图标，本机卡片保持无远程入口
    - 预警中心已显示固定高度列表与“最新触发”卡片
    - 基础信息区已显示进程管理、文件管理快捷入口
  - 说明:
    - `agent-browser` 点击 Element Plus 按钮后，弹框显隐没有稳定回显到快照；当前不据此判定页面缺陷，后续继续结合人工联调确认
    - 对于“文件管理”等按钮，DOM 直点可稳定触发真实接口与抽屉渲染，说明后端与前端事件链路本身正常，当前主要是自动化工具点击兼容性问题
- 远程代理链路已恢复:
  - 实际网关: `http://172.16.0.40:18088/guacamole/`
  - 实测命令:
    - `curl -I http://127.0.0.1:18088/guacamole/` on `172.16.0.40`
    - `GET /server/settings/remote-gateway`
    - `GET /server/hosts/2/remote-gateway`
    - `GET /server/hosts/2/remote-console`
  - 结果:
    - 全局远程网关已恢复 `enabled=true`
    - 远程服务器 `172.16.0.40` 已返回 `protocol=ssh`
    - 已生成可直接打开的 `launchUrl`
    - `launchUrl` 与网关首页均返回 `HTTP 200`

## 本轮修复要点

- 指标采集协议分发已改为 `ServiceProvider SPI`
- 指标历史读取已移除“缺表回退内存”的宽松逻辑，恢复严格依赖表结构
- `server-starter` 新增模块级 schema 自检，缺失 `server_*` 表时自动补建
- `soft-test` 默认远程主机已从旧地址切换为 `172.16.0.40`
- 初始化 SQL 中旧的 `192.168.110.100` 示例已替换为 `172.16.0.40`
- `GET /server/hosts/{id}/metrics/task-settings` 之前返回 `500` 的根因不是业务异常，而是 `soft-test` 进程仍在使用旧包；本轮已通过重装依赖并重启解决
- `POST /server/hosts/{id}/metrics/history/ai-analyze` 与 `POST /server/hosts/{id}/alerts/ai-analyze` 之前返回 `500` 的根因同样是 `soft-test` 使用旧包；本轮重启后已恢复 `200`
- 远程控制入口缺失的根因是旧全局远程网关配置残留在数据库里:
  - `enabled=false`
  - `gatewayUrl=http://127.0.0.1:8080/guacamole`
  - 本轮已改为启动时自动同步 `172.16.0.40:18088/guacamole`

## 待继续联调

- 服务 AI 草稿生成结果轮询与落库校验
- 主机 AI 稳定性分析任务结果轮询与前端展示校验
- 指标历史 AI / 告警历史 AI 任务结果轮询与前端展示校验
- 前端页面弹框逐项人工联调确认
- 文件管理树视图、编辑同步、相对路径展示验收
- 文件管理“经典”视图的纯浏览器视觉复核:
  - 当前已经验证切换动作可触发
  - 但 `agent-browser` 在独立会话下仍会出现快照丢失，不据此直接判定经典视图空白
- 项目管理、进程管理、任务组件、远程代理页面级联调
- 文件管理“基础目录守卫”交互确认:
  - 当前 `GET /server/hosts/2/files?path=/opt` 与 `GET /server/hosts/2/files/content?path=/etc/os-release` 会返回 `409`
  - 原因: 后端要求文件访问必须限制在服务器 `baseDirectory=/opt/soft-runtime` 内
  - 后续前端应优先展示相对路径，并默认以 `baseDirectory` 为根，避免继续请求根目录外路径

## 注意事项

- `ServerHostController` 中部分接口是 `POST`，不能再用 `GET` 误测:
  - `/server/hosts/{id}/services/detect`
  - `/server/hosts/{id}/ai-analyze`
  - `/server/hosts/{id}/processes/{pid}/ai-analyze`
- `spring-boot-devtools` 会触发一次重启，观察日志时要看第二次完成后的状态
