# Spring Support Datasource Starter

## 📖 模块简介

Spring Support Datasource Starter 是一个功能强大的数据源配置和管理模块，提供了企业级应用中数据源的配置、管理和监控功能。该模块支持多数据源配置、动态数据源切换、连接池管理、事务配置以及SQL监控等功能。

## ✨ 主要功能

### 🗄️ 多数据源支持
- 支持配置和管理多个数据源
- 动态数据源切换
- 基于注解的数据源选择
- 数据源健康检查

### 📊 SQL监控和分析
- 集成P6Spy进行SQL监控
- SQL性能分析
- 慢查询检测
- SQL执行统计

### 🔄 连接池管理
- 支持HikariCP连接池
- 连接池配置和监控
- 连接泄漏检测
- 连接池性能优化

### 🔧 SQL解析和优化
- 集成Apache Calcite进行SQL解析
- 跨数据源查询支持
- SQL优化建议
- 查询计划分析

### 💼 事务管理
- 分布式事务支持
- 本地事务管理
- 事务传播配置
- 事务超时控制

## 🚀 快速开始

### Maven依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-datasource-starter</artifactId>
    <version>4.0.0.41</version>
</dependency>
```

### 基础配置

```yaml
# 多数据源配置
plugin:
  multi-datasource:
    # 普通 JDBC 数据源列表
    data-source:
      - name: master
        url: jdbc:mysql://localhost:3306/master_db  # 主库连接地址
        username: root                              # 数据库用户名
        password: password                          # 数据库密码
        driver-class-name: com.mysql.cj.jdbc.Driver # 驱动类
      - name: slave
        url: jdbc:mysql://localhost:3306/slave_db   # 从库连接地址
        username: root
        password: password
        driver-class-name: com.mysql.cj.jdbc.Driver

# 多数据源设置
plugin:
  multi-datasource:
    force-annotation: true  # 是否强制使用注解指定的数据源名称
  
  # 事务配置
  transaction:
    enable: true
    timeout: 30  # 事务超时时间（秒）
    read-only: "get*,find*,list*,query*,select*"  # 只读事务方法前缀
    write-only: "save*,insert*,update*,delete*,remove*"  # 写事务方法前缀
    no-tx: "count*,exists*"  # 无事务方法前缀
