# spring-support-docker-starter

Docker 独立管理模块：容器、镜像、网络、卷、镜像库与容器模板的完整管理台后端。全部能力经 SSH 在受控 POSIX 主机上执行 `docker` CLI，不要求暴露 Docker TCP 端口（2375/2376）；镜像库走 `DockerRegistryProvider` SPI，长耗时操作统一以 SSE 实时回显。在 spring-support 体系中，它是 `spring-support-server-starter` 之上的运维能力模块：只消费 server 的主机台账与 SSH 连接设施，不反向依赖 monitor，可被任意宿主（如 `spring-support-monitor-starter`）按需引入。

> **通道说明（4.0.0.52 起，源码 `DockerChannel` / `LocalDockerHostRegistrar`）**：实际执行路径由主机台账 `MsServerHost` 的 `serverHostDockerMode` 字段决定，共三种，均为台账数据、不是 `application.yml` 配置项：
> ① **CMD 通道**（默认）——经 SSH 在目标主机执行 `docker` CLI，本 README 主体流程即此通道；
> ② **API 通道**——经 Docker Engine HTTP API（地址取台账 `serverHostDockerApiUrl`，未配置时按 `http://<主机地址>:2375` 推导），暂不支持交互式终端；
> ③ **本机通道**——主机地址指向部署本服务的这台机器时，直接以本地进程执行 `docker` CLI（不经 SSH），且应用就绪后由 `LocalDockerHostRegistrar` 自动探测本机 Docker 并把 127.0.0.1 注册为主机（幂等，失败仅记日志不影响启动）。

## 1. 架构图

```mermaid
flowchart TB
    FE["宿主应用前端 Docker 管理台（宿主如 spring-support-monitor-starter）"]
    subgraph docker["spring-support-docker-starter"]
        AUTO["DockerAutoConfiguration<br/>@AutoConfiguration + @ComponentScan(com.chua.starter.docker)"]
        WEB["接入层：DockerController / RegistryController / TemplateController(dockerTemplateController)"]
        SVC["核心服务：DockerServiceImpl / RegistryService / TemplateService"]
        SPIX["SPI：DockerRegistryProvider ← DockerHub / GenericV2 实现"]
        STORE["JsonFileStore（~/.monitor/docker/*.json）"]
        AUTO --> WEB --> SVC
        SVC --> SPIX
        SVC --> STORE
    end
    subgraph server["spring-support-server-starter（唯一业务依赖）"]
        DEP["ServerHostService / MsServerHost / ConnectionManager / SshClients<br/>主机台账 + 凭据解析 + SSH 连接池"]
    end
    REMOTE[("受控 POSIX 主机：sshd + docker CLI")]
    FE -->|"HTTP / SSE：/ms/server/docker/**"| WEB
    SVC -->|"主机查询 / 凭据解析 / borrow·release"| DEP
    DEP -->|"SSH 执行 docker CLI"| REMOTE
```

依赖方向：本模块 → `spring-support-server-starter`（外加 web / jackson / swagger-annotations / lombok），不依赖 monitor，避免循环依赖（`DockerAutoConfiguration` 类注释口径）；项目发布类型扩展 `DockerProjectTypeProvider` 经 `META-INF/extensions` 由发布侧 `ProjectTypeProviderRegistry` 消费，不走 HTTP 链路。

## 2. 核心流程图

创建并启动容器（SSE）——最能串联本模块各组件的请求流程：

```mermaid
sequenceDiagram
    participant FE as 前端
    participant DS as DockerServiceImpl
    participant TS as TemplateService
    participant RS as RegistryService
    participant SH as ServerHostService
    participant CM as ConnectionManager
    participant RH as 主机 docker CLI
    FE->>DS: POST /ms/server/docker/{hostId}/containers/run
    DS->>DS: SseEmitter(30min) + runAsync；ensurePosix（Windows 直接 error）
    DS->>TS: fill(request)（templateId 非空时回填未传字段）
    DS->>RS: pullRef(registryId, image)（SPI 拼 registry host + namespace）
    DS->>SH: get(hostId) + resolveCredential
    DS->>CM: borrow(host, credential)
    DS->>RH: docker login（有凭据时，heredoc --password-stdin）→ docker pull（pull≠false）→ docker run -d（buildRunCommand 白名单校验 + 单引号转义）
    DS-->>FE: step / log / done(容器ID) / error
    DS->>CM: release(hostId)
```

