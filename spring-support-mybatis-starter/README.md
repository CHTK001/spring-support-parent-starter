# spring-support-mybatis-starter

MyBatis-Plus 增强装配：分页/乐观锁/逻辑删除、`@DataScope` 数据权限、只读模式、慢 SQL 事件、XML 热重载与自定义 SQL 注入。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| MyBatis-Plus 装配 | `MybatisPlusConfiguration`（`SqlSessionFactory`、`SqlSessionTemplate`、`GlobalConfig`） | 自动配置 |
| 分页 | `PaginationInnerInterceptor` | 自动注册，`IPage` 返回 |
| 乐观锁 | `OptimisticLockerInnerInterceptor` | `@Version` |
| 逻辑删除 | `GlobalConfig`（固定 `sys_deleted` / `1` / `0`） | `@TableLogic` |
| 自动填充 | 容器中的 `MetaObjectHandler` | `@TableField(fill = ...)` |
| 数据权限 | `DataScope`、`MybatisPlusDataPermissionInterceptor`、`MybatisPlusDataPermissionHandler` | `plugin.mybatis.data.scope` |
| 只读模式 | `ReadOnlyInterceptor`、`ReadOnlyException` | `plugin.mybatis.read-only` |
| 删除保护 | `DeleteOnlySelfInterceptor`、`DeleteOnlySelfOnMapper` | 注解（当前不命中，见已知限制） |
| 字段加解密 | `CryptoInterceptor`、`EncryptTypeHandler` | `mybatis-plus.encryptor.*` |
| 慢 SQL 检测 | `SlowSqlDetectInterceptor`、`MybatisSlowSqlEvent` | `plugin.mybatis.slow-sql` |
| 多数据源路由 | `MapperDataSourceInterceptor` | 与 `spring-support-datasource-starter` 协作 |
| SQL 物理化 | `MaterializedSqlInterceptor` | `plugin.datasource.materialized.enabled` |
| XML 热重载 | `Reload`、`MapperReload`、`MapperXmlEndpoint` | `plugin.mybatis.open-xml-reload` |
| 自定义 SQL 注入 | `SupportInjector`、`SqlMethodProperties` | `plugin.mybatis.sql-method` |
| 关联查询 | `BaseMapperX`、`MPJLambdaWrapperX`、`mybatis-plus-join` | 代码调用 |
| 类型处理器 | `IntegerListTypeHandler`、`LongListTypeHandler`、`StringListTypeHandler`、`JsonLongSetTypeHandler` | 实体字段 `@TableField(typeHandler=...)` |
| 分页结果转换 | `PageResult`、`PageResultUtils`、`ReturnPageResultUtils`、`MybatisPageUtils` | 代码调用 |
| 操作日志切面 | `SysLoggerPointcutAdvisor` | `@SysLog` |
| Actuator 端点 | `MybatisEndpoint`（`mybatis-extension`）；`MapperXmlEndpoint`（`mybatis-xml`，未在自动配置注册） | 需 Actuator + `mapper-reload` |

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-mybatis-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 说明 |
|---|---|---|
| `mybatis-plus-spring-boot4-starter` | MyBatis-Plus 核心 | Boot 4 专用坐标 |
| `mybatis-plus-jsqlparser` | SQL 解析（分页、数据权限、物理化） | 必需 |
| `mybatis-plus-join-boot-starter` | 多表关联查询 | 必需 |
| `spring-support-common-starter` | `AuthService`、`DataFilterTypeEnum`、`SysLoggerInfo` | 必需 |
| `p6spy` | SQL 打印 | 必需 |
| `spring-support-datasource-starter` | `MultiDataSourceSettingProperties`、`MaterializedRouteProperties` | `optional` |
| `mybatis-plus-generator` + `spring-boot-starter-freemarker` | 代码生成 | `provided` |
| `spring-support-swagger-starter` | 文档注解 | `provided` |

## 三、配置项

