# spring-support-datasource-starter

多数据源路由与治理：`@DS` 注解切换、SpEL 动态路由、分组负载均衡、多数据源事务、ENC 配置加密、健康检查、SQL 监听与 Calcite 跨源查询。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| `@DS` 数据源切换 | `DS`、`DynamicDataSourceAspect` | 类 / 方法 / Mapper 接口级注解 |
| SpEL 动态路由 | `DynamicDataSourceAspect#resolveSpelExpression` | `@DS("#tenantId")`、`#header`、`#session` |
| 跨源聚合 | `MultiDataSource`（Calcite 合并） | `@DS("master,slave")` |
| 分组 + 负载均衡 | `DataSourceGroupRegistry`、`DataSourceGroup` | 数据源名 `组_序号` 自动归组 |
| 多数据源事务 | `DSTransactional`、`DSTransactionalAspect`、`MultiDSTransactionContext` | `@DSTransactional` |
| 声明式事务切入点 | `DataSourceTransactionConfiguration`、`MethodPointcutAdvisor` | `plugin.transaction` |
| Seata 集成 | `SeataAutoConfiguration` | `@GlobalTransactional` |
| ENC 配置加密 | `ENCEnvironmentPostProcessor`、`AesPasswordDecryptor` | `ENC(...)`、`plugin.datasource.enc` |
| 健康检查 | `DataSourceHealthIndicator` | `/actuator/health` |
| 优雅关闭 | `GracefulShutdownListener` | `plugin.datasource.graceful-shutdown` |
| 脚本初始化 | `DataSourceScriptConfiguration`、`DataSourceScriptProperties` | `plugin.datasource.script` |
| SQL 监听 | `DataSourceSqlMonitor`、`SqlMonitorEndpoint` | `plugin.datasource.monitor` |
| SQL 物理化路由 | `MaterializedRoute`、`MaterializedRouteAdvisor` | `@MaterializedRoute` |
| 多连接池适配 | `DataSourcePropertyProvider`（Hikari/Druid/DBCP2/Tomcat/C3P0） | `plugin.multi-datasource.hikari` |
| H2 / SQLite 引擎 | `H2EngineAutoConfiguration`、`SqliteEngineAutoConfiguration` | `plugin.h2` / `plugin.sqlite` |
| 分库分表 | `ShardingAutoConfiguration` | `plugin.datasource.sharding` |
| 数据库文档 | `DataSourceDocumentController`、`DatabaseGraphService` | `plugin.datasource.document` |

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-datasource-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 | scope |
|---|---|---|
| `spring-support-common-starter` | 统一响应、`ModuleLog`、`ServiceInfoContributor` | 必需 |
| `spring-boot-starter-jdbc` | `DataSourceProperties`、事务基础设施 | 必需 |
| `aspectjweaver` | `@DS` / `@DSTransactional` 切面 | 必需 |
| `calcite-core` | 跨源 SQL 解析与合并 | 必需 |
| `p6spy` | 旧版 SQL 打印 | 必需 |
| `sqlite-jdbc` | 默认内置库 | 必需 |
| `utils-support-flyway-starter` | 脚本迁移 SPI 实现 | 必需 |
| `HikariCP` / `druid-spring-boot-4-starter` / `commons-dbcp2` / `tomcat-jdbc` / `c3p0` | 连接池 | `provided`，按需引入 |
| `spring-boot-starter-actuator` | 健康检查 | `provided` + `optional` |
| `seata-all` | 分布式事务 | `provided` + `optional` |
| `utils-support-calcite-starter` / `utils-support-shardingv5-starter` | 跨源 / 分片实现 | `optional` |
| `utils-support-h2-starter` / `utils-support-sqlite-starter` | 嵌入式引擎 | `optional` |

## 三、配置项