其余 SSE 流程（卸载容器/镜像、拉取镜像、日志流、交互终端）复用同一「borrow → 执行 → step/log/done/error → release」骨架；日志流与终端额外发 `session` 事件，终端数据走 `output`、结束发 `closed`。

## 3. 能力清单

入口前缀 `ServerConstants.API_PREFIX = "/ms/server"`（源码常量）。

### 3.1 容器 / 镜像 / 网络 / 卷（`DockerController` → `DockerServiceImpl`，hostId 维度）

| 能力域 | 关键类 / 方法 | 入口（REST） |
|---|---|---|
| 能力探测 | `DockerServiceImpl#capability` | `GET /ms/server/docker/{hostId}/capability` |
| 容器列表 / 详情 | `#containers` / `#containerDetail` | `GET .../containers?all=`、`GET .../containers/{id}` |
| 生命周期（start/stop/restart/pause/unpause/kill） | `#containerAction` | `POST .../containers/{id}/{action}` |
| 删除容器（同步 / SSE） | `#removeContainer` / `#removeContainerStream` | `DELETE .../containers/{id}?force=`、`POST .../containers/{id}/remove-stream` |
| 创建并启动容器（SSE） | `#runContainer` | `POST .../containers/run` |
| 容器日志（一次性 / SSE 流） | `#containerLogs` / `#streamContainerLogs` | `GET .../containers/{id}/logs?tail=`、`GET .../containers/{id}/logs/stream?tail=` |
| 交互终端（PTY）+ 输入 + 关闭 | `#execContainer` / `#execInput` / `#execClose` | `POST .../containers/{id}/exec`、`.../exec/input`、`.../exec/close` |
| 镜像：列表 / 拉取（SSE）/ 删除（同步 / SSE） | `#images` / `#pullImage` / `#removeImage` / `#removeImageStream` | `GET .../images`、`POST .../images/pull?image=&registryId=`、`DELETE .../images/{imageId}?force=`、`POST .../images/{imageId}/remove-stream` |
| 网络 / 卷：列表·创建·删除 | `#networks` / `#createNetwork` / `#removeNetwork` / `#volumes` / `#createVolume` / `#removeVolume` | `GET\|POST .../networks`、`DELETE .../networks/{id}`、`GET\|POST .../volumes`、`DELETE .../volumes/{name}` |
| 清理悬空资源 | `#prune`（`docker system prune -af --volumes`） | `POST .../prune` |

### 3.2 镜像库与模板（全局维度，`RegistryController` / `TemplateController`）

| 能力域 | 关键类 / 方法 | 入口（REST） |
|---|---|---|
| 镜像库列表 / 保存 / 删除 | `RegistryService#list` / `#save` / `#delete` | `GET\|POST /ms/server/docker/registries`、`DELETE .../registries/{id}` |
| 连通性测试 / 库类型 / 标签 | `#test` / `#types` / `#tags` | `POST .../registries/{id}/test`、`GET .../registries/types`、`GET .../registries/{id}/tags?image=` |
| 在线检索镜像（跨库并发） | `#search` | `GET /ms/server/docker/images/search?keyword=&registryId=&limit=` |
| 模板列表 / 保存 / 删除 | `TemplateService#list` / `#save` / `#delete` | `GET\|POST /ms/server/docker/templates`、`DELETE .../templates/{id}` |

### 3.3 非 REST 能力

