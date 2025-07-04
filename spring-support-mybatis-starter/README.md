# Spring Support MyBatis Starter

## 📖 模块简介

Spring Support MyBatis Starter 是一个基于MyBatis Plus的增强ORM模块，提供了企业级应用中数据库操作的完整解决方案。该模块集成了代码生成、分页插件、乐观锁、数据权限、多租户、SQL监控等功能，大大简化了数据库开发工作。

## ✨ 主要功能

### 🔧 MyBatis Plus增强
- 基于MyBatis Plus的CRUD操作
- 自动代码生成器
- 条件构造器增强
- 自定义SQL方法注入

### 📄 分页和查询
- 高性能分页插件
- 多表关联查询支持
- 动态条件查询
- 复杂查询构建

### 🔒 数据安全
- 乐观锁并发控制
- 数据权限控制
- 多租户数据隔离
- 逻辑删除支持

### 📊 监控和审计
- SQL执行监控
- 性能分析
- 操作审计日志
- Mapper热重载

### 🏗️ 代码生成
- 根据数据库表生成实体类
- 自动生成Mapper接口
- Service层代码生成
- Controller层代码生成

## 🚀 快速开始

### Maven依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-mybatis-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 基础配置

```yaml
# MyBatis Plus配置
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      logic-delete-field: sys_deleted  # 逻辑删除字段
      logic-delete-value: 1            # 删除值
      logic-not-delete-value: 0        # 未删除值
      id-type: auto                    # 主键策略

# 插件配置
plugin:
  mybatis-plus:
    open-xml-reload: true              # 启用XML热重载
    data-scope:
      enable: true                     # 启用数据权限
      tenant-enable: true              # 启用多租户
```

## 📋 详细功能说明

### 1. 基础CRUD操作

#### 实体类定义

```java
@Data
@TableName("sys_user")
public class User {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("username")
    private String username;
    
    @TableField("email")
    private String email;
    
    @TableField("phone")
    private String phone;
    
    @TableField("status")
    private Integer status;
    
    // 逻辑删除字段
    @TableLogic
    @TableField("sys_deleted")
    private Integer deleted;
    
    // 乐观锁字段
    @Version
    @TableField("version")
    private Integer version;
    
    // 自动填充字段
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;
    
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
```

#### Mapper接口

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 自定义查询方法
     */
    @Select("SELECT * FROM sys_user WHERE status = #{status}")
    List<User> findByStatus(@Param("status") Integer status);
    
    /**
     * 复杂查询
     */
    IPage<User> selectUserPage(IPage<User> page, @Param("ew") Wrapper<User> wrapper);
}
```

#### Service层

```java
@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    
    /**
     * 分页查询用户
     */
    public IPage<User> getUserPage(int current, int size, String keyword) {
        Page<User> page = new Page<>(current, size);
        
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(User::getUsername, keyword)
                   .or()
                   .like(User::getEmail, keyword);
        }
        wrapper.eq(User::getStatus, 1)
               .orderByDesc(User::getCreateTime);
        
        return this.page(page, wrapper);
    }
    
    /**
     * 批量操作
     */
    @Transactional
    public boolean batchUpdateStatus(List<Long> ids, Integer status) {
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(User::getId, ids)
               .set(User::getStatus, status);
        
        return this.update(wrapper);
    }
}
```

### 2. 分页查询

#### 基础分页

```java
@RestController
@RequestMapping("/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @GetMapping("/page")
    public IPage<User> getUserPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        
        return userService.getUserPage(current, size, keyword);
    }
}
```

#### 自定义分页查询

```java
@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    
    public IPage<UserVO> getUserVOPage(int current, int size, UserQueryDTO query) {
        Page<UserVO> page = new Page<>(current, size);
        
        // 使用自定义SQL进行分页查询
        return baseMapper.selectUserVOPage(page, query);
    }
}
```

### 3. 数据权限控制

#### 启用数据权限

```yaml
plugin:
  mybatis-plus:
    data-scope:
      enable: true
      tenant-enable: true
      tenant-column: "tenant_id"  # 租户字段
      ignore-tables:              # 忽略多租户的表
        - "sys_config"
        - "sys_dict"
```

#### 使用数据权限注解

```java
@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<User> getUsersByDataScope() {
        // 会自动添加数据权限条件
        return this.list();
    }
}
```

### 4. 多租户支持

#### 租户配置

```java
@Configuration
public class TenantConfig {
    
    @Bean
    public TenantLineInnerInterceptor tenantLineInnerInterceptor() {
        return new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                // 从当前上下文获取租户ID
                String tenantId = TenantContextHolder.getTenantId();
                if (StringUtils.isNotBlank(tenantId)) {
                    return new LongValue(tenantId);
                }
                return new NullValue();
            }
            
