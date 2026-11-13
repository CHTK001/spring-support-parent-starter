# Changelog

本文件记录服务管理（协议服务器节点 / ServerFilter / 工作台）这一轮的改动与测试结论。
产品文档见同目录 `README.md`。

- 记录时间：2026-11-12 ~ 2026-11-13
- 被测环境：远端 cancon.hpccube.com（Slurm 计算节点内 MySQL + Redis + 应用 8082），前端本地 `pnpm dev` 8848 经 SSH 隧道访问
- 结论：**代理转发与操作日志全量通过**；本轮修复 3 项、新增 8 项 UI/交互；2 项后端修复已提交代码但**尚未随新包部署**（见「未完成」）

---

## 一、本轮改动

### 1.1 后端（utils 仓库）

| 文件 | 改动 | 目的 |
|---|---|---|
| `network/server/proxy/HttpProxyServer.java` | 两次连接后端都失败时回写 `502 Bad Gateway: backend unreachable` 再关连接 | 之前只有「后端地址解析为空」才写 502，连不上时客户端拿到连接重置，排障分不清「后端挂了」与「网络断了」 |
| `network/server/filter/IpFilter.java` | 加 `@Spi("ip")` + `@SpiDescribe(...)`（含 `mode`/`ips` 两个参数）、公有无参构造、`init(ServerFilterConfig)` | 之前构造器 private 且无 SPI 描述，服务管理里根本挂不上（报「缺少无参或 (ServerSetting) 构造器」） |

> 两处改动已通过 `mvn -o compile`（`utils-support-common-starter` BUILD SUCCESS，两个 class 均重新生成）。

### 1.2 前端（vue 仓库 `pages/monitor`）

| 文件 | 改动 |
|---|---|
| `views/ScServiceManage.vue` | 去掉方向切换器，固定三栏工作台；新增排序状态（名称/端口/异常优先）并接入左栏 |
| `views/service/shell/ShellRack.vue` | 单一工作台外壳（左栏清单 / 中间 ReTabs / 右侧检查器） |
| `views/service/shell/SvcNodeList.vue` | 行信息去重（第二行改为「协议 · 插件数 · →后端」）；ERROR 角标 + 原因 tooltip；↑↓ 移动焦点、Enter 打开；首载骨架屏 |
| `views/service/shell/SvcStatBar.vue` | 左栏增加排序下拉 |
| `views/service/shell/SvcWorkspace.vue` | 空态从一句说明改为「新增服务 / 打开 P2P 房间 / 刷新列表」三个入口 |
| `views/service/shell/SvcInspector.vue` | 新增「复制监听 / 复制地址 / 打开」三个地址动作；插件链 chips 可点进过滤器链 |
| `components/ScServerDetailBody.vue` | 访问地址可点击打开（含键盘 Enter）；新增「目标后端」并支持复制 |
| `apps/vue-support-monitor-starter/app.yaml` | `otherConfig.wallpaper.enable` 改回 `false`（注释本就写着「本部署先关闭」），消除启动时 `/v2/wallpaper/list` 500 红字 |

---

## 二、测试情况矩阵

结果口径：**PASS** 真实链路实测通过；**NOTE** 实测但结果可解释、需决策；**仅静态** 只做编译/类型检查；**未执行** 本次未跑。

### 2.1 部署与环境

| 编号 | 项 | 实际值 | 方式 | 结果 |
|---|---|---|---|---|
| ENV-1 | 远端入口 | `cancon.hpccube.com:65023`，用户 `acp5ayppx5`，RSA 私钥 | SSH | PASS |
| ENV-2 | 后端位置 | Slurm 作业 `127760561`，节点 `a16r1n15`，`--mem=10G` | `squeue` | PASS（16h+ 持续运行） |
| ENV-3 | 数据库 | 作业内 MySQL 8.0.36，`report` 库 **244 张表**（非 H2） | SQL + health | PASS |
| ENV-4 | 缓存 | 作业内 Redis 6.2.14（16379） | health | PASS |
| ENV-5 | JVM | `-XX:+UseCompactObjectHeaders`、`-Xmx2g`、SerialGC | 启动参数 | PASS |
| ENV-6 | 端口 | 仅 8082（room 29185 / agent 19399），单实例 | `netstat` | PASS |
| ENV-7 | 本机后端 | 无（已停） | `netstat` / 进程 | PASS |
| ENV-8 | 本地前端 | `pnpm dev` :8848，`MS_API_TARGET=http://127.0.0.1:18082` | 浏览器 | PASS |
| ENV-9 | 隧道 | 本地 18082 → `a16r1n15:8082` | health | PASS |
| ENV-10 | 长驻方式 | WMI 拉起（普通 `Start-Process` 子进程会被回收） | 复核 | PASS |