| 能力域 | 关键类 | 触发方式 |
|---|---|---|
| 自动配置 | `com.chua.starter.docker.config.DockerAutoConfiguration` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| 镜像库 SPI | `DockerRegistryProvider`（内置 `dockerhub` / `generic-v2` 实现） | Spring Bean 集合，`RegistryService` 构造器按 `type()` 建索引 |
| 项目发布类型扩展 | `DockerProjectTypeProvider`（`@Spi(value = "docker", order = 100)`） | `META-INF/extensions/com.chua.starter.server.support.spi.ProjectTypeProvider` |
| JSON 持久化 / HTTP 工具 | `JsonFileStore`（包级私有）/ `RegistryHttp`（`spi.impl` 包私有） | 服务内部使用；`RegistryHttp` 共享 `HttpClient`（连接超时 5s、读取超时 8s）+ Basic Auth |

SSE 事件名契约（`DockerService` 注释即契约）：`step`（阶段说明）、`log`（输出增量）、`done`（成功）、`error`（失败原因）。

## 4. Bean 能力矩阵

装配总说明：`DockerAutoConfiguration` 上没有任何 `@ConditionalOnXxx`，只做 `@ComponentScan(basePackages = "com.chua.starter.docker")`；下表 Bean 全部为**无条件装配**——jar 在 classpath 且自动装配未被排除即注册。全模块没有任何 `@ConditionalOnMissingBean`，因此「覆盖」只能靠 `spring.main.allow-bean-definition-overriding=true` 下的同名 Bean 覆盖（默认重名即启动失败）。

| Bean 名 | 类型 | 装配条件 | 可否覆盖 | 典型用途 |
|---|---|---|---|---|
| `dockerController` / `registryController` | `DockerController` / `RegistryController`（`@RestController`） | 无条件（组件扫描） | 需开 `allow-bean-definition-overriding`；默认重名失败 | 一般不注入，经 HTTP 调用 |
| `dockerTemplateController` | `com.chua.starter.docker.controller.TemplateController`（`@RestController("dockerTemplateController")`） | 无条件（组件扫描）；显式 bean 名规避与模板模块同名 `TemplateController` 冲突 | 同上 | 一般不注入 |
| `dockerServiceImpl` | `DockerServiceImpl`（`@Service`，实现 `DockerService`） | 无条件（组件扫描） | 同上；另注册同类型不同名 Bean 会导致按类型注入歧义，需 `@Primary` | 宿主注入 `DockerService` 接口复用能力 |
| `registryService` | `RegistryService`（`@Service`） | 无条件（组件扫描）；构造器注入 `List<DockerRegistryProvider>` | 同上 | 镜像库 CRUD / 检索 / `pullRef` / `config(id)` |
| `templateService` | `TemplateService`（`@Service`） | 无条件（组件扫描） | 同上 | 模板 CRUD 与 `fill` 回填 |
| `dockerHubRegistryProvider` / `genericV2RegistryProvider` | `DockerHubRegistryProvider`（`type()="dockerhub"`）/ `GenericV2RegistryProvider`（`type()="generic-v2"`），均 `@Component` | 无条件（组件扫描） | 新增优先：同 `type()` 的 Bean 后到者覆盖索引（`LinkedHashMap#put`） | 由 `RegistryService` 收集，不推荐直接注入 |

非 Bean 关键组件：`DockerProjectTypeProvider`（`META-INF/extensions` 注册，不进 Spring 容器）、`JsonFileStore`（服务构造时 `new`，包级私有 final 类）、`RegistryHttp`（`spi.impl` 包私有静态工具）。

## 5. 完整配置

**本模块没有 `@ConfigurationProperties`、没有 `@Value`，不存在任何 `application.yml` 配置键**（全模块源码逐文件核验）。是否启用 = 宿主是否引入本依赖，无 `docker.enabled` 之类开关。

模块固定使用两个本机持久化文件（路径在构造时以 `System.getProperty("user.home")` 写死，不可配置）：

| 固定路径 | 内容 | 写入保证（`JsonFileStore`） |
|---|---|---|
| `~/.monitor/docker/registries.json` | 镜像库配置（含明文密码） | UTF-8 无 BOM；临时文件 + `ATOMIC_MOVE` 原子替换；`ReentrantLock` 串行化读改写 |
| `~/.monitor/docker/templates.json` | 容器模板 | 同上 |

