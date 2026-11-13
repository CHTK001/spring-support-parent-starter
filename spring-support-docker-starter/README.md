# spring-support-docker-starter

Docker 独立模块：容器、镜像、网络、卷、镜像库与容器模板的完整管理台后端。
全部能力经 SSH 在受控主机上执行 `docker` CLI，不要求额外暴露 Docker TCP 端口；
镜像库走 `DockerRegistryProvider` SPI，长耗时操作统一用 SSE 实时回显。

## 一、模块能力

`ServerConstants.API_PREFIX = "/ms/server"`，下表路径均已展开为完整路径。

### 容器与镜像

| 能力域 | 关键类 / 方法 | 入口（REST 路径） |
|---|---|---|
| 能力探测 | `DockerServiceImpl#capability` | `GET /ms/server/docker/{hostId}/capability` |
| 容器列表 | `DockerServiceImpl#containers` | `GET /ms/server/docker/{hostId}/containers?all=` |
| 容器详情 | `DockerServiceImpl#containerDetail` | `GET /ms/server/docker/{hostId}/containers/{id}` |
| 生命周期操作 | `DockerServiceImpl#containerAction` | `POST /ms/server/docker/{hostId}/containers/{id}/{action}` |
| 删除容器（同步） | `DockerServiceImpl#removeContainer` | `DELETE /ms/server/docker/{hostId}/containers/{id}?force=` |
| 删除容器（SSE） | `DockerServiceImpl#removeContainerStream` | `POST /ms/server/docker/{hostId}/containers/{id}/remove-stream` |
| 创建并启动容器（SSE） | `DockerServiceImpl#runContainer` | `POST /ms/server/docker/{hostId}/containers/run` |
| 容器日志（一次性） | `DockerServiceImpl#containerLogs` | `GET /ms/server/docker/{hostId}/containers/{id}/logs?tail=` |
| 容器日志流（SSE） | `DockerServiceImpl#streamContainerLogs` | `GET /ms/server/docker/{hostId}/containers/{id}/logs/stream?tail=` |
| 进入容器（SSE） | `DockerServiceImpl#execContainer` | `POST /ms/server/docker/{hostId}/containers/{id}/exec` |
| 交互输入 | `DockerServiceImpl#execInput` | `POST /ms/server/docker/{hostId}/containers/{id}/exec/input` |
| 关闭交互会话 | `DockerServiceImpl#execClose` | `POST /ms/server/docker/{hostId}/containers/{id}/exec/close` |
| 镜像列表 | `DockerServiceImpl#images` | `GET /ms/server/docker/{hostId}/images` |
| 拉取镜像（SSE） | `DockerServiceImpl#pullImage` | `POST /ms/server/docker/{hostId}/images/pull?image=&registryId=` |
| 删除镜像（同步） | `DockerServiceImpl#removeImage` | `DELETE /ms/server/docker/{hostId}/images/{imageId}?force=` |
| 删除镜像（SSE） | `DockerServiceImpl#removeImageStream` | `POST /ms/server/docker/{hostId}/images/{imageId}/remove-stream` |

### 网络、卷与清理

| 能力域 | 关键类 / 方法 | 入口（REST 路径） |
|---|---|---|
| 网络列表 | `DockerServiceImpl#networks` | `GET /ms/server/docker/{hostId}/networks` |
| 创建网络 | `DockerServiceImpl#createNetwork` | `POST /ms/server/docker/{hostId}/networks` |
| 删除网络 | `DockerServiceImpl#removeNetwork` | `DELETE /ms/server/docker/{hostId}/networks/{id}` |
| 卷列表 | `DockerServiceImpl#volumes` | `GET /ms/server/docker/{hostId}/volumes` |
| 创建卷 | `DockerServiceImpl#createVolume` | `POST /ms/server/docker/{hostId}/volumes` |
| 删除卷 | `DockerServiceImpl#removeVolume` | `DELETE /ms/server/docker/{hostId}/volumes/{name}` |
| 清理悬空资源 | `DockerServiceImpl#prune` | `POST /ms/server/docker/{hostId}/prune` |

