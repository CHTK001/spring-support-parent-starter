# hotsport 部署（lib 与业务 jar 分离）

日常升级只上传 **业务 jar（含前端 agent 包后约 14MB）**，不上传 107MB 的依赖。这是本目录存在的唯一理由。

与 `spring-support-ai-starter/deploy/` 同一套方案、同一套目录约定，区别只在于
主类、端口、探活端点。

## 为什么能分离

fat jar 里 97% 是依赖。Spring Boot 默认的 `java -jar` 只认 jar 内 `BOOT-INF/lib`：

| 方案 | 结果 |
|---|---|
| `java -jar app.jar` | 只认 jar 内 `BOOT-INF/lib`，无法外置 |
| `PropertiesLauncher` + `-Dloader.path=lib` | **失败**：启动即 `NoClassDefFoundError`，`loader.path` 未把 lib 加进 classloader（本仓 ai-starter 模块实测） |
| `-cp "app.jar:lib/*"` + 主类全名 | **可用**：hotsport 实测 1.745s 启动 |

`start.sh` 用的是第三种。

## 实测体积（v4.0.0.42，含前端 agent 包）

| 部分 | 大小 |
|---|---|
| fat jar | 108.3 MB |
| `BOOT-INF/lib`（125 个 jar） | 106.5 MB |
| 业务代码 `target/classes`（43 个文件） | 3.35 MB |
| 前端 agent 包 `static/`（713 文件） | 26.4 MB（随业务 jar 一起打进 `app.jar`） |

日常升级上传的 `app.jar` = 业务代码 + 前端 agent 包 + 少量启动料 ≈ **14MB**
（而不是 108MB 的 fat jar）。

## 目标机目录

```
/data/hotsport/
├── app.jar           业务 jar，约 14MB（业务代码 + 前端 agent 包），每次发布替换（旧的留 5 份 app.jar.bak-*）
├── lib/              125 个依赖 jar，约 107MB，只在依赖变化时增量更新
├── jdk/              （可选）自带 JDK；不放则用 PATH 里的 java
├── config/
│   └── app.env       部署参数（不入仓库，见 app.env.example）
├── logs/app.log
├── app.pid
└── start.sh          启停脚本
```

需要 **JDK 25**：本模块字节码 major=69。宿主 JDK 若是 21，启动会直接
`UnsupportedClassVersionError`。

## 产物与脚本

| 文件 | 作用 | 在哪执行 |
|---|---|---|
| `release.ps1` | 构建 → 生成 app.jar + 依赖清单 → （可选）依赖增量 → 上传 → 重启 → 探活 | 开发机（Windows） |
| `start.sh` | `start` / `stop` / `restart` / `status` / `health` / `log` | 目标主机 |
| `app.env.example` | 部署参数模板 | — |

## 首次部署（依赖全量一次）

```powershell
# 1) 归一化构建：产物是 fat jar（repackage 默认开启），业务代码仍在 target/classes
mvn -o -DskipTests -pl spring-support-hotsport-starter clean package

# 2) 全量同步依赖 + 上传业务 jar
pwsh -File spring-support-hotsport-starter/deploy/release.ps1 -SyncDeps
```

依赖清单来自 **fat jar 的 `BOOT-INF/lib`**，不是 `mvn dependency:list`：后者会把
聚合 starter 空壳算进去、把 provided/optional 漏掉，按它同步会在 `lib/` 里留下
同一构件的两个版本，classpath 谁先谁后不可控。

> 清单必须是 **LF 换行**。远端用 `IFS= read -r n` 逐行读，CRLF 会让每个文件名
> 尾部多一个 `\r`，`[ -f "$n" ]` 全部失败 → 每次都重传全量。

## 日常升级（只传 14MB）

```powershell
pwsh -File spring-support-hotsport-starter/deploy/release.ps1
```

输出形如：

```
[release] business jar: app.jar  13,873 KB  (744 entries)  sha256=3f2a...
[release] skipping dependency sync (pass -SyncDeps to enable)
[release] uploading business jar (13,873 KB) ...
[release] restarting service
[release] health check (GET /api/apm/stats)
[release] DONE - /api/apm/stats reachable
```

依赖没变时**不要**加 `-SyncDeps`。

## 依赖变了怎么办

两种方式，二选一：

**A. 从开发机推（默认）**

```powershell
mvn -o -DskipTests -pl spring-support-hotsport-starter clean package
pwsh -File spring-support-hotsport-starter/deploy/release.ps1 -SyncDeps
```

只上传缺失的 jar（打成 `lib-delta.tar`），不是全量。

**B. 由目标机从远端 URL 拉（省开发机上行）**

依赖目录先发布成一个可访问的 HTTP 基址（`instabox-port serve <dir>` 即可得到），
然后让目标机自己拉：

```powershell
pwsh -File spring-support-hotsport-starter/deploy/release.ps1 -SyncDeps `
  -LibBase https://8080-xxxxxxxx.instabox.tech