以下行为参数为代码内常量 / 请求级默认值，同样不可经配置修改：

| 参数 | 值 / 规则 | 代码位置 |
|---|---|---|
| SSE 超时 / 输出截断 | 30 分钟（`SSE_TIMEOUT_MILLIS`）/ 64KB（`OUTPUT_TRUNCATE`） | `DockerServiceImpl` |
| 命令超时常量 | `EXEC_TIMEOUT_SECONDS = 30` 仅在类中声明，当前未传入任何 `exec` 调用，实际不生效（远程命令没有本模块级超时，取决于 SSH 会话默认行为） | `DockerServiceImpl` |
| action / 重启策略白名单 | `start`/`stop`/`restart`/`pause`/`unpause`/`kill`；`no`/`always`/`unless-stopped`/`on-failure` | `DockerServiceImpl#containerAction` / `#buildRunCommand` |
| 字段格式白名单 | 镜像名 `[A-Za-z0-9][A-Za-z0-9._/:@-]*`、端口 `\d{1,5}:\d{1,5}(/(tcp\|udp))?`、环境变量 `KEY=VALUE`、卷映射 `src:dst`、名称 `[A-Za-z0-9][A-Za-z0-9_.-]*` | `DockerServiceImpl#buildRunCommand` 等 |
| 列表 / 日志 / 终端默认值 | 容器列表 `all` 缺省 `true`；日志 `tail` 一次性缺省 200、流缺省 100；终端默认命令 `/bin/sh` | `DockerServiceImpl` |
| 检索与目录上限 | 检索 `limit` 缺省 25、上限 100（`DEFAULT_LIMIT`/`MAX_LIMIT`），关键词 <2 字符返回空；`CATALOG_MAX=500`（`limit*CATALOG_FACTOR(10)` 封顶）；`TAG_LIMIT=100` | `RegistryService#search` / `GenericV2RegistryProvider` / `DockerHubRegistryProvider` |
| 内置库 / Hub 常量 / login 标记 | 内置库 id 固定 `dockerhub`（`BUILTIN_ID`）、`builtin=true`、不可删除、可改地址/凭据；密码「不修改」回传 `******`（`PASSWORD_MASK`）或空串；Hub API `https://hub.docker.com`（`HUB_API`，检索不受镜像加速地址影响）；heredoc 标记 `CHUA_DOCKER_LOGIN_EOF`（密码经标准输入） | `RegistryService` / `DockerHubRegistryProvider` / `DockerServiceImpl#LOGIN_EOF` |

### 5.1 逐项配置说明（按功能分组）

本模块没有可经 `application.yml` 调整的配置项，因此**不存在逐项 YAML 配置**。下表把上表的行为参数与相关数据项按功能分组，补齐含义与默认值；「YAML 可配」一列恒为否——取值来源只有代码常量、请求参数、主机台账数据三类：