### 镜像库与模板（不带 hostId）

| 能力域 | 关键类 / 方法 | 入口（REST 路径） |
|---|---|---|
| 镜像库列表 | `RegistryService#list` | `GET /ms/server/docker/registries` |
| 新增 / 更新镜像库 | `RegistryService#save` | `POST /ms/server/docker/registries` |
| 删除镜像库 | `RegistryService#delete` | `DELETE /ms/server/docker/registries/{id}` |
| 连通性测试 | `RegistryService#test` | `POST /ms/server/docker/registries/{id}/test` |
| 可用库类型 | `RegistryService#types` | `GET /ms/server/docker/registries/types` |
| 镜像标签 | `RegistryService#tags` | `GET /ms/server/docker/registries/{id}/tags?image=` |
| 在线检索镜像 | `RegistryService#search` | `GET /ms/server/docker/images/search?keyword=&registryId=&limit=` |
| 模板列表 | `TemplateService#list` | `GET /ms/server/docker/templates` |
| 新增 / 更新模板 | `TemplateService#save` | `POST /ms/server/docker/templates` |
| 删除模板 | `TemplateService#delete` | `DELETE /ms/server/docker/templates/{id}` |

### 装配与扩展点

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 自动配置 | `com.chua.starter.docker.config.DockerAutoConfiguration` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| 镜像库 SPI | `com.chua.starter.docker.spi.DockerRegistryProvider` | Spring Bean 集合，`RegistryService` 按 `type()` 建索引 |
| 内置实现 | `DockerHubRegistryProvider`（`dockerhub`）、`GenericV2RegistryProvider`（`generic-v2`） | 由 `@Component` 自动注册 |
| 项目类型扩展 | `com.chua.starter.docker.provider.project.DockerProjectTypeProvider` | `META-INF/extensions/com.chua.starter.server.support.spi.ProjectTypeProvider` |

返回结构约定：容器列表项为 `id` / `shortId` / `name` / `image` / `state` / `status` /
`ports` / `network` / `runningFor` / `createdAt`；镜像项为 `id` / `shortId` / `repository` /
`tag` / `size` / `createdAt`；`capability` 为 `installed` / `version` / `compose` / `runtime`。
SSE 事件名（`DockerService` 注释即契约）：`step`（阶段说明）、`log`（命令行输出增量）、
`done`（成功）、`error`（失败原因）；日志流与交互式终端另发 `session`（会话标识），
终端结束时发 `closed`。

## 二、依赖与引入

Maven 坐标（版本取父工程真实值）：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-docker-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 版本来源 | 作用 |
|---|---|---|
| `spring-support-server-starter` | `${project.version}`（`4.0.0.51`） | 主机台账 `MsServerHost`、凭据解析 `ServerHostService`、连接池 `ConnectionManager` / `SshClients`、`ServerConstants`、`RemoteExecResult` |
| `spring-boot-starter-web` | Spring Boot BOM | `@RestController`、`SseEmitter` |
| `jackson-databind` | Spring Boot BOM | `docker ... --format '{{json .}}'` 解析、`~/.monitor/docker/*.json` 持久化 |
| `swagger-annotations` | Spring Boot BOM | `@Tag` / `@Operation` / `@Schema` |
| `lombok` | 父工程 `1.18.48`，`optional` | `@Data` / `@RequiredArgsConstructor` / `@Slf4j` |

pom 里设置了 `<spring-boot.repackage.skip>true</spring-boot.repackage.skip>`，
并让 `spring-boot-maven-plugin` 读取该属性：本模块**只产出标准库 jar**，
不能被 `java -jar` 直接启动，必须由宿主应用依赖后运行。
编译目标为 `source/target 25`（父工程 `<java.version>25</java.version>`）。

## 三、配置项

**无 `@ConfigurationProperties`，复用宿主配置。** 本模块没有 `DockerProperties`
之类的属性类，源码中也没有 `@Value`；所有行为参数来自请求体/查询参数与宿主已有配置。

模块自身固定使用两个本机持久化文件（不可配置，路径在构造时写死）：