```

`-LibBase` 生效的前提是该基址下**平铺**着各个 jar（`<LibBase>/<jar 名>`）。
实测这类静态服务会忽略 `Range`，不支持断点续传，因此单个 jar 的拉取是原子的
（先写 `.part` 再 `mv`），失败重试即可。

## 常用操作

```bash
ssh -p 2222 <user>@<host> 'PORT=18086 bash /data/hotsport/start.sh status'
ssh -p 2222 <user>@<host> 'bash /data/hotsport/start.sh health'
ssh -p 2222 <user>@<host> 'bash /data/hotsport/start.sh log 200'
ssh -p 2222 <user>@<host> 'bash /data/hotsport/start.sh restart'
```

`release.ps1` 开关：

| 开关 | 作用 |
|---|---|
| `-SyncDeps` | 比对并同步缺失的依赖（配合 `-LibBase` 则由目标机拉取） |
| `-LibBase <url>` | 依赖的远端 HTTP 基址 |
| `-SkipBuild` | 不重新构建，直接用 `target/` 现有产物 |
| `-SkipRestart` | 只上传不重启 |
| `-DryRun` | 只打印计划，不落任何改动 |

## 探活为什么打 `/api/apm/stats`

本应用**没有** actuator（`spring-boot-starter-actuator` 不在依赖里），
`/actuator/health` 会被当成静态资源处理并 404。`/api/apm/stats` 由 APM 控制器提供，
不依赖外部数据库（`application.yml` 的 H2 兜底），返回 `200` + `"ok": true`
即证明 Tomcat + Spring MVC + APM 服务层全部就绪。

## 前端交付：三包拆分

前端产物（`vue-support-parent-starter/apps/vue-support-hotspot-starter/dist`）
总约 178MB，直接整体入库/入 jar 太重。用 `split-dist.ps1` 拆成三个交付包：

| 包 | 内容 | 体积 | 用途 |
|---|---|---|---|
| **agent** | `index.html` + `loader*.js` + `os/` + `assets/` 全部 | 26.4MB | 观测面板核心运行时：Dashboard / Traces / Errors / 依赖 / JVM / 告警 / 日志等全部页面。**直接依赖 `@pages/hotsport` 的模块、或只要观测面板的部署，只需要这一包** |
| **resources** | `wasm/` `vendor/` `ruffle/` `images/` `apps/` + `flyfish-viewer-assets.json` | 151.6MB | 重型可选资源：文件预览（wasm）、Flash（ruffle）、地图引擎（vendor）等**按需懒加载**功能。不引入则面板其余功能不受影响 |
| **full** | agent + resources | 178MB | 完整 dist 原样 |

```powershell
# 在 vue-support-parent-starter 根：先构建再拆分
pnpm -C apps/vue-support-hotspot-starter build
pwsh -File apps/vue-support-hotspot-starter/split-dist.ps1
# 产物：dist-split/hotsport-agent.zip / hotsport-resources.zip / hotsport-full.zip / manifest.txt
```

**agent 包已打进 `app.jar`**：`src/main/resources/static/` 直接放 agent 包内容
（713 文件 / 26.4MB），thin jar 随之约 14MB。日常升级流程不变，前端页面
（含慢 SQL / 运维工具 / 调用序列下钻等排查分析界面）随 jar 一体交付。

**资源包不进入 jar**（151MB 太重，且与面板核心无关）。需要文件预览 / Flash /
地图等懒加载功能时，单独给部署挂资源：

> 注意：`common-starter` 的 `ResourceWebMvcConfigurer` 硬编码 `/** → classpath:/static/`，
> 覆盖了 `spring.web.resources.static-locations` 属性——**外部静态目录方案不可用**。
> 资源包要么打进一个独立的辅助 jar（`classpath:/static/wasm|vendor|ruffle...` 即可被
> 同一映射命中），要么在宿主上另起一个轻量静态服务（`instabox-port serve <dir>`）
> 并通过前端 loader-config 指向该地址。二者都不要与 `app.jar` 的 `static/` 混放。

## 已知缺口

- `release.ps1` 是 PowerShell：开发机是 Windows。Linux 开发机需要 `scp` 手工传
  `app.jar`（含前端后约 14MB，手工也能接受）或自行改写。

## 存储后端

| type | 说明 | 重启后数据 |
|---|---|---|
| `sqlite`（默认） | 内嵌真实数据库文件（`<path>/apm.db`），不依赖外部服务 | 保留 |
| `mysql` | 真实 MySQL/MariaDB，JDBC 直连，可 `SELECT` 直查/备份/多实例共享 | 保留 |
| `inmemory` | 仅调试用 | 丢失（归零） |
| `noop` | 关闭采集写入 | — |

`type=mysql` 时需额外满足：

1. `lib/` 里放入 `mysql-connector-j`（starter 中该依赖为 `optional`，fat jar 不含它，
   需手动拷贝：`scp .../mysql-connector-j-9.7.0.jar host:/data/hotsport/lib/`）；
2. 在 `config/app.env` 的 `APP_ARGS` 里给出 `url` / `username` / `password`（见模板）。

> 连接三键走 JVM 系统属性（`apm.storage.url` 等）生效：`ApmBootstrap`
> 只把固定键与「系统属性中 `apm.storage.*` 的键」合并进存储配置，
> provider 返回的其它键不会带入存储层（见 `ApmQueryService.currentStorageConfig()` 注释）。
