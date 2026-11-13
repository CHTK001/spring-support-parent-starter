# spring-support-software-starter

软件资产与安装管理的完整闭环模块：软件目录、软件源、安装/卸载/升级记录、软件配置，
外加「当前机器已装软件扫描」与安装进度实时推送。CRUD 与统计走 MyBatis-Plus + MySQL，
真实安装动作通过 `SoftwareSource` 扩展点交给具体源实现。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
| --- | --- | --- |
| 软件目录维护 | `SoftSoftwareController` / `SoftSoftwareService` | `/v2/soft/software`（`page` / `save` / `update` / `delete` / `detail`） |
| 软件源管理（含启用、禁用、同步） | `SoftSourceController` / `SoftSourceService` | `/v2/soft/source`（`page` / `save` / `update` / `delete` / `detail` / `enable` / `disable` / `sync` / `default`） |
| 软件配置（按软件挂键值配置） | `SoftConfigController` / `SoftConfigService` | `/v2/soft/config`（`page` / `save` / `update` / `delete` / `list` / `batch`） |
| 安装 / 卸载 / 升级与记录 | `SoftInstallController` / `SoftInstallRecordService` | `/v2/soft/install`（`do` / `uninstall` / `update` / `detail` / `list` / `installed`） |
| 本机软件扫描、清理、手动添加 | `LocalSoftwareController` / `LocalSoftwareService` | `/v2/soft/local`（`scan` / `clear` / `page` / `detail` / `install` / `uninstall` / `add` / `list`） |
| 软件源扩展点 | `SoftwareSource`、`SoftwareSourceResult` | 注入 `List<SoftwareSource>`，按 `support(SoftSource)` 匹配 |
| 平台采集器扩展点 | `LocalSoftwareCollector` | `WindowsLocalSoftwareCollector` / `LinuxLocalSoftwareCollector` / `MacLocalSoftwareCollector` |
| 安装进度实时推送 | `InstallProgressPublisher` / `SocketInstallProgressPublisher` | `InstallProgressDispatcher#dispatch(InstallProgressEvent)` |
| 进度事件组装 | `InstallProgressEvent` | `InstallProgressEvent#of(SoftInstallRecord, String)`、`#toMsgStep()` |
| 数据表与初始化脚本 | `SoftSource` / `SoftSoftware` / `SoftConfig` / `SoftInstallRecord` | `db/init/V2.0__init_all_software.sql`、`db/init/V2.0__initdata_software.sql` |
| 装配与服务信息 | `SoftwareConfiguration` | `@AutoConfiguration` + `@MapperScan` + `@ComponentScan` |

数据表：`soft_source`、`soft_software`、`soft_config`、`soft_install_record`（均通过实体 `@TableName` 声明）。

> 装配提醒：本模块**没有** `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
> `SoftwareConfiguration` 虽然标了 `@AutoConfiguration`，但仍需宿主扫描 `com.chua.starter.software.support`
> 包（或显式 `@Import(SoftwareConfiguration.class)`）才会生效。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-software-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 版本 | 作用 |
| --- | --- | --- |
| `com.chua:spring-support-datasource-starter` | `4.0.0.51`（同父版本） | 数据源与事务 |
| `com.chua:spring-support-mybatis-starter` | `4.0.0.51` | MyBatis-Plus、`Query` / `PageResultUtils` / `ReturnPageResult` |
| `com.chua:spring-support-swagger-starter` | `4.0.0.51` | `@Tag` / `@Operation` / `@ParameterObject` |
| `org.springframework.boot:spring-boot-starter-web` | 由父 POM 管理 | REST 层 |
| `org.springframework.boot:spring-boot-starter-tomcat` | 由父 POM 管理 | 独立启动时的容器 |
| `jakarta.servlet:jakarta.servlet-api` | `6.1.0`（显式声明） | Servlet 契约 |
| `com.chua:utils-support-common-starter` | `4.0.0.50` | `ReturnResult`、`StringUtils`、`CollectionUtils` |
| `com.chua:spring-support-socket-starter` | `4.0.0.51`（`optional`） | 仅用于进度广播；未引入时由空发布器兜底 |
| `org.junit.jupiter:junit-jupiter` | `${junit.jupiter.version}` = `6.1.3`（`test`） | 安装进度事件/分发器单测 |

父 POM：`com.chua:spring-support-parent-starter:4.0.0.51`，`java.version = 25`。
编译期额外启用 Lombok 与 NullAway 注解处理器（`proc=full`）。

## 三、配置项

**无 `@ConfigurationProperties`，复用宿主配置。** 本模块不定义任何自有配置前缀，
所有行为都由宿主的数据源、MyBatis-Plus、权限与 socket 配置决定。

模块自带的 `src/main/resources/application.yml` 只是 `SoftStarterApplication` 独立启动时的示例，
不是 starter 配置项：

| 宿主配置 | 自带示例值 | 说明 |
| --- | --- | --- |
| `spring.datasource.*` | MySQL `jdbc:mysql://172.16.0.40:3306/softdb` | 需宿主自行提供；密码走环境变量 `SOFTWARE_DB_PASSWORD` |
| `spring.sql.init.mode` | `never` | 不自动建表，需手工执行 `db/init` 下的脚本 |
| `mybatis-plus.mapper-locations` | `classpath*:mapper/**/*.xml` | 本模块带 `mapper/SoftSoftwareMapper.xml`、`mapper/SoftConfigMapper.xml` |
| `mybatis-plus.global-config.db-config.id-type` | `ASSIGN_ID` | 主键由 MyBatis-Plus 生成（雪花 ID） |
| `server.port` | `8081` | 仅独立启动时生效 |