| 功能分组 | 参数 / 数据项 | 含义 | 默认值 | YAML 可配 |
|---|---|---|---|---|
| 执行通道 | 台账字段 `serverHostDockerMode` | 容器操作通道：`CMD`=SSH+docker CLI、`API`=Docker Engine HTTP API | `CMD` | 否（server 模块主机台账字段，经主机管理接口/页面维护） |
| 执行通道 | 台账字段 `serverHostDockerApiUrl` | API 通道的 Docker Engine 地址 | 空 → 按 `http://<主机地址>:2375` 推导 | 否（同上） |
| SSE 与输出 | `SSE_TIMEOUT_MILLIS` | SSE 长任务（建容器/拉镜像/日志/终端）超时 | 30 分钟 | 否 |
| SSE 与输出 | `OUTPUT_TRUNCATE` | 单条输出截断阈值 | 64KB | 否 |
| 创建容器 | 重启策略白名单 | `restartPolicy` 允许值 | `no` / `always` / `unless-stopped` / `on-failure` | 否 |
| 创建容器 | 字段格式白名单 | 镜像名、端口、环境变量、卷映射、容器名的合法性校验（正则见第 5 节参数表） | — | 否 |
| 容器列表与日志 | `all` / `tail` | 列表是否含停止容器；日志尾部行数 | `all=true`；一次性日志 200、流式日志 100 | 否（请求级参数） |
| 交互终端 | 默认命令 | 未传命令时进入的 shell | `/bin/sh` | 否 |
| 镜像检索 | `DEFAULT_LIMIT` / `MAX_LIMIT` / `CATALOG_MAX` / `TAG_LIMIT` | 检索条数缺省 / 上限、目录拉取封顶、标签数上限 | 25 / 100 / 500 / 100 | 否 |
| 内置镜像库 | `BUILTIN_ID` / `PASSWORD_MASK` / `HUB_API` | 内置 Docker Hub 库 id、密码回显掩码、Hub 检索 API 地址 | `dockerhub` / `******` / `https://hub.docker.com` | 否 |
| 本地持久化 | 存储目录 | 镜像库与模板 JSON 落盘位置 | `~/.monitor/docker/`（由 `user.home` 推导） | 否（唯一相关的 JVM 标准属性是 `user.home`，见 5.2） |
| 本机自注册 | `LocalDockerHostRegistrar` 触发时机 | 应用就绪后探测本机 `docker`，可用则把 `127.0.0.1` 注册为主机（CMD 通道、免凭据） | `ApplicationReadyEvent` 后异步执行 | 否 |

### 5.2 完整 YAML 使用示例

宿主应用引入本依赖后，`application.yml` **不需要（也没有）任何本模块配置键**。下面给出一个可运行的宿主应用最小 YAML，注释标明与本模块相关的全部事实：

```yaml
server:
  port: 8080            # 宿主应用端口；本模块的 /ms/server/docker/** 随宿主暴露在该端口下

spring:
  application:
    name: my-docker-console

# 注意：本模块不读取任何 docker.* 或其它自定义配置键（全模块无 @ConfigurationProperties、无 @Value）。
# 行为定制只能通过：
#   1) server 模块主机台账数据（serverHostDockerMode / serverHostDockerApiUrl 等，经主机管理接口维护）；
#   2) JVM 标准属性 user.home（决定 ~/.monitor/docker/ 持久化目录，一般不要改动）。
# 因此这里刻意不写 docker: 配置段——写了也不会被读取。
```

若确需迁移 `~/.monitor/docker/` 的落盘位置，只能以 JVM 参数整体调整用户目录（影响所有依赖 `user.home` 的组件，慎用）：

```text
java -Duser.home=/var/lib/myapp -jar app.jar
```

### 5.3 生产注意事项

- **凭据明文落盘**：镜像库密码明文保存在 `~/.monitor/docker/registries.json`（接口回传为 `******` 掩码，但文件本身是明文）。必须把该目录纳入主机安全管控：限制目录权限、纳入备份与审计策略。
- **无模块级鉴权**：本模块接口挂在宿主的 `/ms/server/docker/**` 前缀下，认证与授权完全依赖宿主安全框架/网关。生产环境务必对该前缀启用登录与权限控制，避免容器管理台裸奔。
- **多实例会话粘滞**：交互终端（exec）会话保存在内存 `ConcurrentHashMap`，多实例部署时 `POST .../exec/input`、`.../exec/close` 必须路由到建立会话的同一实例；SSE 长连接要求网关不缓冲响应、连接保持时间不小于模块侧 30 分钟超时。
- **命令白名单是安全边界**：创建容器仅接受白名单字段并做格式校验（见第 5 节参数表），不要为「灵活」绕过校验拼接任意 shell 命令。
- **通道与平台限制**：CMD 通道要求远程主机为 POSIX（远程 Windows 主机被 `ensurePosix` 拒绝）；API 通道暂不支持交互式终端；本机通道以本地进程执行 `docker` CLI，不受 SSH/POSIX 限制。
- **清理操作影响面大**：`POST .../prune` 等价 `docker system prune -af --volumes`，会删除目标主机上全部未使用容器/镜像/网络/卷，生产主机慎用，并在前端保留二次确认。
- **删除类操作不可逆**：容器/镜像/网络/卷删除接口均无回收站，前端集成时应保留确认框；确认提示文案与操作日志建议落审计。

