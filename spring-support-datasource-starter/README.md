# Spring Support Datasource Starter

## 📖 模块简介

Spring Support Datasource Starter 是一个功能强大的数据源配置和管理模块，提供了企业级应用中数据源的配置、管理和监控功能。

### ✨ 功能全景

| 功能 | 状态 | 对标 baomidou |
|------|:----:|:------------:|
| `@DS` 注解 (类/方法级别) | ✅ | ✅ |
| SpEL 表达式 (`#header`, `#session`, 方法参数) | ✅ | ✅ |
| 数据源分组 + 轮询/随机负载均衡 | ✅ | ✅ |
| 严格模式 (找不到数据源时抛异常) | ✅ | ✅ |
| `@DSTransactional` 多数据源本地事务 | ✅ | ✅ |
| Seata 分布式事务集成 | ✅ | ✅ |
| ENC 配置加密 (AES/可扩展SPI) | ✅ | ✅ |
| Actuator 健康检查 (`/actuator/health`) | ✅ | ✅ |
| 优雅关闭 (ContextClosedEvent) | ✅ | ✅ |
| Calcite 跨源 JOIN 查询 | ✅ | ❌ **独家** |
| SQL 物理化路由 (@MaterializedRoute) | ✅ | ❌ **独家** |
| 分区路由表 (PartitionRoutingTable) | ✅ | ❌ **独家** |

---

## 🚀 快速开始

### Maven依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-datasource-starter</artifactId>
    <version>4.0.0.41</version>
</dependency>
```

### 最简配置

```yaml
plugin:
  multi-datasource:
    data-source:
      - name: master
        url: jdbc:mysql://localhost:3306/master_db
        username: root
        password: password
        driver-class-name: com.mysql.cj.jdbc.Driver
      - name: slave
        url: jdbc:mysql://localhost:3306/slave_db
        username: root
        password: password
        driver-class-name: com.mysql.cj.jdbc.Driver
```

---

## 📚 核心功能详解

### 1. `@DS` 注解 —— 数据源切换

#### 方法级别

```java
@Service
public class UserService {

    @DS("master")
    public User save(User user) { return userRepository.save(user); }

    @DS("slave")
    public List<User> findAll() { return userRepository.findAll(); }
}
```

#### 类级别

```java
@Service
@DS("slave")   // 整个类默认使用 slave
public class ReportService {

    public List<Report> generate() { return reportRepository.findAll(); }

    @DS("master")  // 方法覆盖类级别
    public void save(Report r) { reportRepository.save(r); }
}
```

#### Mapper 级别

```java
@Mapper
@DS("slave")
public interface UserMapper {

    List<User> selectAll();

    @DS("master")
    int insert(User user);
}
```

#### 逗号分隔聚合查询

```java
@DS("master,slave")  // 跨 master 和 slave 两个数据源查询
public List<User> queryAll() { ... }
```

---

### 2. SpEL 表达式 —— 动态数据源路由

`@DS` 支持以 `#` 开头的 SpEL 表达式，在运行时根据上下文动态决定数据源。

#### 语法总览

| 表达式 | 说明 | 示例 |
|--------|------|------|
| `#参数名` | 使用方法参数 | `@DS("#tenantId")` |
| `#header.xxx` | 使用请求头 | `@DS("#header.xTenantId")` |
| `#header['xxx']` | 请求头（下标语法） | `@DS("#header['xTenantId']")` |
| `#session.xxx` | 使用 Session 属性 | `@DS("#session.userType")` |
| `#request` | 直接使用 HttpServletRequest | 用于复杂自定义逻辑 |

#### 用法示例

```java
@RestController
public class OrderController {

    // 从请求头动态获取租户ID，路由到对应数据源
    @GetMapping("/orders")
    @DS("#header.xTenantId")
    public List<Order> listOrders() { ... }

    // 从请求头获取数据源名称，带默认值
    @GetMapping("/users")
    @DS("#header['x-datasource'] ?: 'master'")
    public List<User> listUsers() { ... }

    // 使用方法参数
    @GetMapping("/tenant/{tenantId}/data")
    @DS("#tenantId")
    public List<Data> getTenantData(@PathVariable String tenantId) { ... }

    // 从 Session 获取用户类型
    @GetMapping("/profile")
    @DS("#session.userType == 'vip' ? 'master' : 'slave'")
    public Profile getProfile() { ... }
}
```