## 四、快速开始

**第一步：引入依赖**（见第二节），并保证宿主已有数据源。

**第二步：建表**。手工执行 `src/main/resources/db/init/V2.0__init_all_software.sql`
（建 4 张表），需要演示数据再执行 `V2.0__initdata_software.sql`。

**第三步：让配置生效**。宿主启动类加 `@ComponentScan(basePackages = "com.chua.starter.software.support")`，
或直接 `@Import(SoftwareConfiguration.class)`。之后 `/v2/soft/**` 端点与四个 Service Bean 可用。

## 五、运行示例

### 5.1 调用示例

REST 侧（全部带 `@Permission`，需要宿主权限拦截器；角色限定 `AuthConstant.ADMIN` / `AuthConstant.SUPER_ADMIN`）：

```text
# 软件目录
GET    /v2/soft/software/page?page=1&pageSize=10      权限码: 无（仅角色）
POST   /v2/soft/software/save       body: SoftSoftware          sys:soft:add
PUT    /v2/soft/software/update     body: SoftSoftware          sys:soft:update
DELETE /v2/soft/software/delete?softwareId=<id>                 sys:soft:delete
GET    /v2/soft/software/detail?softwareId=<id>

# 软件源
GET    /v2/soft/source/page
POST   /v2/soft/source/save         body: SoftSource            sys:soft:source:add
PUT    /v2/soft/source/update       body: SoftSource            sys:soft:source:update
DELETE /v2/soft/source/delete?sourceId=<id>                     sys:soft:source:delete
PUT    /v2/soft/source/enable?sourceId=<id>                     sys:soft:source:update
PUT    /v2/soft/source/disable?sourceId=<id>                    sys:soft:source:update
POST   /v2/soft/source/sync?sourceId=<id>                       sys:soft:source:sync
GET    /v2/soft/source/default

# 安装
POST   /v2/soft/install/do          body: InstallRequest        sys:soft:install
DELETE /v2/soft/install/uninstall?softwareId=&userId=&deviceId=  sys:soft:uninstall
PUT    /v2/soft/install/update?softwareId=&userId=&deviceId=&targetVersion=  sys:soft:update
GET    /v2/soft/install/detail?recordId=<id>
GET    /v2/soft/install/list?softwareId=<id>
GET    /v2/soft/install/installed?userId=<id>

# 本机软件
POST   /v2/soft/local/scan?sourceId=<id>                        sys:soft:local:scan
DELETE /v2/soft/local/clear?sourceId=<id>                       sys:soft:local:delete
GET    /v2/soft/local/page
POST   /v2/soft/local/install       body: InstallRequest        sys:soft:local:install
DELETE /v2/soft/local/uninstall?softwareId=&userId=&deviceId=   sys:soft:local:uninstall
POST   /v2/soft/local/add           body: SoftSoftware          sys:soft:local:add
GET    /v2/soft/local/list?keyword=<kw>
```

Java 侧按步骤调用（**本模块无链式 API**：Service 方法都是普通调用，`InstallRequest` 是 record；
`SoftwareSourceResult.success(...)` / `fail(...)` 只是静态工厂，不是 Builder）：