## 6. 使用方式

### 6.1 Maven 坐标

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-docker-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

依赖会传递带入 `spring-support-server-starter`（主机台账 + SSH 连接池）、`spring-boot-starter-web`、`jackson-databind`、`swagger-annotations`；`lombok` 为 `optional`，仅编译本模块时需要。pom 设置 `spring-boot.repackage.skip=true`：只产出标准库 jar，**不能 `java -jar` 直接启动**，必须由宿主应用依赖后运行。

### 6.2 最小启用示例

无需任何 yml 配置（见第 5 节），引入依赖即整体装配：

```java
@SpringBootApplication
public class App {
    public static void main(String[] args) { SpringApplication.run(App.class, args); }  // 无需扫描 com.chua.starter.docker
}

@RestController @RequiredArgsConstructor
class DemoController {
    private final DockerService dockerService;  // 注入接口，实现为 DockerServiceImpl
    @GetMapping("/demo/capability")
    public ReturnResult<Map<String, Object>> capability() { return dockerService.capability(1L); }
}
```

创建容器（SSE）：

```java
DockerCreateRequest request = new DockerCreateRequest();  // @Data POJO，setter 赋值，无 Builder
request.setImage("nginx:alpine");
request.setPorts(List.of("8080:80"));
request.setVolumes(List.of("/data/nginx:/usr/share/nginx/html"));
request.setRestartPolicy("unless-stopped");
request.setPull(true);
request.setRegistryId("dockerhub");   // 可空；非空时先按库凭据 docker login
request.setTemplateId(null);          // 可空；非空时先按模板回填未传字段
SseEmitter emitter = dockerService.runContainer(1L, request);
```

前置条件：① 宿主已接入 server 模块主机台账（存在 `MsServerHost` 记录且 `ServerHostService#resolveCredential` 能解析 SSH 凭据，否则报「主机不存在」或凭据解析失败；本机通道由 `LocalDockerHostRegistrar` 自动注册，免手工添加）；② CMD 通道的远程主机须为 POSIX（Linux/macOS）——所有入口先 `ensurePosix(hostId)`，远程 Windows 主机抛 `UnsupportedOperationException`（本机通道不受此限，本服务所在机器是 Windows 时也可管理本机 Docker）；③ CMD 通道无需暴露 Docker TCP 端口，全部经 SSH 执行 `docker` CLI；API 通道则要求主机台账配置 `serverHostDockerApiUrl`（未配置时按 `http://<主机地址>:2375` 推导）。

### 6.3 可选依赖说明

| 依赖 | 是否必需 | 缺失影响 |
|---|---|---|
| `spring-support-server-starter` | 必需（传递带入） | 无法编译/启动：主机台账、凭据解析、连接池、`ServerConstants`、`ProjectTypeProvider` 扩展点均来自它 |
| `spring-boot-starter-web` | 必需（传递带入） | 无 `@RestController` / `SseEmitter`，模块无法工作 |
| `jackson-databind` | 必需（传递带入） | `docker --format '{{json .}}'` 输出与本地 JSON 持久化无法解析 |
| `swagger-annotations` / `lombok` | 必需（传递带入）/ 编译期 `optional` | 前者仅 `@Tag`/`@Operation`/`@Schema` 注解、无运行时逻辑；后者仅编译本模块源码时需要，宿主运行期不需要 |

宿主无需引入 docker-java 等 Docker 客户端库——CMD 通道只经 SSH 调用主机上的 `docker` 命令；API 通道经 server 模块提供的 Docker Engine HTTP 客户端（`DockerChannel`/`DockerEngineClient`），同样无需额外依赖。

## 7. 使用场景

**适用：**