| 前缀 | 配置类 | 关键项（默认值） |
|---|---|---|
| `plugin.mybatis` | `MybatisPlusProperties` | `enable`=`true`（当前无消费方）；`open-xml-reload`=`true`（字段默认 true，但 Bean 条件 `matchIfMissing=false`，**不显式配置不生效**）；`read-only`=`false`；`reload-type`=`AUTO`（仅 `NONE`/`AUTO`）；`reload-time`=`3000`（毫秒）；`reload-directories[].path/pattern/watch-enabled`（`pattern` 默认 `**/*Mapper.xml`）；`sql-method[].type`（`FILE`/`MYSQL`）、`source`、`timeout`=`3000`、`watchdog` |
| `plugin.mybatis.data.scope` | `MybatisPlusDataScopeProperties` | `enable`=`false`；`table-name`=`sys_dept`；`dept-id-column`=`sys_dept_id`；`dept-tree-id-column`=`sys_dept_tree_id`；`current-user-id-column`=`create_by` |
| `plugin.mybatis.slow-sql` | `MybatisSlowSqlProperties` | `enabled`=`true`；`threshold-ms`=`500`；`print`=`true`；`include-sql`=`true`；`include-parameters`=`false`；`max-sql-length`=`2000` |
| `mybatis-plus.*` | MyBatis-Plus 原生 | `configuration.*`、`global-config.db-config.*`、`mapper-locations`、`encryptor.password`、`encryptor.mode` |
| `plugin.datasource.materialized` | `MaterializedRouteProperties`（datasource-starter） | `enabled`，启用后本模块注册 `MaterializedSqlInterceptor` |

注意数据权限前缀是 `plugin.mybatis.data.scope`（**不是** `plugin.mybatis-plus.data-scope`）。

## 四、快速开始

**第一步**：引入依赖，保留宿主的 `spring.datasource` 与 `mybatis-plus.mapper-locations`。本模块自动提供 `SqlSessionFactory` / `SqlSessionTemplate`，无需手写配置类。

**第二步**：按需开启数据权限与慢 SQL：

```yaml
plugin:
  mybatis:
    read-only: false
    open-xml-reload: true          # 必须显式写 true 才会注册 Reload
    data:
      scope:
        enable: true
        table-name: sys_dept
        dept-id-column: sys_dept_id
        dept-tree-id-column: sys_dept_tree_id
        current-user-id-column: create_by
    slow-sql:
      enabled: true
      threshold-ms: 500
```

**第三步**：实体继承 `SysBase` / `SysTenantBase`，Mapper 继承 `BaseMapperX<T>`，在需要过滤的方法上加 `@DataScope`。

## 五、运行示例

### 5.1 调用示例

本模块无链式 API，以下为注解 + 步骤式用法。

**实体与 Mapper**：

```java
@Data
@TableName("sys_user")
public class User extends SysBase {          // SysBase 已带 create_name/create_by/create_time/update_*

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @TableLogic
    private Integer sysDeleted;              // 与 GlobalConfig 的 sys_deleted 对应

    @Version
    private Integer version;
}

@Mapper
public interface UserMapper extends BaseMapperX<User> {

    @DataScope                                   // 按当前登录用户的数据权限过滤
    List<User> selectUserList();

    @DataScope(value = DataFilterTypeEnum.DEPT, useUserPermission = false)
    List<User> selectDeptUserList();

    @DataScope(enabled = false)                  // 管理员旁路
    List<User> selectAllUserList();
}
```

**数据权限类型**（`com.chua.starter.common.support.constant.DataFilterTypeEnum`）：

| 枚举 | 语义 | 生成条件 |
|---|---|---|
| `ALL` | 全部可见 | 不加条件 |
| `SELF` | 本人可见 | `create_by = 当前用户` |
| `DEPT` | 所在部门 | `sys_dept_id = 当前部门` |
| `DEPT_AND_SUB` | 本部门及子级 | `sys_dept_tree_id LIKE '当前树ID%'`（可用索引） |
| `DEPT_SETS` | 指定部门集合 | `sys_dept_id IN (...)` |
| `CUSTOM` | 自定义 | 本人 + 指定部门（结合 `CurrentUser.dataPermissionRule`） |

**多表别名**：

```java
@DataScope(value = DataFilterTypeEnum.DEPT_AND_SUB, deptAlias = "d", userAlias = "u")
@Select("SELECT u.*, d.dept_name FROM sys_user u LEFT JOIN sys_dept d ON u.sys_dept_id = d.sys_dept_id")
List<UserVO> selectUserWithDept();
```