```java
@Service
@RequiredArgsConstructor
public class SoftwareOps {

    private final SoftSourceService softSourceService;
    private final LocalSoftwareService localSoftwareService;
    private final SoftInstallRecordService installRecordService;
    private final SoftSoftwareService softSoftwareService;

    public void demo() {
        // 1) 扫描本机已安装软件；sourceId 传 null 时使用默认本地源（source_type=3）
        int scanned = localSoftwareService.scanLocalSoftware(null);

        // 2) 从某个源同步软件目录（内部遍历 List<SoftwareSource>，按 support(source) 命中）
        SoftSource local = softSourceService.getDefaultLocalSource();
        ReturnResult<Integer> synced = softSourceService.syncSource(local.getSourceId());

        // 3) 安装（落一条 soft_install_record → 推 0%/30% 进度 → 调命中的 SoftwareSource#install → 推 100%）
        InstallRequest request = new InstallRequest("sw-1", "u-1", "dev-1", "D:/apps/demo", null);
        ReturnResult<InstallDetailResponse> installed = installRecordService.install(request);

        // 4) 读安装详情（install 内部已把记录推到 INSTALLED / 100%）
        String recordId = installed.getData().getInstallRecordId();
        ReturnResult<InstallDetailResponse> detail = installRecordService.getInstallDetail(recordId);
        List<SoftInstallRecord> records = installRecordService.listInstalledByUserId("u-1");

        // 5) 分页查询（已安装的排前面）
        var page = softSoftwareService.pageFor(new Query<>(), new SoftwarePageRequest(),
                installRecordService.getInstalledSoftwareIds("u-1"));
    }
}
```

安装状态机（`InstallStatus`，落库为 `install_record_status` 整数）：

| 值 | 枚举 | 含义 |
| --- | --- | --- |
| 0 | `NOT_INSTALLED` | 未安装 |
| 1 | `INSTALLING` | 安装中 |
| 2 | `INSTALLED` | 已安装 |
| 3 | `FAILED` | 安装失败 |
| 4 | `UNINSTALLING` | 卸载中 |
| 5 | `UNINSTALLED` | 已卸载 |
| 6 | `UPDATING` | 更新中 |
| 7 | `UPDATE_FAILED` | 更新失败 |

软件源类型（`SourceType`）：`OFFICIAL(0)`、`THIRD_PARTY(1)`、`SELF_BUILT(2)`、`LOCAL(3)`。

### 5.2 SPI 扩展示例

本模块有两个扩展接口，**都走 Spring 容器注册，不使用 `META-INF/services`**
（模块内不存在 `META-INF` 目录）。

**扩展一：`com.chua.starter.software.support.spi.SoftwareSource`** —— 接入一个新的软件源类型。

```java
@Component
public class NexusSoftwareSource implements SoftwareSource {

    @Override
    public boolean support(SoftSource source) {
        return source != null && source.getSourceType() == SourceType.THIRD_PARTY.getValue();
    }

    @Override
    public List<SoftSoftware> fetchSoftwareList(SoftSource source) {
        // 按 source.getSourceApiUrl() 拉取，返回待入库的 SoftSoftware
        return List.of();
    }

    @Override
    public SoftSoftware fetchSoftwareDetail(SoftSource source, String softwareCode) {
        return null;
    }

    @Override
    public SoftwareSourceResult install(SoftSource source, SoftSoftware software, String installPath) {
        return SoftwareSourceResult.success("安装完成", installPath);
    }

    @Override
    public SoftwareSourceResult uninstall(SoftSource source, SoftSoftware software, String installPath) {
        return SoftwareSourceResult.success("卸载完成", installPath);
    }

    @Override
    public SoftwareSourceResult update(SoftSource source, SoftSoftware software,
                                       String targetVersion, String installPath) {
        return SoftwareSourceResult.fail("该源不支持原地升级");
    }

    @Override
    public String getLatestVersion(SoftSource source, String softwareCode) {
        return null;
    }
}
```

生效路径：
`POST /v2/soft/source/sync?sourceId=<id>` → `SoftSourceService#syncSource` →
`SoftSoftwareServiceImpl#syncFromSource` 遍历注入的 `List<SoftwareSource>` → 命中 `support(source)` 的实现
→ `fetchSoftwareList` 结果按 `softwareCode` 去重后入库，返回同步条数。
注意 `DefaultSoftwareSource` 上标了 `@ConditionalOnMissingBean(SoftwareSource.class)`，意图是「容器里没有任何
`SoftwareSource` 实现时才兜底」。它同时是 `@Component`，条件评估与扫描顺序有关，实际是否注册以容器内 Bean 列表为准。

**扩展二：`com.chua.starter.software.support.spi.local.LocalSoftwareCollector`** —— 支持一个新的操作系统。

```java
@Component
public class FreeBsdLocalSoftwareCollector implements LocalSoftwareCollector {

    @Override
    public boolean support() {
        return System.getProperty("os.name", "").toLowerCase().contains("freebsd");
    }

    @Override
    public List<SoftSoftware> collect() {
        return List.of();   // 只填名称/版本/安装路径/图标等基础信息
    }
}
```