```

### 数据库脚本初始化

```yaml
plugin:
  datasource:
    script:
      enable: true
      script-path: classpath*:db/init/*.sql
      scan-mode: ONCE               # 表结构脚本: NONE / ONCE / ALWAYS，默认 ONCE
      migration-scan-mode: ALWAYS  # 升级补丁脚本: NONE / ALWAYS，默认 ALWAYS
      data-scan-mode: ONCE         # 初始化数据脚本: NONE / ONCE / ALWAYS，默认 ONCE
      continue-on-error: true
      version-table: sys_database_version
```

推荐目录组织：

- `db/init/V1.0__init_xxx_complete.sql`：模块结构初始化
- `db/init/V1.0__initdata_xxx.sql`：模块初始化数据

兼容说明：

- starter 仍兼容 `db/migration` 与 `db/sync`
- 新模块和已完成收敛的模块，推荐统一只使用 `db/init`
- `initdata` 不需要单独目录，按命名规范放在 `db/init` 即可

- `scan-mode=NONE`：完全不扫描表结构脚本
- `scan-mode=ONCE`：仅当当前库没有业务表时执行表结构脚本
- `scan-mode=ALWAYS`：每次启动都执行表结构脚本
- `migration-scan-mode=NONE`：不执行升级补丁脚本
- `migration-scan-mode=ALWAYS`：每次启动都扫描并按版本记录执行升级补丁脚本
- `data-scan-mode=NONE`：不执行初始化数据脚本
- `data-scan-mode=ONCE`：仅当当前库没有业务数据时执行初始化数据脚本
- `data-scan-mode=ALWAYS`：每次启动都执行初始化数据脚本

## 📋 详细功能说明

### 1. 多数据源配置

#### HikariCP连接池配置

```yaml
plugin:
  multi-datasource:
    hikari:
      # Hikari 连接池数据源列表
      data-source:
        - name: master
          jdbc-url: jdbc:mysql://localhost:3306/master_db  # Hikari 推荐使用 jdbc-url
          username: root
          password: password
          driver-class-name: com.mysql.cj.jdbc.Driver
          max-pool-size: 20          # 最大连接数
          minimum-idle: 5            # 最小空闲连接数
          connection-timeout: 30000  # 获取连接超时，单位毫秒
          idle-timeout: 600000       # 空闲连接超时，单位毫秒
          max-lifetime: 1800000      # 连接最大生命周期，单位毫秒
          leak-detection-threshold: 60000  # 连接泄漏检测阈值
        - name: slave
          jdbc-url: jdbc:mysql://localhost:3306/slave_db
          username: root
          password: password
          driver-class-name: com.mysql.cj.jdbc.Driver
          max-pool-size: 10
          minimum-idle: 2
```

#### 使用@DS注解切换数据源

```java
@Service
public class UserService {
    
    @DS("master")  // 使用master数据源
    public User saveUser(User user) {
        return userRepository.save(user);
    }
    
    @DS("slave")   // 使用slave数据源
    public List<User> findUsers() {
        return userRepository.findAll();
    }
    
    // 不指定数据源，使用默认数据源（master）
    public User findById(Long id) {
        return userRepository.findById(id);
    }
}
```

#### 类级别数据源配置

```java
@Service
@DS("slave")  // 整个类默认使用slave数据源
public class ReportService {
    
    public List<Report> generateReport() {
        // 使用slave数据源
        return reportRepository.findAll();
    }
    
    @DS("master")  // 方法级别覆盖类级别配置
    public void saveReport(Report report) {
        // 使用master数据源
        reportRepository.save(report);
    }
}
```

### 2. 动态数据源切换

#### 编程式数据源切换

```java
@Service
public class DataMigrationService {
    
    public void migrateData() {
        try {
            // 切换到源数据源
            DataSourceContextSupport.setDbType("source");
            List<Data> sourceData = dataRepository.findAll();
            
            // 切换到目标数据源
            DataSourceContextSupport.setDbType("target");
            dataRepository.saveAll(sourceData);
            
        } finally {
            // 清除数据源设置
            DataSourceContextSupport.clearDbType();
        }
    }
}
```

#### 数据源管理

```java
@Component
public class DataSourceManager {
    
    @Autowired
    private DynamicDataSource dynamicDataSource;
    
    public void addDataSource(String name, DataSource dataSource) {
        DataSourceContextSupport.addDatasource(name, dataSource);
    }
    
    public DataSource getDataSource(String name) {
        return DataSourceContextSupport.getDatasource(name);
    }
    
    public boolean hasDataSource(String name) {
        return DataSourceContextSupport.hasDbType(name);
    }
}
```

### 3. 事务管理配置

#### 详细事务配置

```yaml
plugin:
  transaction:
    enable: true
    timeout: 30  # 全局事务超时时间
    tx-mapper: "com.example.service"  # 事务拦截包路径
    
    # 只读事务方法模式（支持通配符）
    read-only: |
      get*,find*,list*,query*,select*,
      search*,count*,exists*,load*
    
    # 写事务方法模式
    write-only: |
      save*,insert*,add*,create*,
      update*,modify*,edit*,
      delete*,remove*,drop*,
      batch*,import*,export*
    
    # 无事务方法模式
    no-tx: |
      validate*,check*,verify*,
      calculate*,compute*,format*
```

#### 自定义事务配置

```java
@Service
@Transactional(readOnly = true)  // 类级别默认只读事务
public class UserService {
    
    // 继承类级别配置，只读事务
    public List<User> findUsers() {
        return userRepository.findAll();
    }
    
    @Transactional  // 覆盖为写事务
    public User saveUser(User user) {
        return userRepository.save(user);
    }
    
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.READ_COMMITTED,
        timeout = 60,
        rollbackFor = Exception.class
    )
    public void complexOperation() {
        // 复杂业务操作
    }
}
```

### 4. 跨数据源查询

#### 使用MultiDataSource进行跨数据源查询

```java
@Service
public class CrossDataSourceService {
    
    @Autowired
    private MultiDataSource multiDataSource;
    
    public List<Map<String, Object>> crossQuery() {
        String sql = """
            SELECT u.name, o.total 
            FROM master.users u 
            JOIN slave.orders o ON u.id = o.user_id
            WHERE o.status = 'COMPLETED'
            """;
        
        try (Connection conn = multiDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            List<Map<String, Object>> results = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("name", rs.getString("name"));
                row.put("total", rs.getBigDecimal("total"));
                results.add(row);
            }
            return results;
        }
    }
}
```

也支持直接标在 Mapper 接口或 Mapper 方法上：

```java
@Mapper
@DS("slave")  // 接口级别默认使用 slave
public interface UserMapper {

    List<User> selectAll();

    @DS("master")  // 方法级别覆盖接口级别
    int insert(User user);
}
```

### 5. SQL 物理化路由

该能力用于给小表查询增加一层 Calcite 内存物理化副本。

- 不改业务 SQL
- 不改 Mapper XML
- 通过注解启用
- 查询优先命中内存副本，失败自动回源库
- `insert` 直接更新内存表
- `update/delete` 重建受影响表

#### 接入依赖

如果要启用该能力，建议显式引入以下依赖：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-datasource-starter</artifactId>
    <version>4.0.0.41</version>
</dependency>

<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-mybatis-starter</artifactId>
    <version>4.0.0.41</version>
</dependency>

<dependency>
    <groupId>com.chua</groupId>
    <artifactId>utils-support-calcite-starter</artifactId>
    <version>4.0.0.40</version>
</dependency>
```

说明：

- 当前接入点在 MyBatis 拦截器，因此建议和 `spring-support-mybatis-starter` 一起使用
- `utils-support-calcite-starter` 提供物理化核心实现，`spring-support-datasource-starter` 只负责 Spring 接入

#### 配置示例

```yaml
plugin:
  datasource:
    materialized:
      enabled: true                         # 是否启用 SQL 物理化路由
      default-threshold: 1000              # 默认阈值，只有小表查询才走内存副本
      refresh-interval-seconds: 0          # 自动刷新窗口，单位秒；默认 0 表示关闭自动刷新
      skip-query-in-transaction: true      # 事务中默认跳过物理化查询
      cache-data-source-prefix: materialized#  # 动态数据源注册前缀
```

配置项说明：

- `enabled`: 是否启用物理化路由
- `default-threshold`: 默认表行数阈值，只有查询涉及表均不超过阈值时才触发物理化
- `refresh-interval-seconds`: 内存副本自动刷新时间窗口，默认 `0` 表示关闭自动刷新
- `skip-query-in-transaction`: 事务内默认跳过物理化查询
- `cache-data-source-prefix`: 内存副本注册到动态数据源中的名称前缀

兼容性说明：

- 当前推荐前缀：`plugin.datasource.materialized`
- 旧前缀 `spring.datasource.materialized` 仍兼容

#### 注解使用

标在 Service 方法上：

```java
import com.chua.starter.datasource.annotation.MaterializedRoute;
import org.springframework.stereotype.Service;

@Service
public class UserReportService {

    @MaterializedRoute(threshold = 500, dataSource = "master")
    public List<UserReportVO> querySmallTableReport() {
        return userReportMapper.querySmallTableReport();
    }
}
```

也可以直接标在 Mapper 方法上：

```java
import com.chua.starter.datasource.annotation.MaterializedRoute;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserReportMapper {

    @MaterializedRoute(threshold = 500)
    List<UserReportVO> querySmallTableReport();
}
```

#### 手动刷新注解

当自动刷新关闭，或者你希望在某个业务动作后主动重建内存副本时，可以使用 `@MaterializedRefresh`：

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

也支持直接标在 Mapper 方法上：

```java
@Mapper
public interface DictMapper {

    @MaterializedRefresh
    List<DictItem> selectAll();
}
```

#### 运行行为

1. MyBatis 拦截器会解析原始 SQL，提取表名和参数。
2. 如果命中注解且涉及表均小于阈值，则在 Calcite 中构建内存副本。
3. 查询优先在内存副本执行，失败时自动回退到原始数据源。
4. `insert ... values ...` 会直接追加到内存表。
5. `update/delete` 会触发受影响表全量重建。
6. 标了 `@MaterializedRefresh` 的方法在执行成功后，会主动重建受影响表的物理化副本。

#### 适用范围和限制

- 当前主要面向小表、码表、配置表、轻量维度表。
- 裸 `JdbcTemplate` 查询不会自动走物理化，当前接入点是 MyBatis。
- 复杂 `insert ... select`、特殊方言 SQL、极复杂联表 SQL，可能自动回源或退化为全量重建。
- 如果业务要求事务内强一致读，建议保持 `skip-query-in-transaction: true`。

### 6. 数据源监控

#### 数据源健康检查

```java
@Component
public class DataSourceHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        try {
            // 检查主数据源
            DataSource masterDs = DataSourceContextSupport.getDatasource("master");
            checkDataSource(masterDs, "master");
            
            // 检查从数据源
            DataSource slaveDs = DataSourceContextSupport.getDatasource("slave");
            checkDataSource(slaveDs, "slave");
            
            builder.up();
        } catch (Exception e) {
            builder.down().withException(e);
        }
        
        return builder.build();
    }
    
    private void checkDataSource(DataSource dataSource, String name) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(5)) {
                throw new SQLException("DataSource " + name + " is not valid");
            }
        }
    }
}
```

## ⚙️ 高级配置

### P6Spy SQL监控配置

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

### 连接池详细配置

```yaml
plugin:
  multi-datasource:
    hikari:
      data-source:
        - name: master
          # 连接配置
          jdbc-url: jdbc:mysql://localhost:3306/master_db
          username: root
          password: password
          driver-class-name: com.mysql.cj.jdbc.Driver
          
          # 连接池大小配置
          max-pool-size: 20          # 最大连接数
          minimum-idle: 5            # 最小空闲连接数
          
          # 超时配置
          connection-timeout: 30000  # 连接超时时间（毫秒）
          idle-timeout: 600000       # 空闲超时时间（毫秒）
          max-lifetime: 1800000      # 连接最大生存时间（毫秒）
          
          # 监控配置
          leak-detection-threshold: 60000  # 连接泄漏检测阈值（毫秒）
          
          # 连接测试
          connection-test-query: SELECT 1
          validation-timeout: 5000
          
          # 其他配置
          auto-commit: true
          read-only: false
          catalog: master_db
          connection-init-sql: SET NAMES utf8mb4
```

> 兼容性说明：当前版本优先使用 `plugin.multi-datasource` / `plugin.multi-datasource.hikari`，同时兼容旧前缀 `spring.multi-datasource` / `spring.multi-datasource.hikari`。

## 🔧 自定义扩展

### 自定义数据源路由策略

```java
@Component
public class CustomDataSourceRouter {
    
    public String determineDataSource(String operation, Object... params) {
        // 根据操作类型和参数决定数据源
        if (operation.startsWith("read")) {
            return "slave";
        } else if (operation.startsWith("write")) {
            return "master";
        }
        
        // 根据参数中的租户ID选择数据源
        for (Object param : params) {
            if (param instanceof TenantAware) {
                String tenantId = ((TenantAware) param).getTenantId();
                return "tenant_" + tenantId;
            }
        }
        
        return "master";
    }
}
```

### 自定义事务拦截器

```java
@Component
public class CustomTransactionInterceptor implements MethodInterceptor {
    
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        
        // 根据方法名确定事务类型
        String methodName = method.getName();
        if (methodName.startsWith("batch")) {
            // 批处理操作使用新事务
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

## 📝 注意事项

1. **数据源切换**：使用@DS注解时，确保在事务边界内保持数据源一致性
2. **连接池配置**：根据实际负载调整连接池参数，避免连接泄漏
3. **事务传播**：跨数据源操作时注意事务传播行为
4. **性能监控**：启用SQL监控可能影响性能，生产环境需谨慎配置
5. **数据一致性**：跨数据源事务无法保证ACID特性，需要应用层处理
6. **物理化路由**：当前是小表查询加速方案，不适合超大表和强事务一致性场景

## 🐛 故障排除

### 常见问题

1. **数据源切换不生效**
   - 检查@DS注解是否正确配置
   - 确认方法是否被Spring代理
   - 验证数据源名称是否存在

2. **连接池耗尽**
   - 检查连接是否正确关闭
   - 调整连接池大小配置
   - 启用连接泄漏检测

3. **事务不生效**
   - 确认事务配置是否启用
   - 检查方法访问修饰符（必须是public）
   - 验证异常类型是否触发回滚

4. **物理化查询未命中**
   - 检查是否引入了 `spring-support-mybatis-starter`
   - 检查是否显式引入了 `utils-support-calcite-starter`
   - 检查 `plugin.datasource.materialized.enabled` 是否开启
   - 检查查询涉及表是否超过阈值
   - 检查方法或 Mapper 上是否添加了 `@MaterializedRoute`

### 调试建议

启用调试日志：

```yaml
logging:
  level:
    com.chua.starter.datasource: DEBUG
    com.zaxxer.hikari: DEBUG
    org.springframework.transaction: DEBUG
```