| 文件 | 内容 | 代码位置 |
|---|---|---|
| `~/.monitor/docker/registries.json` | 镜像库配置（含密码） | `RegistryService` 构造函数 |
| `~/.monitor/docker/templates.json` | 容器模板 | `TemplateService` 构造函数 |

写入规则由 `JsonFileStore` 统一保证：UTF-8、同目录临时文件 + `ATOMIC_MOVE` 替换、
`ReentrantLock` 串行化「读—改—写」。是否启用本模块由宿主是否引入依赖决定，没有 `docker.enabled` 开关。

## 四、快速开始

**第一步：引入依赖。** 需要 `spring-support-docker-starter`，
它会把 `spring-support-server-starter` 一并带入；宿主要已有主机台账数据
（`MsServerHost` 记录 + 可解析的 SSH 凭据），否则 `capability` 等接口会报「主机不存在」或凭据解析失败。

**第二步：确认目标主机是 POSIX。** 所有接口入口先执行 `ensurePosix(hostId)`，
Windows 主机会直接抛 `UnsupportedOperationException`（"该主机为 Windows，暂不支持 Docker 管理"）；
主机上没有 Docker 不抛错：`GET .../capability` 返回 `{"installed": false, "version": null, "compose": false, "runtime": "docker"}`。

**第三步：从能力探测开始用。** 先调 `GET /ms/server/docker/{hostId}/capability`
判断是否已安装与是否有 compose，再拉容器/镜像列表；创建容器走
`POST /ms/server/docker/{hostId}/containers/run`（SSE，边拉镜像边建容器）。

## 五、运行示例

### 5.1 调用示例

**本模块无链式 API。** `DockerService` 的方法是普通接口方法，返回
`ReturnResult<...>` 或 `SseEmitter`；`DockerCreateRequest` / `DockerTemplate` /
`DockerNetworkRequest` / `DockerVolumeRequest` 都是 `@Data`（或手写 getter/setter）的
POJO，用 setter 逐项赋值，**没有 Builder，也没有返回 `this` 的链式写法**。

注入服务并查询（与控制器同一契约）：

```java
@Autowired
private DockerService dockerService;

ReturnResult<Map<String, Object>> capability = dockerService.capability(1L);
ReturnResult<List<Map<String, Object>>> containers = dockerService.containers(1L, true);
ReturnResult<Map<String, Object>> detail = dockerService.containerDetail(1L, "nginx");
ReturnResult<Boolean> started = dockerService.containerAction(1L, "nginx", "restart");
ReturnResult<String> logs = dockerService.containerLogs(1L, "nginx", 200);
```

`containerAction` 的 `action` 只接受
`start` / `stop` / `restart` / `pause` / `unpause` / `kill`（见方法内白名单）。

创建容器（步骤式，setter 赋值；SSE 由前端消费）：

```java
DockerCreateRequest request = new DockerCreateRequest();
request.setName("demo-nginx");
request.setImage("nginx:alpine");
request.setPorts(List.of("8080:80"));
request.setEnv(List.of("TZ=Asia/Shanghai"));                  // 格式 KEY=VALUE
request.setVolumes(List.of("/data/nginx:/usr/share/nginx/html"));  // 格式 宿主:容器
request.setRestartPolicy("unless-stopped");
request.setPull(true);
request.setRegistryId("dockerhub");   // 可空；非空时先按库凭据 docker login
request.setTemplateId(null);          // 可空；非空时先按模板回填未传字段

SseEmitter emitter = dockerService.runContainer(1L, request);   // 事件：step / log / done / error
```

网络与卷（请求对象为手写 getter/setter 的 POJO，步骤同样是 new + setter）：
`DockerNetworkRequest` 传 `setName` / `setDriver` / `setOptions`，
`DockerVolumeRequest` 同理，分别交给 `dockerService.createNetwork(hostId, request)`
与 `dockerService.createVolume(hostId, request)`。

模板（服务层可直接调用，`fill` 只补请求里没传的字段）：