生效路径：`POST /v2/soft/local/scan` → `LocalSoftwareService#scanLocalSoftware` →
`LocalSoftwareSource#fetchSoftwareList`（`support` 判断 `sourceType == 3`）→ 遍历 `List<LocalSoftwareCollector>`
取第一个 `support()` 为 true 的采集器 → `collect()` 结果写库。多个采集器命中时**只取第一个**，
顺序即 `List` 的注入顺序（可用 `@Order` 干预）。

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
| --- | --- | --- |
| `spring-support-datasource-starter` | 本模块依赖它 | 数据源、事务模板 `TransactionTemplate` |
| `spring-support-mybatis-starter` | 本模块依赖它 | Mapper 扫描、分页、`Query` / `ReturnPageResult` |
| `spring-support-swagger-starter` | 本模块依赖它 | 接口文档注解 |
| `spring-support-socket-starter` | 本模块可选依赖它 | 安装进度广播；未引入时 `SoftwareRealtimeConfiguration` 装配空发布器 |
| `utils-support-common-starter` | 本模块依赖它 | `ReturnResult` 等基础类型 |
| `spring-support-common-starter` | 传递依赖 | `@Permission`、`ServiceInfoContributor`、`ReturnPageResult` |
| `spring-support-device-starter` | 无直接依赖 | 安装记录里的 `deviceId` 只是字符串，由宿主关联设备体系 |

当前父 POM 中没有其它模块声明依赖 `spring-support-software-starter`（以代码为准）；
模块自带 `SoftStarterApplication` 可单独 `mvn spring-boot:run` 调试。

## 七、已知限制

1. **没有自动配置注册文件**。缺 `META-INF/spring/...AutoConfiguration.imports`，
   宿主不扫描 `com.chua.starter.software.support` 就什么都装配不上（`@MapperScan` 也随之失效）。
2. **默认软件源是空实现**。`DefaultSoftwareSource#support` 恒为 `false`，
   `install` / `uninstall` / `update` 一律返回 `fail("不支持的软件源类型")`；
   不接自己的 `SoftwareSource` 就没有真实安装能力。
3. **本地源不执行真实安装**。`LocalSoftwareSource#install` 直接返回 `success("本地软件已存在")`，
   `uninstall` 返回 `success("标记为待删除")`，`update` 只 `setSoftwareLatestVersion` 而不动系统；
   `LocalSoftwareService` 侧同样是「管理记录」语义，系统级安装/卸载需外部手段完成。
4. **Elasticsearch 方法是占位**。`indexToElasticsearch` / `deleteFromElasticsearch` 只打日志，
   `searchFromElasticsearch` 直接返回空列表（`SoftSoftwareServiceImpl` 内），检索能力尚未落地。
5. **强依赖 MySQL**。自带 `application.yml` 指向 `jdbc:mysql://172.16.0.40:3306/softdb`，
   且 `spring.sql.init.mode: never`——不会自动建表。`SoftStarterApplication` 的类注释写着「内置 H2」，
   与实际配置不一致，以 `application.yml` 为准。
6. **权限只是注解**。`@Permission` 依赖宿主的权限拦截器解析，宿主没有相应组件时
   `/v2/soft/**` 会完全开放（注解不拦截）。
7. **进度推送失败被吞**。`InstallProgressPublisher` 的实现约定是「任何异常都必须吞掉」，
   `InstallProgressDispatcher` 也只在事务提交后发布、回滚整帧丢弃；socket 缺失时只写 debug 日志。
   引入 socket 后既是广播又是定向投递，**发起人会收到两份相同载荷**（前端自行按内容指纹去重）。
8. **本机扫描是同步阻塞的**。Windows 采集器通过 `reg query` 读三处 Uninstall 节点（GBK 解码），
   Linux 走 `dpkg` / `rpm` / `pacman`，macOS 读 `/Applications` 并用 `mdls` 取名称版本；
   软件数量大时接口耗时明显，且只返回第一个 `support()` 为 true 的采集器结果。
9. **删除是软删除**。`SoftSoftwareService#deleteSoftware` 把 `software_status` 置 1 而不是删行；
   查询时需自行带上状态条件。
10. **`syncFromSource` 逐条写库**。命中源的 `fetchSoftwareList` 结果在同一个 `TransactionTemplate` 内
    按 `softwareCode` 逐条 `insert` / `updateById`，源返回超大列表时是一次长事务。
11. **`install` 不看 `SoftwareSourceResult#success`**。`SoftInstallRecordServiceImpl#install` 拿到结果后
    直接 `updateProgress(..., 100, result.message())` 再 `completeInstall(...)`，不判断 `success`：
    实现返回 `SoftwareSourceResult.fail(...)` 不会让记录变成 `FAILED`，只有**抛异常**才会走 `failInstall`。
    需要失败语义时，请让实现在失败分支抛异常。