| 前缀 | 配置类 | 关键项（默认值） |
|---|---|---|
| `plugin.multi-datasource` | `MultiDataSourceProperties` | `enable`=`false`；`data-source[]`（`name`/`url`/`username`/`password`/`driver-class-name`）；旧前缀 `spring.multi-datasource` 兼容 |
| `plugin.multi-datasource` | `MultiDataSourceSettingProperties` | `force-annotation`=`true`；`strict`=`false`；`load-balance`=`round_robin`（`random`） |
| `plugin.multi-datasource.hikari` | `MultiHikariDataSourceProperties` | `data-source[]` 以 `HikariDataSource` 构建，支持 `max-pool-size`、`minimum-idle`、`leak-detection-threshold` 等 |
| `plugin.transaction` | `TransactionProperties` | `enable`=`false`；`timeout`=`10`（秒）；`tx-mapper`=`""`；`read-only`/`write-only`/`no-tx` 均为空串（不预设方法前缀） |
| `plugin.datasource.enc` | `ENCEnvironmentPostProcessor` | `enabled`=`false`；`secret-key`=`""` |
| `plugin.datasource.health` | — | `enabled`=`true`（`matchIfMissing=true`） |
| `plugin.datasource.graceful-shutdown` | — | `enabled`=`true` |
| `plugin.datasource.script` | `DataSourceScriptProperties` | `enable`=`true`；`scan-mode`=`ONCE`；`migration-scan-mode`=`ALWAYS`；`data-scan-mode`=`ONCE`；`script-path`=`classpath*:db/init/*.sql,classpath*:db/migration/*.sql,classpath*:db/sync/*.sql`；`continue-on-error`=`true`；`version-table`=`sys_database_version`；`baseline-version`=`1.0.0`；`release-type`=`STABLE`；`initdata-parallelism`=`0` |
| `plugin.datasource.monitor` | `SqlMonitorProperties` | `enabled`=`null`（按 profile 自动：`dev`/`test`/`uat`/`local` 开启）；`slow-sql-threshold-ms`=`1000`；`max-sql-length`=`2000`；`max-tracked-data-sources`=`1000`；`overflow-policy`=`REJECT` |
| `plugin.datasource.materialized` | `MaterializedRouteProperties` | `enabled`=`false`；`default-threshold`=`1000`；`refresh-interval-seconds`=`0`；`skip-query-in-transaction`=`true`；`cache-data-source-prefix`=`materialized#`；旧前缀 `spring.datasource.materialized` 兼容 |
| `plugin.datasource.calcite` | `CalciteDataSourceProperties` | `enabled`，Calcite 合并数据源参数 |
| `plugin.datasource.sharding` | `ShardingDataSourceProperties` | `enabled`，ShardingSphere 5.x 参数 |
| `plugin.datasource.document` | `DocumentProperties` | 数据库文档导出配置 |
| `plugin.h2` / `plugin.sqlite` | `H2DataSourceProperties` / `SqliteDataSourceProperties` | 内置库路径等 |

## 四、快速开始

**第一步**：引入依赖，并保留宿主的 `spring.datasource` 作为主库（默认数据源名 `master`）。

**第二步**：声明多数据源。每个条目需要同时给出 `name` 与 `url`，否则跳过注册：

```yaml
plugin:
  multi-datasource:
    load-balance: round_robin
    data-source:
      - name: master
        url: jdbc:mysql://localhost:3306/master_db
        username: root
        password: password
        driver-class-name: com.mysql.cj.jdbc.Driver
      - name: slave_1
        url: jdbc:mysql://localhost:3306/slave_db
        username: root
        password: password
```

**第三步**：在 Service / Mapper 上标注 `@DS`。不标注时走默认数据源 `master`。

## 五、运行示例

### 5.1 调用示例

本模块无链式 API，以下均为注解 + 步骤式用法。

**方法级与类级切换**：

```java
@Service
public class UserService {

    @DS("master")
    public User save(User user) { return userRepository.save(user); }

    @DS("slave")
    public List<User> findAll() { return userRepository.findAll(); }
}

@Service
@DS("slave")                    // 类级默认
public class ReportService {

    public List<Report> generate() { return reportRepository.findAll(); }

    @DS("master")               // 方法级覆盖
    public void save(Report r) { reportRepository.save(r); }
}
```

**SpEL 动态路由**（`#` 开头才解析，支持 `#参数名`、`#header`、`#session`、`#request`）：

```java
@GetMapping("/orders")
@DS("#header.xTenantId")
public List<Order> listOrders() { ... }

@GetMapping("/users")
@DS("#header['x-datasource'] ?: 'master'")
public List<User> listUsers() { ... }

@GetMapping("/tenant/{tenantId}/data")
@DS("#tenantId")
public List<Data> getTenantData(@PathVariable String tenantId) { ... }
```

表达式解析失败时记 WARN 日志并回退 `master`。

**分组负载均衡**：数据源名按 `组_序号` 自动归组，`@DS("slave")` 在 `slave_1`/`slave_2` 之间按 `round_robin` 或 `random` 选择。

**多数据源事务**：

```java
@DSTransactional
@DS("master")
@Service
public class UserService {

    public void saveUser(User user) {
        userDao.insert(user);                              // master
        UserLog log = logService.getLastLog(user.getId());  // 嵌套切 slave
        userDao.updateLog(user.getId(), log.getId());       // 恢复 master
    }
}
```