```java
@Autowired
private TemplateService templateService;

DockerTemplate template = new DockerTemplate();
template.setName("nginx 模板");
template.setImage("nginx:alpine");
template.setPorts(List.of("8080:80"));
DockerTemplate saved = templateService.save(template);   // id 为空表示新增，返回带 id/时间戳的副本

DockerCreateRequest fromTemplate = new DockerCreateRequest();
fromTemplate.setTemplateId(saved.getId());
templateService.fill(fromTemplate);      // 回填 image / ports / env / volumes / network / restartPolicy / pull
```

镜像库（`save` 的入参是 `Map<String, Object>` 表单，与 `POST /registries` 同构）：

```java
@Autowired
private RegistryService registryService;

Map<String, Object> body = new LinkedHashMap<>();
body.put("type", "generic-v2");
body.put("name", "内网 Harbor");
body.put("url", "https://registry.example.com");
body.put("username", "robot");
body.put("password", "******");
registryService.save(body);

List<Map<String, Object>> registries = registryService.list();   // 不含 password，只回 hasPassword
Map<String, Object> tested = registryService.test("dockerhub");  // {ok, message}
List<String> tags = registryService.tags("dockerhub", "nginx");
String pullRef = registryService.pullRef("dockerhub", "nginx:alpine");
```

镜像库写入时密码有两种「不改」写法：传空串或传占位符 `******`（`RegistryService` 内部的私有常量 `PASSWORD_MASK`）。
内置 Docker Hub 固定 id 为 `RegistryService.BUILTIN_ID = "dockerhub"`，`builtin=true`，可改地址/凭据但不可删除。

### 5.2 SPI 扩展示例

本模块有两个真实扩展点：**镜像库 SPI（Spring Bean）** 与 **项目类型扩展点（`META-INF/extensions`）**。

**（1）镜像库：实现 `DockerRegistryProvider` 并声明为 Spring Bean。**

```java
@Component
public class MyHarborRegistryProvider implements DockerRegistryProvider {

    @Override public String type() { return "my-harbor"; }   // 必须唯一，持久化在 RegistryConfig.type
    @Override public String displayName() { return "我的 Harbor"; }
    @Override public List<Map<String, Object>> search(RegistryConfig cfg, String keyword, int limit) {
        return List.of();   // 统一结构：name / description / stars / official / pullRef
    }
    @Override public List<String> tags(RegistryConfig cfg, String image) { return List.of(); }
    @Override public String pullRef(RegistryConfig cfg, String image) { return image; }
    @Override public boolean test(RegistryConfig cfg) { return true; }
}
```

生效方式：`RegistryService` 的构造函数接收 `List<DockerRegistryProvider>`，
按 `type()` 建索引；`GET /ms/server/docker/registries/types` 会立刻列出
`my-harbor`，`POST /ms/server/docker/registries` 的 `type` 也就能填 `my-harbor`。
**不需要**在 `META-INF/services` 里登记——它不是 JDK SPI，而是 Spring Bean 集合。
`type()` 重复时后者覆盖前者（`LinkedHashMap#put`），查找不到实现时抛
`IllegalStateException`（"没有可用的镜像库实现"），不做静默降级。

**（2）项目发布类型：实现 `ProjectTypeProvider` 并在扩展点文件里登记。**

接口全限定名：`com.chua.starter.server.support.spi.ProjectTypeProvider`
（来自 `spring-support-server-starter`），本模块自带实现为
`com.chua.starter.docker.provider.project.DockerProjectTypeProvider`
（`@Spi(value = "docker", order = 100)`，`type()` 返回 `"docker"`）。

登记文件在 `src/main/resources` 下，文件名就是接口全限定名，每行一个实现类：

```text
META-INF/extensions/com.chua.starter.server.support.spi.ProjectTypeProvider
```

内容（本模块实际就这一行）：

```text
com.chua.starter.docker.provider.project.DockerProjectTypeProvider
```

生效方式：发布模块（`spring-support-server-starter` 的 `ProjectTypeProviderRegistry`）
首次解析扩展点时加载该文件，按 `type()` 建索引；项目类型选 `docker` 时
`scripts(windows, mainEntry)` 走 `ProjectScripts.windowsDocker/linuxDocker`，
主入口按容器名处理，`pidFile()` 为 `app.pid`。新增自定义类型不改本模块，
在自己 jar 里放同名文件并追加一行即可。

