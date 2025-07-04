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
    <version>4.0.0.32</version>
</dependency>
```

### 基础配置

```yaml
# 多数据源配置
spring:
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

# 多数据源设置
plugin:
  multi-datasource:
    force-annotation: true  # 强制使用注解指定数据源
  
  # 事务配置
  transaction:
    enable: true
    timeout: 30  # 事务超时时间（秒）
    read-only: "get*,find*,list*,query*,select*"  # 只读事务方法前缀
    write-only: "save*,insert*,update*,delete*,remove*"  # 写事务方法前缀
    no-tx: "count*,exists*"  # 无事务方法前缀
```

## 📋 详细功能说明

### 1. 多数据源配置

#### HikariCP连接池配置

```yaml
spring:
  multi-datasource:
    hikari:
      data-source:
        - name: master
          jdbc-url: jdbc:mysql://localhost:3306/master_db
          username: root
          password: password
          driver-class-name: com.mysql.cj.jdbc.Driver
          maximum-pool-size: 20
          minimum-idle: 5
          connection-timeout: 30000
          idle-timeout: 600000
          max-lifetime: 1800000
          leak-detection-threshold: 60000
        - name: slave
          jdbc-url: jdbc:mysql://localhost:3306/slave_db
          username: root
          password: password
          driver-class-name: com.mysql.cj.jdbc.Driver
          maximum-pool-size: 10
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

### 5. 数据源监控

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
spring:
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
          maximum-pool-size: 20      # 最大连接数
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

### 调试建议

启用调试日志：

```yaml
logging:
  level:
    com.chua.starter.datasource: DEBUG
    com.zaxxer.hikari: DEBUG
    org.springframework.transaction: DEBUG
```