`@DSTransactional` 属性：`value`/`name`（互为别名）、`propagate`（默认 `true`）。它只保证 `@DS` 在事务绑定前完成切换并支持嵌套恢复，**不提供跨库 ACID**。

**严格模式**：`plugin.multi-datasource.strict=true` 时数据源不存在抛异常，否则回退 `master`。

```java
@DS("nonexistent_ds")   // 严格模式：IllegalStateException
public void test() { ... }
```

**编程式切换**：

```java
try {
    DataSourceContextSupport.setDbType("source");
    List<Data> data = dataRepository.findAll();
    DataSourceContextSupport.setDbType("target");
    dataRepository.saveAll(data);
} finally {
    DataSourceContextSupport.clearDbType();
}
```

**ENC 配置加密**（默认关闭）：

```yaml
plugin:
  datasource:
    enc:
      enabled: true
      secret-key: mySecretKey12345

spring:
  datasource:
    password: ENC(uX5z1H+z5f3k8L9mW2qR4w==)
```

支持 `ENC(base64密文)` 与 `ENC(AES:base64密文)`；解密在 `EnvironmentPostProcessor` 阶段完成，失败默认保留原值。

**SQL 监听**：`plugin.datasource.monitor.enabled` 不设时按 profile 自动判断；慢 SQL 阈值 `slow-sql-threshold-ms` 默认 1000ms，统计通过 `SqlMonitorEndpoint` 与 `SqlExecutionEvent` 暴露。该能力是纯 JDBC 包装（`MonitoringDataSource`），不依赖 P6Spy 或 Druid。

**健康检查**（需 Actuator）：

```json
{
  "status": "DEGRADED",
  "components": {
    "dataSource": {
      "status": "DEGRADED",
      "details": { "total": 3, "up": 2, "down": 1, "dataSources": { "master": { "status": "UP", "responseTime": 5 } } }
    }
  }
}
```

状态规则：全部 UP → `UP`；部分异常 → `DEGRADED`；全部异常 → `DOWN`；无数据源 → `OUT_OF_SERVICE`。`CommunicationsException` / `SocketException` / `InterruptedException` 视为暂时性错误，仍记 `UP` 并附 `warning`。

**SQL 物理化路由**（需 `spring-support-mybatis-starter` + `utils-support-calcite-starter`）：

```yaml
plugin:
  datasource:
    materialized:
      enabled: true
      default-threshold: 1000
      refresh-interval-seconds: 0
      skip-query-in-transaction: true
```

```java
@MaterializedRoute(threshold = 500, dataSource = "master")
public List<UserReportVO> querySmallTableReport() { ... }

@MaterializedRefresh(dataSource = "master")
public List<DictItem> reloadDict() { ... }
```

**Seata 分布式事务**（需自行引入 `seata-spring-boot-starter`）：检测到 `io.seata.core.context.RootContext` 时，`SeataAutoConfiguration` 自动为已注册数据源包装 `DataSourceProxy`。

```yaml
seata:
  enabled: true
  application-id: my-app
  tx-service-group: my_tx_group
  enable-auto-data-source-proxy: false   # 本模块自行代理，避免双重包装
  data-source-proxy-mode: AT             # AT / XA
```

```java
@GlobalTransactional
public void transfer() {
    accountDao.debit();   // 数据源 A
    accountDao.credit();  // 数据源 B
}
```

**HikariCP 细粒度配置**（走 `plugin.multi-datasource.hikari`，旧前缀 `spring.multi-datasource.hikari` 兼容）：

```yaml
plugin:
  multi-datasource:
    hikari:
      data-source:
        - name: master
          jdbc-url: jdbc:mysql://localhost:3306/master_db
          username: root
          password: password
          max-pool-size: 20
          minimum-idle: 5
          connection-timeout: 30000
          idle-timeout: 600000
          max-lifetime: 1800000
          leak-detection-threshold: 60000
          connection-test-query: SELECT 1
```

**ENC 密文生成**（密钥先经 SHA-256 归一为 32 字节，再 AES/ECB/PKCS5Padding + Base64）：

```java
public static String encrypt(String plainText, String secretKey) throws Exception {
    byte[] key = MessageDigest.getInstance("SHA-256").digest(secretKey.getBytes(StandardCharsets.UTF_8));
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
    return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
}
```

**P6Spy**（模块保留 `p6spy` 依赖与 `spy/SimpleAppender`，新项目建议优先用上面的 `plugin.datasource.monitor`）：