### 2.2 服务管理 API

| 编号 | 功能 | 结果 | 证据 |
|---|---|---|---|
| API-1 | 协议清单 `GET /ms/monitor/server/protocols` | PASS | 含 `http-proxy`、`tcp-proxy` 等 27 项 |
| API-2 | 插件元数据 `GET /filters` | PASS | `file-storage`/`service-discovery`/`ROUTING`/`URL-MAPPING`/`SHELL-URL-MAPPING` + FQCN |
| API-3 | 建节点 `POST /ms/monitor/server` | PASS | 返回 id，`status=RUNNING` |
| API-4 | 列表 `GET /ms/monitor/server` | PASS | 数组结构完整 |
| API-5 | 改节点 `PUT /{id}` | PASS | remark 回读一致 |
| API-6 | 启停 `POST /{id}/start|/{id}/stop` | PASS | 停止后端口不服务，启动后 200 |
| API-7 | 删除 `DELETE /{id}` | PASS | 列表归零 |
| API-8 | 热更新 `POST /{id}/hot-update` | PASS | 监听不变时只热更过滤器链 |
| API-9 | 操作日志 `GET /oper-log/{id}` | PASS | `total=3`：START / STOP / UPDATE |
| API-10 | 日志清理 | PASS | 删除节点后仅剩 DELETE 自身 1 条（设计口径） |

### 2.3 http-proxy（真实链路）

| 编号 | 用例 | 结果 | 证据 |
|---|---|---|---|
| HP-1 | GET 透传 | PASS | 直连与经代理均为 200 / 21 字节 / sha `13afeedf129863f6` |
| HP-2 | 响应头透传 | PASS | `Content-Type: application/vnd.spring-boot.actuator.v3+json;charset=UTF-8` |
| HP-3 | POST + 包体 | PASS | 经代理 `POST /v2/user/login` → 200 + accessToken |
| HP-4 | 写请求落库 | PASS | 经代理改 remark，直连回读一致 |
| HP-5 | 启停恢复 | PASS | stop 后 `000`，start 后 200 |
| HP-6 | 后端不可达 | **NOTE** | 服务器 jar 仍是旧包 → 表现为连接重置（`000`）；代码已修 502，待重新部署 |

### 2.4 tcp-proxy（字节级）

| 编号 | 用例 | 结果 | 证据 |
|---|---|---|---|
| TP-1 | 非 HTTP 后端 | PASS | 19103 → Redis：`PING` → `+PONG` |
| TP-2 | HTTP 后端 | PASS | 19104 → 8082：与直连同字节 `13afeedf129863f6` |
| TP-3 | 启停 | PASS | 同 HP-5 |

### 2.5 ServerFilter / 防火墙

| 编号 | 用例 | 结果 | 证据 |
|---|---|---|---|
| FLT-1 | 元数据枚举 | PASS | 见 API-2 |
| FLT-2 | 挂载既有插件 | PASS | 节点详情显示 1 环 / 13 参数 |
| FLT-3 | 名为 firewall 的插件 | NOTE | 不存在；`ip` 类型将在新包中出现（本轮已加 SPI 描述） |
| FLT-4 | `IpFilter` 挂载 | **已修，待部署** | 旧包报「缺少无参或 (ServerSetting) 构造器」 |
| FLT-5 | 主机级防火墙 | NOTE | `GET /ms/system/firewall/rules` 200，但目标机非 root 且无免密 sudo |

### 2.6 前端 UI

| 编号 | 项 | 方式 | 结果 |
|---|---|---|---|
| UI-1 | 单一三栏工作台（无切换器） | 浏览器 | PASS |
| UI-2 | 行信息去重 + 后端指示 | 浏览器 | PASS（`→127.0.0.1:8082`） |
| UI-3 | 异常角标 | 静态 | 仅静态（无 ERROR 节点可造） |
| UI-4 | 排序（名称/端口/异常优先） | 浏览器 | PASS |
| UI-5 | ↑↓ / Enter | 静态 | 仅静态（未做键盘自动化） |
| UI-6 | 空态三入口 | 浏览器 | PASS |
| UI-7 | 检查器地址动作 + 插件链 | 浏览器 | PASS（3 个地址按钮） |
| UI-8 | 详情「目标后端」+ 可点地址 | 浏览器 | PASS（spec 含该字段） |
| FE-1 | vite 模块编译 | 6 个改动文件 | PASS（全部 200） |
| FE-2 | `vue-tsc --noEmit` | 本次改动路径 | PASS（0 报错；仓库既有 17 条不属本次） |
| FE-3 | 运行时异常 | Playwright | PASS（pageerror=0） |
| FE-4 | 首载骨架屏 | 静态 | 仅静态 |