**用户上下文**：数据权限依赖 `AuthService`，Bean 名为 `authService`（或 `defaultAuthService`）。

```java
@Component("authService")
public class OauthAuthService implements AuthService {
    @Override
    public CurrentUser getCurrentUser() {
        // 从会话/令牌解析；CurrentUser 需带 userId、deptId、dataPermission(、roles)
        return requestContext.getCurrentUser();
    }
}
```

`MybatisPlusDataPermissionHandler` 对 `SUPER_ADMIN` / `ADMIN` 角色直接旁路，不拼条件。

**分页与结果转换**：

```java
public PageResult<User> page(Query<User> query) {
    return userMapper.selectPage(query, Wrappers.<User>lambdaQuery().eq(User::getStatus, 1));
}

public PageResult<UserVO> joinPage(Query<User> query) {
    return userMapper.selectJoinPage(query, UserVO.class,
            new MPJLambdaWrapperX<User>().selectAll(User.class).selectAs(Dept::getDeptName, UserVO::getDeptName));
}
```

**只读模式**（`plugin.mybatis.read-only=true`）：`ReadOnlyInterceptor` 拦截 `INSERT`/`UPDATE`/`DELETE` 并抛 `ReadOnlyException`，适合只读副本或演示环境。

**删除保护**：`DeleteOnlySelfInterceptor` 的设计意图是给 `DELETE` 语句追加 `AND create_by = 当前用户`（列名与参数名可由 `DeleteOnlySelfOnMapper` 的 `createUserColumn` / `currentUserParam` 指定）。该注解声明为 `@Target(TYPE)`，而拦截器从 Mapper 方法上读取，当前版本实际不会命中，启用前请自行验证（见「已知限制」）。

**慢 SQL 事件**：

```java
@Component
public class SlowSqlAlert {
    @EventListener
    public void onSlow(MybatisSlowSqlEvent event) {
        log.warn("慢 SQL {}ms, statement={}: {}", event.getElapsedMs(), event.getStatementId(), event.getSql());
    }
}
```

**XML 热重载**（开发环境）：

```yaml
plugin:
  mybatis:
    open-xml-reload: true
    reload-type: AUTO          # 仅 NONE / AUTO
    reload-time: 3000
    reload-directories:
      - path: "classpath:mapper"
        pattern: "*.xml"
        watch-enabled: true
      - path: "D:/work/mapper"
        pattern: "**/*Mapper.xml"
        watch-enabled: true
```

注册 Bean 名为 `mapper-reload` 的 `Reload` 后，`MybatisEndpoint`（`/actuator/mybatis-extension`）提供按文件名/方法名触发重载与信息查询；`MapperXmlEndpoint`（`mybatis-xml`）只有 `@WebEndpoint` 注解、未在自动配置中登记 Bean，需要时自行声明。

**自定义 SQL 方法注入**：

```yaml
plugin:
  mybatis:
    sql-method:
      - type: FILE
        source: classpath:sql/find-all.sql
        watchdog: false
        timeout: 3000
```

`SupportInjector` 会把配置的方法注入到每个 Mapper（`FindAllSqlMethod` / `DynamicSqlMethod`）。

### 5.2 SPI 扩展示例

本模块不提供 SPI 扩展点（`src/main/resources/META-INF` 下只有 `spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，无 `extensions` / `services` 目录）。

可扩展点是 Spring Bean 覆盖（均为 `@ConditionalOnMissingBean`）：

| 扩展点 | 覆盖方式 |
|---|---|
| 数据权限处理器 | 声明 `MybatisPlusDataPermissionHandler` Bean 替换默认实现 |
| 自动填充 | 声明 `MetaObjectHandler` Bean，装配进 `GlobalConfig` |
| 内部拦截器 | 声明任意 `InnerInterceptor` Bean，会被收集进 `MybatisPlusInterceptor`（分页固定排最后） |
| XML 重载 | 声明名为 `mapper-reload` 的 `Reload` Bean |
| SQL 注入 | 声明 `supportInjector` / 自定义 `DefaultSqlInjector` |

示例（自定义数据权限）：

```java
@Component
public class TenantDataPermissionHandler extends MybatisPlusDataPermissionHandler {