```properties
modulelist=com.p6spy.engine.spy.P6SpyFactory
logMessageFormat=com.p6spy.engine.spy.appender.CustomLineFormat
customLogMessageFormat=%(currentTime) | %(executionTime) ms | %(category) | %(sql)
appender=com.p6spy.engine.spy.appender.Slf4JLogger
excludecategories=info,debug,result,resultset
```

`@DSTransactional` 切面优先级为 `Ordered.LOWEST_PRECEDENCE - 10`，早于 Spring `TransactionInterceptor`（`LOWEST_PRECEDENCE`），从而保证数据源上下文先于事务绑定完成切换。

### 5.2 SPI 扩展示例

本模块不提供 SPI 扩展点（`src/main/resources/META-INF` 下只有 `spring.factories` 与 `AutoConfiguration.imports`，无 `extensions` 目录）。

唯一可替换的扩展是密码解密器，走 Spring Bean 覆盖而非 SPI：

```java
// 1. 实现接口
public class Sm4PasswordDecryptor implements PasswordDecryptor {
    @Override
    public String decrypt(String encryptedValue) {
        return Sm4Utils.decrypt(encryptedValue);   // 不含 ENC() 包裹
    }

    @Override
    public boolean supports(String algorithm) {
        return "SM4".equalsIgnoreCase(algorithm);
    }
}

// 2. 声明为 Bean（默认 AesPasswordDecryptor 带 @ConditionalOnMissingBean）
@Configuration
public class CryptoConfiguration {
    @Bean
    public PasswordDecryptor passwordDecryptor() {
        return new Sm4PasswordDecryptor();
    }
}
```

3. 生效：`plugin.datasource.enc.enabled=true`，配置值写 `ENC(SM4:base64密文)`。注意 `ENCEnvironmentPostProcessor` 在环境准备阶段自行构建解密器，自定义 Bean 主要用于容器内注入场景，两处行为需分别验证。

## 六、与其它 starter 的关系

- **上游**：`spring-support-common-starter`（统一响应、`ServiceInfoContributor` 注册横幅）。
- **MyBatis**：`spring-support-mybatis-starter` 提供 SQL 物理化拦截器，`@MaterializedRoute` 在 MyBatis 执行链上生效；裸 `JdbcTemplate` 不会自动走物理化。
- **连接池**：Druid/Hikari/DBCP2/Tomcat/C3P0 全部 `provided`，由宿主选择；`DataSourcePropertyProvider` 按池类型映射配置项。
- **缓存/Redis**：互不依赖；健康检查与 `spring-support-redis-starter` 的健康指示器并列出现在 `/actuator/health`。
- **分库分表**：ShardingSphere 5.x 通过 `plugin.datasource.sharding` 与 `utils-support-shardingv5-starter` 接入，与本模块的 `@DS` 路由是两套独立机制，不建议同一方法上同时使用。

## 七、已知限制

1. `@DS` 依赖 Spring AOP 代理，`private`/`final` 方法、同类内部 `this.xxx()` 调用均不生效。
2. 类级与方法级注解只支持 `TYPE`/`METHOD`，字段、构造参数上标注无效。
3. SpEL 在 `@DS` 解析阶段执行，早于参数校验；无 `RequestContextHolder`（异步线程、定时任务）时 `#header`/`#session` 不可用，会回退 `master`。
4. 分组依赖 `组_序号` 命名（如 `slave_1`），`master` 这类无下划线名称不归组；组内成员在启动注册时确定。
5. `@DSTransactional` 不做跨库提交/回滚协调，跨库一致性必须使用 Seata `@GlobalTransactional`。
6. ENC 功能默认关闭，需 `plugin.datasource.enc.enabled=true`；`secret-key` 变更后旧密文无法解密。
7. 健康检查需 `spring-boot-starter-actuator` 在类路径，且 Boot 4 的 `org.springframework.boot.health.contributor.HealthIndicator` 存在，否则指示器不注册。
8. SQL 物理化只面向小表/码表；复杂 SQL 会回源或退化为全量重建，事务内默认跳过（`skip-query-in-transaction=true`）。
9. `plugin.transaction.enable` 默认 `false`，不开启时不注册声明式事务切入点，方法前缀类名规则（`read-only` 等）也不生效。
10. 运行环境：Java 25、Spring Boot 4.1.x（pom 引入 `druid-spring-boot-4-starter`、`spring-boot-starter-jdbc`），Boot 3.x 不在支持范围。