## 六、与其它 starter 的关系

依赖方向（本模块 → 依赖）：

- `spring-support-server-starter`（`4.0.0.51`）— 主机台账、凭据解析、SSH 连接池、
  `ServerConstants`、`ProjectTypeProvider` 扩展点、`ProjectScripts`
- `spring-boot-starter-web` — 控制器与 SSE
- `jackson-databind` — Docker JSON 输出解析与本地 JSON 持久化

反向依赖（仓库内真实存在）：

| 模块 | 依赖方式 |
|---|---|
| `spring-support-monitor-starter` | 声明 `spring-support-docker-starter`，经 `AutoConfiguration.imports` 自动装配 `/ms/server/docker` 全量接口 |

设计边界（类注释口径）：本模块**只依赖** `spring-support-server-starter`，
不反向依赖 monitor，避免循环依赖；`DockerAutoConfiguration` 用
`@ComponentScan(basePackages = "com.chua.starter.docker")` 扫描本模块的控制器、服务与 SPI 实现，
因此宿主不需要把 `com.chua.starter.docker` 加进自己的扫描路径。
与 `spring-support-control-starter`：两者在 `spring-support-monitor-starter` 中**并列引入**，
互相没有依赖；Docker 走 SSH + docker CLI，远控走 WebRTC 信令，协议与页面都独立。

## 七、已知限制

1. **只支持 POSIX 主机。** 所有入口先 `ensurePosix(hostId)`，Windows 主机直接报
   "该主机为 Windows，暂不支持 Docker 管理"；没有 Windows 容器/命名管道实现。
2. **强依赖 SSH 与主机台账。** 需要 `MsServerHost` 记录、`ServerHostService#resolveCredential`
   能解析出凭据、`ConnectionManager#borrow` 能借到连接；这些数据由
   `spring-support-server-starter` 维护，本模块不提供自建主机入口。
3. **持久化路径不可配置。** `~/.monitor/docker/registries.json` 与
   `templates.json` 在构造时用 `System.getProperty("user.home")` 拼死，
   多实例/多租户共用同一用户目录时会互相覆盖；也没有配置项可改。
4. **镜像库密码明文落盘。** `RegistryConfig.password` 原样写进 `registries.json`
   （文件内容对该用户可读），对外视图只剔除字段（`hasPassword`），不加密存储。
5. **容器创建不接受任意命令透传。** `buildRunCommand` 只拼 `docker run -d` +
   name/restart/network/ports/env/volumes + 镜像 + 按空白拆分的命令，
   各字段先做正则校验（端口 `\d{1,5}:\d{1,5}(/(tcp|udp))?`、环境变量 `KEY=VALUE`、
   卷 `src:dst`、容器名/网络名白名单），命令逐段单引号转义——这是有意的安全限制。
6. **搜索有长度与条数门槛。** `RegistryService#search` 关键词少于 2 字符直接返回空列表；
   `limit` 缺省 25、上限 100；`GenericV2RegistryProvider` 的 `_catalog` 拉取上限 500 条
   （`CATALOG_MAX`），超大仓库只能看到部分结果。
7. **Docker Hub 检索固定走 hub.docker.com。** `DockerHubRegistryProvider.HUB_API` 是常量，
   改镜像加速地址只影响 `pullRef` 前缀，不影响检索域名；无外网时检索为空列表（只记 `warn`）。
8. **SSE 会话 30 分钟超时、单次输出截断 64KB。** `SSE_TIMEOUT_MILLIS` 为 30 分钟，
   `OUTPUT_TRUNCATE` 为 64KB（一次性日志/命令输出），超长输出会被截断。
9. **交互式终端状态与线程池都在内存。** `DockerServiceImpl#execSessions` 是
   `ConcurrentHashMap`，进程重启即丢，多实例部署时 `exec/input` 必须打到同一实例；
   `RegistryService#searchPool`（`Executors.newVirtualThreadPerTaskExecutor()`）
   没有 `close`/`@PreDestroy`，依赖 JVM 结束回收。
