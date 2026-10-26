# hotsport 部署（lib 与业务 jar 分离）

日常升级只上传 **业务 jar（约 3MB）**，不上传 107MB 的依赖。这是本目录存在的唯一理由。

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

## 实测体积（v4.0.0.42）

| 部分 | 大小 |
|---|---|
| fat jar | 108.3 MB |
| `BOOT-INF/lib`（125 个 jar） | 106.5 MB |
| 业务代码 `target/classes`（43 个文件） | 3.35 MB |

也就是说：一次日常升级传 3.35MB，而不是 108MB。

## 目标机目录

```
/data/hotsport/
├── app.jar           业务 jar，约 3MB，每次发布替换（旧的留 5 份 app.jar.bak-*）
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

## 日常升级（只传 3MB）

```powershell
pwsh -File spring-support-hotsport-starter/deploy/release.ps1
```

输出形如：

```
[release] business jar: app.jar  3,431 KB  (43 entries)  sha256=3f2a...
[release] skipping dependency sync (pass -SyncDeps to enable)
[release] uploading business jar (3,431 KB) ...
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

## 已知缺口

- **`app.jar` 里不含前端静态资源的最新版本**：`src/main/resources/static/` 目前仍是
  早期构建产物。前端产物 178MB（含 `wasm/`、`ruffle/`、`vendor/` 等重型库），
  不宜整体入库。前端要么单独部署、要么把重型库分离后再打进 `app.jar`。
- `release.ps1` 是 PowerShell：开发机是 Windows。Linux 开发机需要 `scp` 手工传
  `app.jar`（3MB，手工也不痛）或自行改写。