---

### 3. 数据源分组 + 负载均衡

当多个数据源属于同一逻辑分组时（如 `slave_1`, `slave_2`），无需额外配置，系统自动将 `_数字` 后缀的数据源归入同一分组。

#### 自动分组规则

```
slave_1, slave_2  →  自动归入 slave 组
read_1, read_2    →  自动归入 read 组
master            →  不包含 `_数字` 后缀，不属于任何分组
```

#### 配置示例

```yaml
plugin:
  multi-datasource:
    load-balance: round_robin    # 全局负载均衡策略：round_robin / random
    data-source:
      - name: master
        url: jdbc:mysql://master:3306/db
      - name: slave_1             # 自动归入 slave 组
        url: jdbc:mysql://slave1:3306/db
      - name: slave_2             # 自动归入 slave 组
        url: jdbc:mysql://slave2:3306/db
```

#### 使用分组

```java
@DS("slave")   // 自动在 slave_1 和 slave_2 之间负载均衡
public List<User> findAll() { ... }
```

#### 支持的负载均衡策略

| 策略 | 配置值 | 说明 |
|------|--------|------|
| 轮询 | `round_robin` | 按顺序轮流分配（默认） |
| 随机 | `random` | 随机选择，适合短连接场景 |

---

### 4. 严格模式

默认情况下，当 `@DS` 指定的数据源不存在时，回退到默认数据源（`master`）。开启严格模式后，直接抛出异常，避免误操作。

#### 配置

```yaml
plugin:
  multi-datasource:
    strict: true   # 开启严格模式（默认 false）
```

#### 效果

```java
@DS("nonexistent_ds")   // 严格模式下抛出 IllegalStateException
public void test() { ... }
```

#### 异常信息

```
[动态数据源] 严格模式：找不到数据源 [nonexistent_ds]，请检查 @DS 注解或配置
```

---

### 5. `@DSTransactional` —— 多数据源本地事务

`@DSTransactional` 确保在动态多数据源场景下，数据源上下文在事务方法中正确切换和管理。

> **设计说明**：`@DSTransactional` **不试图实现跨数据源 ACID**（跨库一致性请使用 Seata）。其核心作用是确保 `@DS` 切换在事务绑定 **之前** 完成，并通过栈式（Deque）上下文管理支持嵌套切换。

#### 基本用法

```java
@Service
public class UserService {

    @DSTransactional
    @DS("master")
    public void saveUser(User user) {
        userDao.insert(user);                         // 使用 master

        UserLog log = logService.getLastLog(user.getId()); // 嵌套切换 slave

        userDao.updateLog(user.getId(), log.getId());      // 恢复 master
    }
}
```

#### 嵌套传播

```java
@DSTransactional
@Service
public class OrderService {

    public void createOrder(Order order) {
        orderDao.insert(order);                  // 外层事务
        paymentService.processPayment(order);    // 嵌套 @DSTransactional(propagate=true)
    }
}

@DSTransactional(propagate = true)  // 继承外层上下文，不创建独立事务
public class PaymentService {
    public void processPayment(Order order) {
        paymentDao.insert(order);   // 参与外层事务
    }
}
```

#### 注解属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | `""` | 事务名称（日志/监控用） |
| `name` | String | `""` | 事务名称（与 value 互为别名） |
| `propagate` | boolean | `true` | 嵌套调用时是否传播外部数据源上下文 |

#### 优先级说明

- `@DSTransactional` 切面优先级为 `Ordered.LOWEST_PRECEDENCE - 10`
- 在 Spring 的 `TransactionInterceptor`（默认 `LOWEST_PRECEDENCE`）**之前**执行
- 确保数据源上下文在事务绑定前完成切换