            @Override
            public boolean ignoreTable(String tableName) {
                // 忽略系统表
                return "sys_config".equals(tableName) || 
                       "sys_dict".equals(tableName);
            }
        });
    }
}
```

### 5. 自动填充

#### 自动填充处理器

```java
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        
        // 获取当前用户
        String currentUser = getCurrentUser();
        this.strictInsertFill(metaObject, "createBy", String.class, currentUser);
        this.strictInsertFill(metaObject, "updateBy", String.class, currentUser);
    }
    
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        
        String currentUser = getCurrentUser();
        this.strictUpdateFill(metaObject, "updateBy", String.class, currentUser);
    }
    
    private String getCurrentUser() {
        // 从安全上下文获取当前用户
        return SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
    }
}
```

### 6. 代码生成器

#### 代码生成配置

```java
@RestController
@RequestMapping("/generator")
public class CodeGeneratorController {
    
    @PostMapping("/generate")
    public void generateCode(@RequestBody GeneratorConfig config) {
        FastAutoGenerator.create(config.getUrl(), config.getUsername(), config.getPassword())
                .globalConfig(builder -> {
                    builder.author(config.getAuthor())
                           .outputDir(config.getOutputDir())
                           .commentDate("yyyy-MM-dd");
                })
                .packageConfig(builder -> {
                    builder.parent(config.getPackageName())
                           .entity("entity")
                           .mapper("mapper")
                           .service("service")
                           .serviceImpl("service.impl")
                           .controller("controller");
                })
                .strategyConfig(builder -> {
                    builder.addInclude(config.getTableNames())
                           .entityBuilder()
                           .enableLombok()
                           .enableTableFieldAnnotation()
                           .logicDeleteColumnName("sys_deleted")
                           .versionColumnName("version")
                           .addTableFills(
                               new Column("create_time", FieldFill.INSERT),
                               new Column("update_time", FieldFill.INSERT_UPDATE)
                           );
                })
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }
}
```

### 7. SQL监控

#### P6Spy配置

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

#### 性能监控

```java
@Component
public class SqlPerformanceInterceptor implements Interceptor {
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        try {
            return invocation.proceed();
        } finally {
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            if (executionTime > 1000) { // 慢查询阈值1秒
                log.warn("慢查询检测: 执行时间 {} ms", executionTime);
            }
        }
    }
}
```

## ⚙️ 高级配置

### 完整配置示例

```yaml
mybatis-plus:
  # MyBatis配置
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
    call-setters-on-nulls: true
    jdbc-type-for-null: 'null'
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  
  # 全局配置
  global-config:
    banner: false
    db-config:
      id-type: auto
      table-underline: true
      logic-delete-field: sys_deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
      insert-strategy: not_null
      update-strategy: not_null
      select-strategy: not_empty
  
  # Mapper XML文件位置
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.example.entity

# 插件配置
plugin:
  mybatis-plus:
    # XML热重载
    open-xml-reload: true
    
    # 数据权限配置
    data-scope:
      enable: true
      tenant-enable: true
      tenant-column: "tenant_id"
      ignore-tables:
        - "sys_config"
        - "sys_dict"
        - "sys_log"
      
    # 代码生成配置
    generator:
      author: "开发者"
      output-dir: "/tmp/generator"
      package-name: "com.example"
```

## 🔧 自定义扩展

### 自定义SQL方法

```java
@Component
public class CustomSqlInjector extends DefaultSqlInjector {
    
    @Override
    public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);
        
        // 添加自定义方法
        methodList.add(new SelectByIdWithDeleted());
        methodList.add(new BatchInsertOrUpdate());
        
        return methodList;
    }
}
```

### 自定义数据权限处理

```java
@Component
public class CustomDataPermissionHandler implements DataPermissionHandler {
    
    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        // 根据当前用户权限生成SQL条件
        String currentUser = getCurrentUser();
        UserPermission permission = getUserPermission(currentUser);
        
        if (permission.isAdmin()) {
            return where; // 管理员不限制
        }
        
        // 普通用户只能查看自己的数据
        Expression userCondition = new EqualsTo(
            new Column("create_by"), 
            new StringValue(currentUser)
        );
        
        return where == null ? userCondition : new AndExpression(where, userCondition);
    }
}
```

## 📝 注意事项

1. **分页性能**：大数据量分页时建议使用游标分页或限制最大页数
2. **乐观锁**：使用乐观锁时需要在更新操作中包含version字段
3. **逻辑删除**：逻辑删除的数据仍占用存储空间，需要定期清理
4. **多租户**：启用多租户后所有表操作都会自动添加租户条件
5. **SQL监控**：生产环境建议关闭详细SQL日志以提高性能

## 🐛 故障排除

### 常见问题

1. **分页不生效**
   - 检查分页插件是否正确配置
   - 确认使用的是IPage类型的返回值
   - 验证SQL是否正确生成

2. **自动填充不工作**
   - 检查MetaObjectHandler是否注册为Bean
   - 确认字段上的@TableField注解配置正确
   - 验证fill属性设置

3. **逻辑删除不生效**
   - 检查全局配置中的逻辑删除字段设置
   - 确认实体类字段上有@TableLogic注解
   - 验证数据库字段类型匹配

### 调试建议

启用调试日志：

```yaml
logging:
  level:
    com.chua.starter.mybatis: DEBUG
    com.baomidou.mybatisplus: DEBUG
    org.apache.ibatis: DEBUG
```
