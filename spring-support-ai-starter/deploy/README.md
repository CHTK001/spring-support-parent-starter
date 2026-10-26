# ai-starter 部署（lib 与业务 jar 分离）

日常升级只上传 **业务 jar（约 3MB）**，不上传 920MB 的依赖。这是本目录存在的唯一理由。

## 为什么能分离

fat jar 里 99.7% 是依赖。Spring Boot 默认的 `java -jar` 只认 jar 内 `BOOT-INF/lib`，
所以外置依赖必须换启动方式：

| 方案 | 结果 |
|---|---|
| `java -jar app.jar` | 只认 jar 内 `BOOT-INF/lib`，无法外置 |
| `PropertiesLauncher` + `-Dloader.path=lib` | **失败**：启动即 `NoClassDefFoundError: org/springframework/boot/SpringApplication`，`loader.path` 未把 lib 加进 classloader |
| `-cp "app.jar:lib/*"` + 主类全名 | **可用**：实测 4.5s 启动 |

`start.sh` 用的是第三种。

## 目标机目录

```
/data/ai-starter/
├── app.jar           业务 jar，约 3MB，每次发布替换（旧的留 5 份 app.jar.bak-*）
├── lib/              433 个依赖 jar，约 920MB，只在依赖变化时增量更新
├── jdk/              Temurin 25（自带，不依赖系统 java）
├── config/
│   ├── app.env       部署参数（不入仓库，见 app.env.example）
│   └── oauth.whitelist  鉴权白名单文件（应用自动创建/监听）
├── logs/app.log
├── start.sh          启停脚本
└── docker-compose.yml  MariaDB 11.8 + Redis 7（均只绑 127.0.0.1）
```

## 产物与脚本

| 文件 | 作用 | 在哪执行 |
|---|---|---|
| `release.ps1` | 构建 → （可选）依赖增量 → 上传业务 jar → 重启 → 健康检查 | 开发机（Windows） |
| `start.sh` | `start` / `stop` / `restart` / `status` / `health` / `log` | 目标主机 |
| `app.env.example` | 部署参数模板 | — |

## 首次部署（依赖全量一次）

```powershell
# 1) 构建业务 jar + exec fat jar（后者只为生成依赖清单）
mvn -o -DskipTests -pl spring-support-ai-starter clean package
mvn -o -DskipTests -Dspring-boot.repackage.skip=false -pl spring-support-ai-starter package

# 2) 发布：-SyncDeps 首次会传全量 433 个 jar（约 300MB 压缩后）
pwsh -File deploy/release.ps1 -SyncDeps
```

首次之前目标机需要 `start.sh` 在位，`release.ps1` 会在重启阶段调用它：

```powershell
scp -P 2222 deploy/start.sh <user>@<host>:/data/ai-starter/start.sh
```

## 日常升级（只传 3MB）

```powershell
pwsh -File deploy/release.ps1
```

输出形如：

```
[release] business jar: spring-support-ai-starter-4.0.0.42.jar  2,533 KB  sha256=...
[release] skipping dependency sync (pass -SyncDeps to enable)
[release] uploading business jar (2,533 KB) ...
[release] restarting service
[release] health check (POST /v2/auth/login)
[release] DONE - /v2/auth/login reachable
```

传的东西只有 `app.jar`。**依赖没有变化时不要加 `-SyncDeps`**。

探活默认打 `POST /v2/auth/login`，不是 `/actuator/health`：本应用把框架的
`plugin.actuator` 模块关掉了，`/actuator/health` 实际是 404（`ai-starter.yml` 里那条
白名单只是「放行规则」，不代表端点存在），只认 200 的健康检查会让每次成功的发布都
在最后一步误报 `release failed: service unhealthy`。带上凭据才是端到端检查
（静态账号 + 登录链路 + 发 token）：

```powershell
$env:AI_HEALTH_USER='admin'; $env:AI_HEALTH_PASS='<口令>'
pwsh -File deploy/release.ps1
```

## 依赖变了怎么办

依赖（含本仓其它 starter，比如 `spring-support-oauth-client-starter`）变化时，
需要让 lib 跟上：

```powershell
mvn -o -DskipTests -Dspring-boot.repackage.skip=false -pl spring-support-ai-starter package
pwsh -File deploy/release.ps1 -SyncDeps
```

`-SyncDeps` 的清单来自 **exec fat jar 的 `BOOT-INF/lib`**，不是 `mvn dependency:list`。
后者在本模块给出 442 条、fat jar 只有 433 条：多出的是聚合用的 starter 空壳，
少了的是 provided/optional（如 `lombok`、`jakarta.servlet-api`）。按 442 条同步会在
`lib/` 里留下同一构件的两个版本，classpath 谁先谁后不可控 —— 所以清单只认 fat jar。
实测：`-SyncDeps` 在依赖未变时上传 0 个文件。

> 清单文件必须是 **LF 换行**。它由 PowerShell 生成、远端用 `IFS= read -r n` 逐行读，
> `Set-Content -Encoding ASCII` 写出来是 CRLF，每个文件名尾部会多一个 `\r`，
> `[ -f "$n" ]` 必然失败 → 433 个全部判为缺失 → 每次重新打包上传约 880MB，
> 最终在 `/tmp`（tmpfs，3.9G）上炸掉。现象就是「lib 分离式发不出去」。

## 常用操作

```bash
ssh -p 2222 <user>@<host> 'bash /data/ai-starter/start.sh status'
ssh -p 2222 <user>@<host> 'bash /data/ai-starter/start.sh log 200'
ssh -p 2222 <user>@<host> 'bash /data/ai-starter/start.sh restart'
```

`release.ps1` 开关：

| 开关 | 作用 |
|---|---|
| `-SyncDeps` | 比对并上传缺失/变化的依赖 |
| `-SkipBuild` | 不重新构建，直接用 `target/` 现有产物 |
| `-SkipRestart` | 只上传不重启 |
| `-DryRun` | 只打印计划，不落任何改动 |

## 数据库与 Redis

```bash
cd /data/ai-starter && docker compose up -d
docker compose ps
```

MariaDB 与 Redis 都只绑 `127.0.0.1`，不对外。库 `ai_test`，账号 `ai/ai`。
表结构由应用启动时自动执行 `db/init/*.sql`（FlywayLike，`scan-mode: ALWAYS`）。

## 已知缺口

- `release.ps1` 是 PowerShell：开发机是 Windows，仓里没有可用的 bash。Linux 开发机
  需要自己改写或用 `scp` 手工传 `app.jar`（只有 3MB，手工也不痛）。
- 认证：`plugin.oauth.protocol=static` 的演示账号必须通过命令行/`app.env` 传入，
  **不入仓库**。`/v2/auth/login` 属于登录链路，框架层已强制放行（见
  `AuthClientProperties.ALWAYS_WHITELIST`），不受 `use-default-whitelist: false` 影响。
- 白名单文件 `config/oauth.whitelist` 由应用自动创建并监听，改完即时生效，无需重启。