---

### 6. Seata 分布式事务

用户需自行引入 `seata-spring-boot-starter`，然后使用 `@GlobalTransactional` 即可。

#### Maven 依赖

```xml
<dependency>
    <groupId>org.apache.seata</groupId>
    <artifactId>seata-spring-boot-starter</artifactId>
    <version>2.6.0</version>
</dependency>
```

#### 配置

```yaml
seata:
  enabled: true                       # 默认开启
  application-id: my-app
  tx-service-group: my_tx_group
  enable-auto-data-source-proxy: false  # 我们自行代理，避免冲突
  data-source-proxy-mode: AT            # AT / XA
```

#### 使用

```java
@GlobalTransactional
public void transfer() {
    accountDao.debit();      // 数据源 A（自动被 Seata DataSourceProxy 包装）
    accountDao.credit();     // 数据源 B（自动被 Seata DataSourceProxy 包装）
}
```

> **自动配置说明**：当检测到类路径存在 `io.seata.core.context.RootContext` 时，自动为所有已注册的动态数据源包装 `DataSourceProxy`，无需手动配置。

---

### 7. ENC 配置加密

支持对配置项中的密码等敏感信息进行加密存储，运行时自动解密。

> **默认关闭**，需显式配置 `plugin.datasource.enc.enabled=true` 启用。

#### 配置

```yaml
plugin:
  datasource:
    enc:
      enabled: true                     # 启用 ENC 解密（必须显式设为 true）
      secret-key: mySecretKey12345      # AES 加密密钥（推荐配置）

# 使用加密密码
spring:
  datasource:
    password: ENC(uX5z1H+z5f3k8L9mW2qR4w==)
    url: ENC(jdbc:mysql://localhost:3306/db)
```

#### 加密格式

| 格式 | 说明 |
|------|------|
| `ENC(base64密文)` | 使用 AES 解密（默认算法） |
| `ENC(AES:base64密文)` | 显式指定 AES 算法 |

#### 生成密文（Java 工具代码）

```java
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class EncryptUtil {
    public static String encrypt(String plainText, String secretKey) throws Exception {
        byte[] key = secretKey.getBytes("UTF-8");
        // 不足 32 字节时补齐
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        key = sha.digest(key);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes("UTF-8")));
    }
}
```

#### 自定义解密器 SPI

实现 `PasswordDecryptor` 接口，通过 SPI 注册：

```java
public class MyDecryptor implements PasswordDecryptor {
    @Override
    public boolean supports(String algorithm) {
        return "SM4".equals(algorithm);
    }
    @Override
    public String decrypt(String encryptedValue) {
        // 国密 SM4 解密逻辑
    }
}
```

---

### 8. Actuator 健康检查

集成 Spring Boot Actuator，自动检查所有已注册数据源的连接健康状态。

#### 配置

```yaml
plugin:
  datasource:
    health:
      enabled: true                     # 默认开启

# Actuator 端点暴露
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

#### 响应示例

```json
{
  "status": "DEGRADED",
  "components": {
    "dataSource": {
      "status": "DEGRADED",
      "details": {
        "total": 3,
        "up": 2,
        "down": 1,
        "dataSources": {
          "master":  { "status": "UP",   "responseTime": 5  },
          "slave_1": { "status": "UP",   "responseTime": 8  },
          "slave_2": { "status": "DOWN", "error": "连接超时" }
        }
      }
    }
  }
}
```

#### 健康状态规则

| 条件 | 状态 |
|------|------|
| 所有数据源正常 | `UP` |
| 部分数据源异常 | `DEGRADED` |
| 所有数据源异常 | `DOWN` |
| 无已注册数据源 | `OUT_OF_SERVICE` |

> 需引入 `spring-boot-starter-actuator` 依赖。

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

---

### 9. 优雅关闭

应用关闭时，自动遍历所有已注册数据源并释放连接池资源。

#### 配置

```yaml
plugin:
  datasource:
    graceful-shutdown:
      enabled: true                     # 默认开启