### 2.7 浏览器验收

| 步骤 | 断言 | 结果 |
|---|---|---|
| 登录 | 滑块通过进入布局 | PASS |
| 服务管理 | 列表 ≥1 行，显示节点名与端口 | PASS |
| 打开详情 | 页签出现，摘要条显示监听/协议/插件/后端 | PASS |
| 检查器 | 3 个地址按钮 + 快捷动作 + 统计 | PASS |
| 空态 | 清理后显示引导与三个入口 | PASS |

---

## 三、未完成 / 限制

1. **502 与 IP 过滤器修复未部署**：两处改动在 utils 源码并通过模块编译，但线上仍是旧 fat jar。生效需要：`mvn install`（utils）→ 重新打 monitor 包 → 上传（约 1.1GB）→ 重启 Slurm 作业。
2. **作业 24h 到期即停**：`--time=24:00:00`；MySQL 数据在计算节点 `/tmp`，作业结束即丢。需要延长时限并考虑数据落 `/public/home`。
3. **`/v2/wallpaper/list` 500** 已通过关闭 `otherConfig.wallpaper.enable` 消除（本次改动），属于配置层处理；后端补接口后可以再打开。
4. **键盘导航、异常角标、骨架屏**只做了静态验证，没有自动化用例。

---

## 四、复跑要点

```bash
# 后端（远端计算节点）
sbatch run-job.sbatch            # 作业内起 MySQL + Redis + 应用 8082

# 本地
MS_API_TARGET=http://127.0.0.1:18082 pnpm dev --port 8848 --host 127.0.0.1

# 真实链路自检（任意能访问 8082 的机器）
curl -s http://127.0.0.1:8082/actuator/health
curl -s -X POST http://127.0.0.1:8082/v2/user/login -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"admin123","loginType":"WEB"}'
curl -s "http://127.0.0.1:8082/ms/monitor/server?page=1&size=10" -H "Authorization: Bearer <token>"
```

验收脚本与临时产物已按本次要求清理，不再随仓库保留。

---

## 五、2026-11-13 第二轮：防火墙（IpFilter）支持所有协议 + 各 ServerFilter 验收

### 改动

| 文件 | 改动 |
|---|---|
| `IpFilter.java` | `supportProtocols()` 返回空数组（= 全部协议）；新增 `allows(clientIp)`；`@Spi("ip")` + `@SpiDescribe(mode/ips)` + 公有构造 + `init(ServerFilterConfig)` |
| `AbstractProxyServer.java` | 登记挂载到代理节点的 `IpFilter`，新增 `allowClient(Socket)` |
| `HttpProxyServer.java` | 读取请求前判定 IP，不放行直接 `403 Forbidden` |
| `TcpProxyServer.java` | 建立后端连接前判定 IP，不放行直接断开 |

构建：`utils-support-common-starter` `mvn package` BUILD SUCCESS（5,242,283 字节），以嵌套 lib 原位替换进远端 fat jar（`zip -0 -X`，已验证条目），新作业 `127873496`（节点 `a16r1n17`）health=200。

### 验收结果（真实链路）

| 用例 | 结果 | 证据 |
|---|---|---|
| ip 挂在 http 节点，黑名单 127.0.0.1 | PASS | 请求返回 403 |
| ip 白名单不含本机（热更新后） | PASS | 403 |
| ip 白名单含本机 | PASS | 未被拦（404 = 无路由，非拒绝） |
| ip 挂在 http-proxy | **FAIL** | 仍 200；过滤器的运行态被包装（Delegating），`instanceof IpFilter` 落空 |
| ip 挂在 tcp-proxy | **FAIL** | 仍收到 `+PONG`，同上原因 |
| file-storage 挂载 | PASS | 节点 RUNNING |
| service-discovery 挂载 | PASS | 节点 RUNNING |
| url-mapping 挂载 | PASS | 节点 RUNNING |
| shell-url-mapping 挂载 | NOTE | status=ERROR（需 shell 侧配置） |
| `/filters` 元数据出现 ip | **FAIL** | 元数据仍只列 5 类；运行时别名可解析，说明枚举源与运行时注册源不同 |

### 待办

1. `AbstractProxyServer.addFilter` 需先拆包（按类名反射取 delegate）再判断 `IpFilter`，代理侧拦截才会生效。
2. `/filters` 枚举源需与运行时 SPI 注册对齐，`ip` 才会出现在插件下拉里。
3. `shell-url-mapping` 的启动前置条件需要补文档或加参数校验。