- 需要集中式 Docker 管理台，统一管理多台受控 POSIX 主机的容器/镜像/网络/卷，且不想暴露 Docker TCP 端口或安装额外 Agent（复用已有 SSH 通道）；长耗时操作（拉镜像、建容器、卸载、看日志、进终端）需要 SSE 实时进度回显。
- 「在线选镜像 → 选 tag → 一键安装」流程，且镜像来源不止 Docker Hub（私有 Harbor / Nexus / registry:2 走 `generic-v2`）。
- 项目发布需要「按 Dockerfile 构建镜像并运行容器」的项目类型（`DockerProjectTypeProvider`）。

**不适用 / 慎用：**

- 远程 Windows 宿主机容器管理（`ensurePosix` 对远程 Windows 直接拒绝，无 Windows SSH 容器/命名管道实现；本机通道除外——本服务所在机器是 Windows 时可管理本机 Docker）；Kubernetes / 编排场景（只面向单机 `docker` CLI，与主机工作台的 K8s 能力解耦）。
- 高安全 / 多实例场景需注意：镜像库密码明文落 `~/.monitor/docker/registries.json`；`exec` 会话在内存 `ConcurrentHashMap`，多实例必须会话粘滞；命令仅白名单字段，不接受任意 shell 透传（有意限制）。

**与相邻模块的取舍：**

| 相邻模块 | 关系 | 取舍 |
|---|---|---|
| `spring-support-server-starter` | 唯一业务依赖：主机台账、凭据解析、SSH 连接池 | 主机/凭据维护在 server 侧；本模块只消费其 Service 契约，不直连其持久层 |
| `spring-support-control-starter` | 在 monitor 中并列引入、互相无依赖 | 远程控制（WebRTC 信令）选 control；容器管理选本模块；协议与页面独立，可同时使用 |
| `spring-support-monitor-starter` | 聚合宿主，自动装配本模块全部接口 | 要全家桶就依赖 monitor；只要 Docker 能力则直接依赖本模块，避免带入多余 Controller/任务（R5「依赖即带入」） |
| Docker SDK（docker-java 等） | 本模块未采用 | 需要编程式 Docker API 细粒度控制时自行引入 SDK；本模块定位是「SSH + CLI」的低侵入管理台 |

## 8. 扩展点

### 8.1 镜像库 SPI：`DockerRegistryProvider`（Spring Bean）

实现 `com.chua.starter.docker.spi.DockerRegistryProvider` 并声明为 Spring Bean（如 `@Component`）即可新增镜像库类型，无需改动服务与控制器。`RegistryService` 构造器注入 `List<DockerRegistryProvider>` 并按 `type()` 建 `LinkedHashMap` 索引，`GET .../registries/types` 立即列出新增类型；**它不是 JDK SPI，不要在 `META-INF/services` 登记**；查找不到实现时 `RegistryService#providerFor` 抛 `IllegalStateException`（「没有可用的镜像库实现」），不做静默降级。

| 方法 | 作用 | 约定 |
|---|---|---|
| `String type()` | 库类型标识，持久化在 `RegistryConfig.type` | 必须唯一；重复时后注册者覆盖索引 |
| `String displayName()` | 前端「镜像库类型」下拉展示名 | — |
| `List<Map<String,Object>> search(RegistryConfig, String keyword, int limit)` | 在线检索 | 统一结构：`name`/`description`/`stars`/`official`/`pullRef` |
| `List<String> tags(RegistryConfig, String image)` | 镜像标签列表 | 查询失败返回空列表 |
| `String pullRef(RegistryConfig, String image)` | 拼出可直接 `docker pull\|run` 的完整引用 | 后端统一用 `pullRef` 拉取，前端不拼 host/命名空间 |
| `boolean test(RegistryConfig)` | 连通性 / 凭据测试 | — |

SPI 入参 `RegistryConfig` 字段：`id`/`name`/`type`/`url`/`username`/`password`（仅服务端内部可见，接口只回 `hasPassword`）/`namespace`/`enabled`（null 视为启用，见 `enabledOrDefault()`）/`builtin`/`createdAt`/`updatedAt`。

### 8.2 项目发布类型：`ProjectTypeProvider`（`META-INF/extensions`）

