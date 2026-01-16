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
- 数据权限控制（支持声明式注解，多种权限类型，多表关联场景）
- 多租户数据隔离
- 逻辑删除支持

### 📊 监控和审计
- SQL执行监控
- 性能分析
- 操作审计日志
- Mapper XML热重载（支持文件监听自动重载）

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
      table-name: "sys_dept"           # 部门表名
      dept-id-column: "sys_dept_id"    # 部门ID字段名
      dept-tree-id-column: "sys_dept_tree_id"  # 部门树ID字段名
      current-user-id-column: "create_by"      # 当前用户ID字段名
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

数据权限功能提供了声明式的数据访问控制，支持基于部门、用户的数据过滤，确保用户只能访问其权限范围内的数据。

#### 功能特性

- **声明式控制**：通过 `@DataScope` 注解轻松控制数据权限
- **多种权限类型**：支持全部可见、本人可见、部门可见、部门及子级可见、指定部门可见、自定义权限
- **多表关联支持**：支持通过 `deptAlias` 和 `userAlias` 指定表别名，适用于复杂多表查询
- **性能优化**：使用 LIKE 前缀查询替代 `find_in_set`，可以利用数据库索引提升性能
- **灵活配置**：支持方法级别和类级别的注解，可灵活控制每个方法的数据权限

#### 启用数据权限

```yaml
plugin:
  mybatis-plus:
    data-scope:
      enable: true                    # 是否启用数据权限
      table-name: "sys_dept"          # 部门表名
      dept-id-column: "sys_dept_id"   # 部门ID字段名
      dept-tree-id-column: "sys_dept_tree_id"  # 部门树ID字段名（用于部门及子级查询）
      current-user-id-column: "create_by"      # 当前用户ID字段名
```

#### 数据权限类型

| 类型 | 枚举值 | 说明 | SQL示例 |
|------|--------|------|---------|
| 全部可见 | `ALL` | 不进行数据权限过滤 | 无限制条件 |
| 本人可见 | `SELF` | 只能查看自己创建的数据 | `WHERE create_by = 'currentUserId'` |
| 所在部门可见 | `DEPT` | 只能查看所在部门的数据 | `WHERE sys_dept_id = currentDeptId` |
| 所在部门及子级可见 | `DEPT_AND_SUB` | 可查看所在部门及所有子部门的数据 | `WHERE sys_dept_id IN (SELECT sys_dept_id FROM sys_dept WHERE sys_dept_tree_id LIKE 'currentDeptTreeId%')` |
| 选择的部门可见 | `DEPT_SETS` | 可查看指定部门列表的数据 | `WHERE sys_dept_id IN (deptId1, deptId2, ...)` |
| 自定义 | `CUSTOM` | 本人数据 + 指定部门数据 | `WHERE (create_by = 'currentUserId' OR sys_dept_id IN (...))` |

#### 使用数据权限注解

**基础用法**

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 使用用户当前的数据权限（默认）
     * 会根据用户登录时的数据权限类型自动过滤
     */
    @DataScope
    List<User> selectUserList();
    
    /**
     * 强制使用指定的数据权限类型
     */
    @DataScope(value = DataFilterTypeEnum.DEPT, useUserPermission = false)
    List<User> selectDeptUserList();
    
    /**
     * 禁用数据权限（管理员查询所有数据）
     */
    @DataScope(enabled = false)
    List<User> selectAllUserList();
}
```

**多表关联场景**

当查询涉及多表关联时，可以通过 `deptAlias` 和 `userAlias` 指定表别名：

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 多表关联查询示例
     * 假设查询语句为：
     * SELECT u.*, d.dept_name 
     * FROM sys_user u 
     * LEFT JOIN sys_dept d ON u.sys_dept_id = d.sys_dept_id
     * 
     * 通过 deptAlias="d" 指定部门表别名，userAlias="u" 指定用户表别名
     */
    @DataScope(
        value = DataFilterTypeEnum.DEPT_AND_SUB,
        deptAlias = "d",  // 部门表别名
        userAlias = "u"   // 用户表别名
    )
    @Select("SELECT u.*, d.dept_name FROM sys_user u " +
            "LEFT JOIN sys_dept d ON u.sys_dept_id = d.sys_dept_id")
    List<UserVO> selectUserWithDept();
}
```

**类级别注解**

可以在 Mapper 接口上使用 `@DataScope` 注解，作用于该接口的所有方法：

```java
@DataScope(value = DataFilterTypeEnum.DEPT)
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 该接口的所有方法默认使用部门权限
    List<User> selectUserList();
    
    // 方法级别注解会覆盖类级别注解
    @DataScope(value = DataFilterTypeEnum.SELF)
    List<User> selectMyUserList();
}
```

**Service 层使用**

```java
@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    
    /**
     * Service 层方法会自动应用 Mapper 方法上的 @DataScope 注解
     */
    public List<User> getUsersByDataScope() {
        // 会自动应用 UserMapper.selectUserList() 上的数据权限
        return baseMapper.selectUserList();
    }
}
```

#### 性能优化说明

数据权限功能针对性能进行了优化：

1. **LIKE 前缀查询**：`DEPT_AND_SUB` 类型使用 `LIKE 'treeId%'` 替代 `find_in_set`，可以利用索引
2. **注解缓存**：`@DataScope` 注解信息会被缓存，避免重复解析
3. **条件检查**：只有表包含部门ID字段时才应用数据权限，减少不必要的处理