    public TenantDataPermissionHandler(MybatisPlusDataScopeProperties props,
                                       Supplier<AuthService> authService) {
        super(props, authService);
    }

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        // 返回 null 表示该表不追加条件
        return super.getSqlSegment(table, where, mappedStatementId);
    }
}
```

生效条件：Bean 在 `MybatisPlusConfiguration#dataPermissionInterceptor` 执行前注册，且 `plugin.mybatis.data.scope.enable=true`。

## 六、与其它 starter 的关系

- **`spring-support-common-starter`**：`AuthService` / `CurrentUser`（数据权限取当前用户）、`DataFilterTypeEnum`（权限枚举）、`SysLoggerInfo`（操作日志事件）。`CurrentUser` 的 `userId`、`deptId`、`dataPermission`、`roles` 为空时数据权限无法生效。
- **`spring-support-datasource-starter`**：本模块 `optional` 依赖它。`MapperDataSourceInterceptor` 读取 `MultiDataSourceSettingProperties` 做数据源路由；`@MaterializedRoute` 的 SQL 物理化由 `MaterializedSqlInterceptor` 接入，需 `plugin.datasource.materialized.enabled=true`。
- **`spring-support-swagger-starter`**：`provided`，仅用于 `AbstractSwagger*Controller` 的文档注解。
- **`spring-support-lowcode-starter` / `spring-support-payment-starter`**：各自实现 `MetaObjectHandler`，可与本模块共存（容器内只取第一个 `MetaObjectHandler`）。
- **`spring-support-datasource-starter` 的 `@DS`**：与本模块共存时，方法级 `@DS` 先切换数据源，再由 MyBatis 执行；两者都依赖 Spring AOP 代理。

## 七、已知限制

1. `plugin.mybatis.enable` 字段存在但无消费方，写 `false` 不会关闭模块。
2. `open-xml-reload` 的属性默认值是 `true`，但注册 `Reload` 的 `@ConditionalOnProperty(matchIfMissing = false)` 要求显式配置 `open-xml-reload: true`，否则热重载与 `mybatis-extension` 端点都不出现（该端点本身还要求类路径有 Actuator）。
3. `reload-type` 只有 `NONE` / `AUTO` 两个值，没有 `MANUAL`；手动重载走 `MapperXmlEndpoint` 的写操作。
4. 热重载只能监听本地文件系统，jar 包内 `classpath:` 资源无法监听；生产环境应关闭。
5. 逻辑删除字段被硬编码为 `sys_deleted`（值 `1`/`0`），`GlobalConfig` 由本模块创建；实体若用其它字段名需自行覆盖 `GlobalConfig` Bean。
6. 数据权限只对包含 `dept-id-column` 与 `current-user-id-column` 的表生效；无 `AuthService` Bean、`CurrentUser` 为空、或 `data.scope.enable=false` 时静默不加条件。
7. 多表查询必须显式给 `deptAlias` / `userAlias`，否则可能生成歧义 SQL；子查询与 UNION 场景未做别名推断。
8. `@DataScope` 解析结果按方法缓存，修改注解后需重启。
9. `CryptoInterceptor` 依赖 `mybatis-plus.encryptor.password` / `.mode`，并在构造 `MybatisPlusConfiguration` 时同步到系统属性，密码变更需重启。
10. `sqlSessionFactory` 由本模块 `@ConditionalOnMissingBean` 创建，若宿主自定义 `SqlSessionFactory`，本模块的全局配置、拦截器与 `SupportInjector` 都不会装配。
11. 运行环境：Java 25、Spring Boot 4.1.x（`mybatis-plus-spring-boot4-starter`），MyBatis-Plus 3 的 Boot 3 坐标不在支持范围。
12. `@DeleteOnlySelfOnMapper` 声明为 `@Target(TYPE)`，但 `DeleteOnlySelfInterceptor` 通过 `mapperMethod.getAnnotation(...)` 从方法上读取，实际永不命中；要启用该能力需把注解改成方法级或覆盖拦截器。