接口 `com.chua.starter.server.support.spi.ProjectTypeProvider` 来自 server 模块，本模块自带实现 `DockerProjectTypeProvider`：

- 注册：`@Spi(value = "docker", order = 100)` + 扩展点文件 `META-INF/extensions/com.chua.starter.server.support.spi.ProjectTypeProvider`，内容一行：`com.chua.starter.docker.provider.project.DockerProjectTypeProvider`
- 语义：主入口 = 容器名；`scripts(windows, mainEntry)` 走 `ProjectScripts.windowsDocker/linuxDocker`（先 `docker build`，成功后才重建容器，构建失败不影响旧容器）；单文件产物直接投放到构建上下文根目录，压缩包先解压；`pidFile()` 为 `app.pid`；POSIX 前台命令 `docker start -a '<容器名>'`
- 自定义新类型：在自己 jar 里放同名扩展点文件并追加实现类全限定名即可，发布侧 `ProjectTypeProviderRegistry` 首次解析时按 `type()` 建索引；不需要改本模块

### 8.3 可注入契约（非 SPI）

`DockerService`、`RegistryService`、`TemplateService` 是普通 Spring Bean，宿主可直接注入组合复用；它们不是扩展点——替换实现的约束见第 4 节「可否覆盖」。

## 9. 常见问题

**Q1：引入依赖后接口 404 / Bean 不生效？**
确认三点：① 依赖确实在宿主 classpath；② 宿主未排除自动装配——本模块靠 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `DockerAutoConfiguration`（Boot 4 下没有、也不会读 `spring.factories` 的自动装配登记）；③ 宿主**不需要**把 `com.chua.starter.docker` 加进自己的 `@ComponentScan`，自动配置类已代为扫描。若宿主自造容器或禁用自动装配，需自行 `@Import(DockerAutoConfiguration.class)`。

**Q2：启动报 `ConflictingBeanDefinitionException: templateController`？**
本模块 `TemplateController` 已显式命名为 `dockerTemplateController`（源码注释：避免与模板模块 `/api/template` 的同名 `TemplateController` 冲突——两个 starter 被同一应用引入时默认 bean 名都是 `templateController`，组件扫描会抛 `ConflictingBeanDefinitionException`）。若再遇其它同名冲突（如自定义了同名 Bean），要么改名，要么开启 `spring.main.allow-bean-definition-overriding=true`；注意覆盖本模块 Bean 后 SSE/SSH 行为由你的实现负责。

**Q3：需要开放 Docker 2375/2376 端口吗？有没有 `docker.enabled` 开关？与 Spring Boot 4.x 兼容吗？**
端口与开关都不需要：CMD 通道所有命令经 SSH（默认 22）在目标主机执行 `docker` CLI（API 通道走 Docker Engine HTTP API、本机通道走本地进程，见第 1 节通道说明）；模块没有 `docker.enabled` 之类开关，引入依赖即装配（见第 5 节）；接口路径固定在 `/ms/server/docker` 下，网关放行按此前缀配置。兼容性方面：模块随父工程基于 Spring Boot 4.1.2 / Java 25 构建（`maven-compiler-plugin` source/target 25），自动装配用 Boot 3/4 标准的 `AutoConfiguration.imports`，Web/SSE 走 jakarta 命名空间，无 javax 遗留。

**Q4：主机没装 Docker 会怎样？多实例部署交互终端（exec）有什么注意点？**
主机未装 Docker 时 `GET .../capability` 不报错，返回 `{"installed": false, "version": null, "compose": false, "runtime": "docker"}`（Windows 主机同样）；列表/操作类接口报「主机未安装或未启动 Docker」错误（`dockerFail` 按 `command not found` / `cannot connect to the docker daemon` 等关键字区分「未安装」与「执行失败」）。`execContainer` 的会话状态在 `DockerServiceImpl#execSessions`（内存 `ConcurrentHashMap`），进程重启即丢；多实例部署时 `POST .../exec/input`、`.../exec/close` 必须路由到建立会话的同一实例（会话粘滞），否则报「会话不存在或已关闭」。