#### 配置属性说明

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enable` | boolean | `false` | 是否启用数据权限功能 |
| `table-name` | String | `"sys_dept"` | 部门表名 |
| `dept-id-column` | String | `"sys_dept_id"` | 部门ID字段名 |
| `dept-tree-id-column` | String | `"sys_dept_tree_id"` | 部门树ID字段名，用于部门及子级查询 |
| `current-user-id-column` | String | `"create_by"` | 当前用户ID字段名，用于本人可见权限 |

#### 注意事项

1. **表字段要求**：数据权限只对包含 `dept-id-column` 字段的表生效
2. **用户上下文**：需要实现 `AuthService` 接口提供当前用户信息
3. **权限数据**：用户对象需要包含 `dataPermission`、`deptId`、`userId` 等字段
4. **多表查询**：多表关联时必须正确指定 `deptAlias` 和 `userAlias`，否则可能生成错误的SQL

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

### 7. Mapper XML热重载

#### 功能说明

Mapper XML热重载功能支持在开发环境中监听Mapper XML文件的变化，自动重新加载到MyBatis配置中，无需重启应用。

#### 配置说明

```yaml
plugin:
  mybatis-plus:
    # 启用XML热重载
    open-xml-reload: true
    # 重载类型：AUTO（自动监听）、MANUAL（手动触发）
    reload-type: AUTO
    # 监听轮询间隔（毫秒）
    reload-time: 1000
    # 配置监听目录
    reload-directories:
      - path: "classpath:mapper"           # classpath路径
        pattern: "*.xml"                    # 文件匹配模式
        watch-enabled: true                 # 是否启用监听
      - path: "/path/to/mapper"             # 本地文件系统路径
        pattern: "**/*Mapper.xml"           # glob模式
        watch-enabled: true
```

#### 使用方式

**自动监听模式（AUTO）**：
- 启动时自动扫描并加载所有Mapper XML文件
- 自动监听文件系统中的XML文件变化
- 文件修改后自动触发重载

**手动触发模式（MANUAL）**：
- 通过API接口手动触发重载
- 支持按文件名或路径重载

```java
@RestController
@RequestMapping("/mapper")
public class MapperReloadController {
    
    @Autowired
    private Reload mapperReload;
    
    /**
     * 手动重载指定Mapper文件
     */
    @PostMapping("/reload")
    public String reloadMapper(@RequestParam String mapperXml) {
        return mapperReload.reload(mapperXml);
    }
    
    /**
     * 列出所有已加载的Mapper文件
     */
    @GetMapping("/list")
    public List<FileInfo> listMappers() {
        return mapperReload.listFiles();
    }
}
```

#### 注意事项

1. **文件系统限制**：只能监听本地文件系统中的文件，jar包内的资源无法监听
2. **开发环境使用**：建议仅在开发环境启用，生产环境应关闭
3. **性能影响**：监听会占用一定系统资源，建议合理设置轮询间隔
4. **文件路径**：支持classpath和本地文件系统路径，不支持jar包内路径

### 8. SQL监控

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
      table-name: "sys_dept"
      dept-id-column: "sys_dept_id"
      dept-tree-id-column: "sys_dept_tree_id"
      current-user-id-column: "create_by"
      
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

如果需要自定义数据权限处理逻辑，可以实现 `MultiDataPermissionHandler` 接口：

```java
@Component
public class CustomDataPermissionHandler implements MultiDataPermissionHandler {
    
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        // 根据当前用户权限生成SQL条件
        AuthService authService = SpringBeanUtils.getBean(AuthService.class);
        CurrentUser currentUser = authService.getCurrentUser();
        
        if (currentUser == null || currentUser.isAdmin()) {
            return null; // 管理员或未登录用户不限制
        }
        
        // 自定义权限逻辑
        DataFilterTypeEnum dataPermission = currentUser.getDataPermission();
        if (dataPermission == DataFilterTypeEnum.SELF) {
            EqualsTo userCondition = new EqualsTo();
            userCondition.setLeftExpression(new Column(table, "create_by"));
            userCondition.setRightExpression(new StringValue(currentUser.getUserId()));
            return where == null ? userCondition : new AndExpression(where, userCondition);
        }
        
        return null;
    }
}
```

然后在配置类中注册自定义处理器：

```java
@Configuration
public class MybatisPlusConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public DataPermissionInterceptor dataPermissionInterceptor(
            @Autowired(required = false) MultiDataPermissionHandler dataPermissionHandler,
            MybatisPlusDataScopeProperties dataScopeProperties) {
        if (dataPermissionHandler == null) {
            dataPermissionHandler = new MybatisPlusDataPermissionHandler(dataScopeProperties);
        }
        return new MybatisPlusDataPermissionInterceptor(dataPermissionHandler, dataScopeProperties);
    }
}
```

## 📝 注意事项

1. **分页性能**：大数据量分页时建议使用游标分页或限制最大页数
2. **乐观锁**：使用乐观锁时需要在更新操作中包含version字段
3. **逻辑删除**：逻辑删除的数据仍占用存储空间，需要定期清理
4. **数据权限**：
   - 数据权限只对包含部门ID字段的表生效
   - 多表关联查询时必须正确指定 `deptAlias` 和 `userAlias`
   - 确保用户对象包含完整的数据权限信息（`dataPermission`、`deptId`、`userId` 等）
5. **SQL监控**：生产环境建议关闭详细SQL日志以提高性能
6. **注解缓存**：`@DataScope` 注解信息会被缓存，修改注解后需要重启应用生效

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