```

#### 关闭策略（按优先级）

1. `DisposableBean.destroy()` —— HikariCP、Druid 等
2. `AutoCloseable.close()` —— 通用
3. `java.io.Closeable.close()` —— 通用
4. 反射关闭 —— HikariCP、Druid、DBCP2、Tomcat JDBC

#### 清理内容

- 关闭所有连接池
- 归还 Calcite 连接池 (MultiDataSource)
- 清除数据源分组注册表
- 输出关闭统计（耗时、成功数、失败数）

---

## 📋 完整配置参考

### 全局配置

```yaml
plugin:
  # ========== 多数据源设置 ==========
  multi-datasource:
    force-annotation: true          # 是否强制使用注解上的数据源（默认 true）
    strict: false                   # 严格模式：true=找不到抛异常，false=回退默认（默认 false）
    load-balance: round_robin       # 负载均衡策略：round_robin / random（默认 round_robin）

  # ========== 数据源脚本初始化 ==========
  datasource:
    script:
      enable: true
      script-path: classpath*:db/init/*.sql
      scan-mode: ONCE
      continue-on-error: true

    # ========== ENC 密码加密（默认关闭） ==========
    enc:
      enabled: false                 # 需显式设为 true 才启用
      secret-key: ""                 # AES 加密密钥（建议配置）

    # ========== Actuator 健康检查 ==========
    health:
      enabled: true                  # 默认开启

    # ========== 优雅关闭 ==========
    graceful-shutdown:
      enabled: true                  # 默认开启

  # ========== 事务配置 ==========
  transaction:
    enable: true
    timeout: 30                      # 事务超时时间（秒）
    read-only: "get*,find*,list*,query*,select*"
    write-only: "save*,insert*,update*,delete*,remove*"
    no-tx: "count*,exists*"
```

---

## 🔧 自定义扩展

### 自定义数据源路由

```java
@Component
public class CustomRouter {
    public String route(String operation, Object... params) {
        if (operation.startsWith("read")) return "slave";
        if (operation.startsWith("write")) return "master";
        return "master";
    }
}
```

### 自定义密码解密器

```java
public class Sm4Decryptor implements PasswordDecryptor {
    @Override
    public boolean supports(String algorithm) {
        return "SM4".equals(algorithm);
    }
    @Override
    public String decrypt(String encryptedValue) {
        // 国密 SM4 解密
    }
}
```

---

## 🐛 故障排除

| 问题 | 原因 | 解决 |
|------|------|------|
| `@DS` 切换不生效 | 方法未被 Spring AOP 代理 | 确保方法为 `public`，且通过注入的 Bean 调用（非 `this.xxx()`） |
| SpEL 表达式报错 | 表达式语法错误或上下文变量不存在 | 检查日志中 `SpEL 表达式解析失败` 警告 |
| 严格模式抛异常 | 指定的数据源不存在 | 检查配置中的数据源名称 |
| ENC 解密失败 | 密钥不匹配或密文格式错误 | 确认 `secret-key` 与加密时一致，密文为 Base64 编码 |
| 健康检查显示 DOWN | 数据源连接不可达 | 检查数据库服务状态和网络连通性 |
| `@DSTransactional` 不生效 | 切面未加载 | 确认 aspectjweaver 在类路径上 |

---

### 10. 跨数据源查询 (MultiDataSource + Calcite JOIN)

支持通过逗号分割 `@DS` 跨多个数据源执行 JOIN 查询，底层基于 Apache Calcite。

```java
@DS("master,slave")
public List<Map<String, Object>> crossQuery() {
    String sql = """
        SELECT u.name, o.total 
        FROM master.users u 
        JOIN slave.orders o ON u.id = o.user_id
        WHERE o.status = 'COMPLETED'
        """;
    // ... 执行 SQL 查询，跨 master.users 和 slave.orders 两个库的关联查询
}
```

也支持直接标在 Mapper 接口或方法上：

```java
@Mapper
@DS("slave")  // 接口级别默认使用 slave
public interface UserMapper {

    List<User> selectAll();

    @DS("master")  // 方法级别覆盖接口级别
    int insert(User user);
}
```

---

### 11. SQL 物理化路由 (@MaterializedRoute)

用于给小表查询增加一层 Calcite 内存物理化副本。不改业务 SQL、不改 Mapper XML，通过注解启用。

- 查询优先命中内存副本，失败自动回源库
- `insert` 直接更新内存表
- `update/delete` 重建受影响表

#### 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-mybatis-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>utils-support-calcite-starter</artifactId>
</dependency>
```

#### 配置

```yaml
plugin:
  datasource:
    materialized:
      enabled: true                     # 是否启用（默认 false）
      default-threshold: 1000           # 阈值，只有小表查询才走内存副本
      refresh-interval-seconds: 0       # 自动刷新窗口（0=关闭）
      skip-query-in-transaction: true   # 事务中默认跳过物理化查询
      cache-data-source-prefix: materialized#  # 动态数据源注册前缀
```

> 兼容性说明：旧前缀 `spring.datasource.materialized` 仍兼容。

#### @MaterializedRoute 注解

标在 Service 或 Mapper 方法上：

```java
import com.chua.starter.datasource.annotation.MaterializedRoute;

@Service
public class UserReportService {

    @MaterializedRoute(threshold = 500, dataSource = "master")
    public List<UserReportVO> querySmallTableReport() {
        return userReportMapper.querySmallTableReport();
    }
}
```

#### @MaterializedRefresh 注解

主动重建内存副本：

```java
import com.chua.starter.datasource.annotation.MaterializedRefresh;

@Service
public class DictRefreshService {

    @MaterializedRefresh(dataSource = "master")
    public List<DictItem> reloadDict() {
        return dictMapper.selectAll();
    }
}
```

#### 运行行为

1. MyBatis 拦截器解析原始 SQL，提取表名和参数
2. 命中注解且涉及表均小于阈值时，在 Calcite 中构建内存副本
3. 查询优先在内存副本执行，失败自动回退原始数据源
4. `insert ... values ...` 直接追加到内存表
5. `update/delete` 触发受影响表全量重建
6. `@MaterializedRefresh` 方法执行成功后主动重建物理化副本

#### 适用范围

- 面向小表、码表、配置表、轻量维度表
- 裸 `JdbcTemplate` 不会自动走物理化（当前接入点是 MyBatis）
- 复杂 SQL 可能自动回源或退化为全量重建
- 事务内强一致读建议保持 `skip-query-in-transaction: true`

---

### 12. HikariCP 连接池详细配置

```yaml
plugin:
  multi-datasource:
    hikari:
      data-source:
        - name: master
          jdbc-url: jdbc:mysql://localhost:3306/master_db  # Hikari 推荐使用 jdbc-url
          username: root
          password: password
          driver-class-name: com.mysql.cj.jdbc.Driver
          max-pool-size: 20          # 最大连接数
          minimum-idle: 5            # 最小空闲连接数
          connection-timeout: 30000  # 获取连接超时(毫秒)
          idle-timeout: 600000       # 空闲连接超时(毫秒)
          max-lifetime: 1800000      # 连接最大生命周期(毫秒)
          leak-detection-threshold: 60000  # 连接泄漏检测阈值(毫秒)
          connection-test-query: SELECT 1
          validation-timeout: 5000
          auto-commit: true
          read-only: false
          connection-init-sql: SET NAMES utf8mb4
```

> 推荐前缀 `plugin.multi-datasource.hikari`，旧前缀 `spring.multi-datasource.hikari` 仍兼容。

---

### 13. P6Spy SQL 监控配置

```properties
# spy.properties
modulelist=com.p6spy.engine.spy.P6SpyFactory
logMessageFormat=com.p6spy.engine.spy.appender.CustomLineFormat
customLogMessageFormat=%(currentTime) | %(executionTime) ms | %(category) | %(sql)
appender=com.p6spy.engine.spy.appender.Slf4JLogger
logfile=spy.log
append=true
dateformat=yyyy-MM-dd HH:mm:ss
filter=false
exclude=
excludecategories=info,debug,result,resultset
```

### 14. 编程式数据源切换

```java
@Service
public class DataMigrationService {

    public void migrateData() {
        try {
            DataSourceContextSupport.setDbType("source");
            List<Data> sourceData = dataRepository.findAll();

            DataSourceContextSupport.setDbType("target");
            dataRepository.saveAll(sourceData);

        } finally {
            DataSourceContextSupport.clearDbType();
        }
    }
}
```

### 15. 自定义事务拦截器

```java
@Component
public class CustomTransactionInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        String methodName = method.getName();
        if (methodName.startsWith("batch")) {
            return executeInNewTransaction(invocation);
        }
        return invocation.proceed();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private Object executeInNewTransaction(MethodInvocation invocation) throws Throwable {
        return invocation.proceed();
    }
}
```

---

## 🐛 高级故障排除

### 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| `@DS` 切换不生效 | 方法未被 Spring AOP 代理 | 确保方法为 `public`，且通过注入的 Bean 调用（非 `this.xxx()`） |
| 连接池耗尽 | 连接未正确关闭 | 调整连接池配置，启用泄漏检测 |
| 事务不生效 | 方法非 public 或异常类型不匹配 | 确认 `@Transactional` 在 public 方法上，异常继承 RuntimeException |
| SpEL 表达式报错 | 表达式语法错误或上下文变量不存在 | 检查日志中 `SpEL 表达式解析失败` 警告 |
| 严格模式抛异常 | 指定的数据源不存在 | 检查配置中的数据源名称 |
| ENC 解密失败 | 密钥不匹配或密文格式错误 | 确认 `secret-key` 与加密时一致，密文为 Base64 编码 |
| 健康检查显示 DOWN | 数据源连接不可达 | 检查数据库服务状态和网络连通性 |
| `@DSTransactional` 不生效 | 切面未加载 | 确认 aspectjweaver 在类路径上 |
| 物理化查询未命中 | 缺少依赖或阈值超限 | 检查 `spring-support-mybatis-starter`、`utils-support-calcite-starter`、`enabled` 配置、`@MaterializedRoute` 注解 |

### 调试建议

```yaml
logging:
  level:
    com.chua.starter.datasource: DEBUG
    com.zaxxer.hikari: DEBUG
    org.springframework.transaction: DEBUG
```

---

## 📌 注意事项

1. **数据源切换**：使用 `@DS` 时，确保在事务边界内保持数据源一致性
2. **SpEL 执行时机**：SpEL 在 `@DS` 解析阶段执行，早于方法参数验证
3. **分组命名**：分组依赖 `_数字` 后缀，如 `slave_1`、`slave_2`
4. **ENC 密钥安全**：自定义密钥不要硬编码在代码中，推荐通过环境变量注入
5. **跨数据源事务**：`@DSTransactional` 不保证跨库 ACID，跨库一致性请使用 Seata
6. **Actuator 依赖**：健康检查需要 `spring-boot-starter-actuator` 在类路径上
7. **Seata 版本兼容**：建议 Seata Server 与 `seata-spring-boot-starter` 版本保持一致
8. **物理化路由依赖**：需要显式引入 `spring-support-mybatis-starter` + `utils-support-calcite-starter`

---

## 📊 版本兼容性

| 项目 | 版本 |
|------|------|
| Java | 25+ |
| Spring Boot | 3.5.x |
| Seata (可选) | 2.6.x |
| Lombok | 1.18.46+ |
| Calcite | 1.37.0 |
| P6Spy | 3.9.1 |
| H2 (物理化副本) | 最新 |

---

## 🔗 相关模块

| 模块 | 说明 |
|------|------|
| `spring-support-mybatis-starter` | MyBatis 集成，物理化路由的拦截器所在 |
| `utils-support-calcite-starter` | Calcite 物理化副本核心实现 |
| `spring-support-common-starter` | 通用工具模块（Actuator 认证过滤器等） |
