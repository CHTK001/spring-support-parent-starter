# spring-support-lowcode-starter

低代码表单管理模块：把「目录 → 表单 / 页面 → 版本发布」这套设计器资产做成**加依赖即生效**的独立 starter，自带数据模型、服务、Mapper、REST 接口与建表脚本。

- 三类资产：目录（树形）、表单（`low_form`）、页面（`low_page`）；后两者各自拥有版本快照表。
- 对外标识用**编码**（`lowFormCode` / `lowPageCode`），主键只做内部关联；运行期渲染只认已发布快照。
- 所有接口统一返回 `ReturnResult`（成功码 `00000`），分页接口返回 `ReturnPageResult`（`code=0`）。

> 当前版本：`4.0.0.51`（跟随父 POM `spring-support-parent-starter`）

## 一、模块能力

| 能力域 | 关键类 | 入口 |
| --- | --- | --- |
| 自动装配 | `com.chua.starter.lowcode.configuration.LowcodeAutoConfiguration` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| 访问前缀 | `LowFormDirectoryController` / `LowFormController` / `LowPageController` | `/v2/lowcode/directory`、`/v2/lowcode/form`、`/v2/lowcode/page` |
| 目录树 | `LowFormDirectoryService#tree()` | `GET /v2/lowcode/directory/tree` |
| 目录下拉 | `LowFormDirectoryService#options()` | `GET /v2/lowcode/directory/options` |
| 目录增改删 | `#addDirectory` / `#editDirectory` / `#deleteDirectory` | `POST /save`、`PUT /update`、`DELETE /delete` |
| 表单分页与详情 | `LowFormService#page` / `#detail` / `#detailByCode` | `GET /page`、`GET /detail`、`GET /code/{formCode}` |
| 表单增改删 | `LowFormService#saveForm` / `#updateForm` / `#deleteForm` | `POST /save`、`PUT /update`、`DELETE /delete` |
| 表单发布链 | `#publishForm` / `#rollbackToVersion` / `#changeStatus` / `#unpublish` / `#versions` | `POST /publish`、`/rollback`、`/status`、`/unpublish`，`GET /versions` |
| 运行期 Schema（表单） | `LowFormService#publishedSchema(String)` | `GET /v2/lowcode/form/api/{formCode}` |
| 页面 CRUD + 发布链 | `LowPageService`（方法名与表单一一对应，参数为 `pageId`） | `/v2/lowcode/page/**` |
| 运行期 Schema（页面） | `LowPageService#publishedSchema(String)` | `GET /v2/lowcode/page/api/{pageCode}` |
| 审计字段填充 | `com.chua.starter.lowcode.support.LowcodeMetaObjectHandler` | `LowcodeAutoConfiguration#lowcodeMetaObjectHandler`（`MetaObjectHandler` Bean） |
| 数据模型 | `LowFormDirectory` / `LowForm` / `LowFormVersion` / `LowFormPage` / `LowFormPageVersion` | 对应表 `low_form_directory` / `low_form` / `low_form_version` / `low_page` / `low_page_version` |
| 出参模型 | `LowFormVO` / `LowFormVersionVO` / `LowFormPageVO` / `LowFormPageVersionVO` / `LowFormDirectoryVO` / `LowFormOptionVO` | Controller 返回值 |
| 建表与种子 | `db/init/V1.0__init_lowcode.sql`、`db/init/V1.0__initdata_lowcode.sql` | 由 `spring-support-datasource-starter` 的脚本通道执行 |

### 接口清单：目录

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/lowcode/directory/tree` | 无 | 树形目录，含 `formCount` / `pageCount`（含子孙汇总） |
| GET | `/v2/lowcode/directory/options` | 无 | `LowFormOptionVO`（`value` / `label`）列表 |
| POST | `/v2/lowcode/directory/save` | Body `LowFormDirectory` | 新增，返回保存后的目录（含 `lowFormDirectoryTreeId`） |
| PUT | `/v2/lowcode/directory/update` | Body `LowFormDirectory` | 修改；`lowFormDirectoryPid` 不传表示沿用原父级 |
| DELETE | `/v2/lowcode/directory/delete` | `directoryId` | 有子目录 / 表单 / 页面时拒绝 |

### 接口清单：表单

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/lowcode/form/page` | `Query<LowForm>`（`page` / `pageSize`）+ `LowForm` 条件 | 分页，出参 `LowFormVO` |
| GET | `/v2/lowcode/form/detail` | `formId` | 详情 |
| GET | `/v2/lowcode/form/code/{formCode}` | 路径 `formCode` | 按编码回读（设计器用） |
| POST | `/v2/lowcode/form/save` | Body `LowForm` | 新增；主键由数据库生成 |
| PUT | `/v2/lowcode/form/update` | Body `LowForm` | 修改；`lowFormCode` / `lowFormName` 缺失时沿用库中原值 |
| DELETE | `/v2/lowcode/form/delete` | `formId` | 删除表单并清理其版本快照 |
| POST | `/v2/lowcode/form/publish` | `formId` | 生成版本快照、版本号按历史最大值 +1、置为已发布且启用 |
| POST | `/v2/lowcode/form/rollback` | `versionId` | 用指定版本快照恢复 Schema / 版本号 / 发布状态 |
| POST | `/v2/lowcode/form/status` | `formId`、`status`（`0` 停用 / `1` 启用） | 停用后运行期接口不再返回 Schema |
| POST | `/v2/lowcode/form/unpublish` | `formId` | 撤销发布；版本历史保留 |
| GET | `/v2/lowcode/form/versions` | `formId` | 版本列表，按版本号倒序 |
| GET | `/v2/lowcode/form/api/{formCode}` | 路径 `formCode` | 运行期取已发布 Schema（字符串），未发布/已停用返回 `null` |

### 接口清单：页面

`/v2/lowcode/page/**` 与表单完全同构，仅参数名不同：

| 方法 | 路径 | 参数 |
| --- | --- | --- |
| GET | `/page` | `Query<LowFormPage>` + `LowFormPage` 条件 |
| GET | `/detail` | `pageId` |
| GET | `/code/{pageCode}` | 路径 `pageCode` |
| POST | `/save` | Body `LowFormPage` |
| PUT | `/update` | Body `LowFormPage` |
| DELETE | `/delete` | `pageId` |
| POST | `/publish` | `pageId` |
| POST | `/rollback` | `versionId` |
| POST | `/status` | `pageId`、`status` |
| POST | `/unpublish` | `pageId` |
| GET | `/versions` | `pageId` |
| GET | `/api/{pageCode}` | 路径 `pageCode` |

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-lowcode-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 版本来源 | 作用 |
| --- | --- | --- |
| `spring-support-mybatis-starter` | `4.0.0.51` | MyBatis-Plus 集成；提供 `SysBase`（审计列基类）、`Query`、`PageResultUtils`、`IService` |
| `spring-support-datasource-starter` | `4.0.0.51` | 数据源与 `db/init` 脚本通道（`DataSourceScriptConfiguration`） |
| `spring-boot-starter-web` | 父 POM 管理 | `@RestController` / `@RequestMapping` |
| `utils-support-common-starter` | `4.0.0.50`（`${utils.version}`） | `ReturnResult`（成功码 `00000`） |
| `swagger-annotations` | 父 POM 管理 | `@Tag` / `@Operation` / `@Schema` |
| `mysql-connector-j` | 父 POM 管理 | `runtime`，生产库驱动 |
| `h2` | 父 POM 管理 | `runtime`，嵌入式库（模块自带建表脚本可免外部库启动） |

传递依赖说明：`AuthService` / `CurrentUser` / `ReturnPageResult` 来自 `spring-support-common-starter`，本模块 pom **未直接声明**，经 `spring-support-mybatis-starter` 传递进来。

## 三、配置项

`LowcodeProperties` 使用 `@ConfigurationProperties(prefix = "plugin.lowcode")`，且**只有一个 key**（代码注释明确「不接受只写不读的参数」）：

| key | 默认值 | 说明 |
| --- | --- | --- |
| `plugin.lowcode.enable` | `true` | 功能总开关。关闭后不装配本模块任何能力（`@ConditionalOnProperty` 与三个 Controller 共用同一开关），已保存的 Schema 保留在库里不动 |

本模块不拥有、但与运行直接相关的宿主配置（来自所依赖的 starter，非本模块定义）：

| key | 来源 | 与本模块的关系 |
| --- | --- | --- |
| `plugin.datasource.script.*` | `spring-support-datasource-starter` | 决定 `db/init/*.sql` 是否执行；默认 `scriptPath` 含 `classpath*:db/init/*.sql` |
| `plugin.mybatis.data.scope.enable` | `spring-support-mybatis-starter`（`MybatisPlusDataScopeProperties.PRE`） | 集成测试里设为 `false`，避免数据权限切面干扰 |
| `plugin.oauth.enable` | `spring-support-oauth-client-starter`（`AuthClientProperties.PRE`） | 决定 `AuthService` 是否由 OAuth 模块提供；集成测试里设为 `false`，改用 `StubAuthConfiguration` 的桩 |

脚本命名约定（由 `DataSourceScriptConfiguration#getScriptType` 解析，决定同步/异步）：

| 文件名 | 类型 | 执行方式 |
| --- | --- | --- |
| `V1.0__init_lowcode.sql` | `INIT` | 同步建表 |
| `V1.0__initdata_lowcode.sql` | `INITDATA` | 异步（虚拟线程）写入种子数据 |
| `V1.0__add_xxx.sql` | `ADD` | 同步增量 |
| `V1.0__xxx.sql` | `NORMAL` | 同步普通脚本 |

## 四、快速开始

**第 1 步：加依赖**，并确保宿主已有可用的数据源（或引入 `h2` 做嵌入式启动）。

**第 2 步：让建表脚本执行**。默认配置下 `spring-support-datasource-starter` 会扫描 `classpath*:db/init/*.sql`：

- `V1.0__init_lowcode.sql` 建 5 张表（`low_form_directory` / `low_form` / `low_form_version` / `low_page` / `low_page_version`）；
- `V1.0__initdata_lowcode.sql` 幂等写入根目录：`low_form_directory_id = 1`、名称「默认目录」。

若宿主关闭了脚本通道，需要手工执行这两个文件；否则新增表单时的默认目录 `1` 不存在（代码里 `ROOT_DIRECTORY_ID = 1` 是硬编码）。

**第 3 步：验证装配**

```bash
curl http://127.0.0.1:8080/v2/lowcode/directory/tree
# {"code":"00000","ok":true,"data":[{"lowFormDirectoryId":1,"lowFormDirectoryName":"默认目录",...}]}
```

## 五、运行示例

### 5.1 调用示例

**方式 A：Service 层（本模块无链式 API）**

```java
// 1) 新增表单：LowForm 是 lombok @Data，全部 setter 赋值
LowForm form = new LowForm();
form.setLowFormCode("order_form");        // 编码唯一，人工录入
form.setLowFormName("订单表单");
form.setLowFormSchema("{\"fields\":[]}"); // Schema 是 JSON 字符串
form.setLowFormDirectoryId(1);
LowForm saved = formService.saveForm(form);   // 返回带主键的对象

// 2) 发布：生成快照 + 版本号 +1 + 置为已发布且启用
formService.publishForm(saved.getLowFormId());

// 3) 运行期取数：只认已发布快照，未发布/已停用返回 null
String schema = formService.publishedSchema("order_form");

// 4) 版本管理与状态
List<LowFormVersionVO> versions = formService.versions(saved.getLowFormId());
Integer versionId = versions.get(0).getLowFormVersionId();
formService.rollbackToVersion(versionId);          // 回滚到该快照
formService.changeStatus(saved.getLowFormId(), 0); // 0 停用 / 1 启用
formService.unpublish(saved.getLowFormId());       // 撤销发布
formService.deleteForm(saved.getLowFormId());      // 连带删除版本快照
```

页面侧完全对称：`pageService.savePage(LowFormPage)`、`publishPage(Integer)`、`rollbackToVersion(Integer)`、`changeStatus(Integer, Integer)`、`unpublish(Integer)`、`versions(Integer)`、`publishedSchema(String)`。

目录服务：

```java
List<LowFormDirectoryVO> tree = directoryService.tree();       // 带 children / formCount / pageCount
List<LowFormOptionVO> options = directoryService.options();    // value=目录ID字符串, label=名称
LowFormDirectory dir = directoryService.addDirectory(new LowFormDirectory());
directoryService.editDirectory(dir);                            // 改父级会级联刷新 treeId
directoryService.deleteDirectory(dir.getLowFormDirectoryId());  // 有子目录/表单/页面时抛异常
```

**方式 B：HTTP**（参数名与 Controller 签名一致）

```bash
# 新增表单
curl -X POST http://127.0.0.1:8080/v2/lowcode/form/save \
     -H "Content-Type: application/json" \
     -d '{"lowFormCode":"order_form","lowFormName":"订单表单","lowFormSchema":"{\"fields\":[]}"}'

# 发布（formId 取新增返回值）
curl -X POST "http://127.0.0.1:8080/v2/lowcode/form/publish?formId=1"

# 运行期取已发布 Schema
curl "http://127.0.0.1:8080/v2/lowcode/form/api/order_form"

# 停用后立即取不到
curl -X POST "http://127.0.0.1:8080/v2/lowcode/form/status?formId=1&status=0"

# 分页（响应 code 为数值 0，列表在 data.data）
curl "http://127.0.0.1:8080/v2/lowcode/form/page?page=1&pageSize=10"
```

**关于链式 API**：本模块无链式 API。实体是 lombok `@Data`（setter 无返回值），Service 方法返回实体 / `boolean` / `String` / `List` / `Page`，没有 Builder，也没有 `return this`。代码里出现的 `Wrappers.<LowForm>lambdaQuery().eq(...).orderByDesc(...)` 属于 MyBatis-Plus 框架的链式写法，由 `IService` 提供，不是本模块定义的 API。

### 5.2 SPI 扩展示例

**本模块不提供 SPI 扩展点**：没有 `META-INF/services/*`，没有 `META-INF/extensions/*`，也没有自定义 SPI 接口。

可以替换的是两个 **Spring Bean 级** 接缝：

| 接缝 | 机制 | 替换方式 |
| --- | --- | --- |
| 审计字段填充 | `LowcodeAutoConfiguration#lowcodeMetaObjectHandler` 上的 `@ConditionalOnMissingBean(MetaObjectHandler.class)` | 宿主自己注册一个 `MetaObjectHandler` Bean，本模块的 `LowcodeMetaObjectHandler` 自动让位 |
| 当前登录用户 | `LowcodeMetaObjectHandler` 构造参数是 `ObjectProvider<AuthService>` | 提供自己的 `AuthService` 实现即可，取不到用户时只填 `createTime` / `updateTime`，不填人名 |

```java
// 示例：宿主的自定义审计填充（注册后本模块的实现不再装配）
@Bean
public MetaObjectHandler metaObjectHandler() {
    return new MetaObjectHandler() {
        @Override
        public void insertFill(org.apache.ibatis.reflection.MetaObject metaObject) {
            strictInsertFill(metaObject, "createTime", java.time.LocalDateTime.class,
                    java.time.LocalDateTime.now());
        }

        @Override
        public void updateFill(org.apache.ibatis.reflection.MetaObject metaObject) {
            strictUpdateFill(metaObject, "updateTime", java.time.LocalDateTime.class,
                    java.time.LocalDateTime.now());
        }
    };
}
```

`LowcodeMetaObjectHandler` 会填的字段（取决于实体是否有对应 setter）是：`createTime` / `updateTime` / `createName` / `createBy` / `updateName` / `updateBy`；其中 `createBy` / `updateBy` 只在 `CurrentUser#getUserId()` 能解析为整数时填，否则留空。

## 六、与其它 starter 的关系

| 关系 | artifactId | 方向与说明 |
| --- | --- | --- |
| 依赖 | `spring-support-mybatis-starter` | 本模块 → 数据访问：`SysBase`（审计列）、`Query` / `createPage()`、`PageResultUtils#ok(IPage)`、`IService` / `ServiceImpl` |
| 依赖 | `spring-support-datasource-starter` | 本模块 → 数据源与脚本通道：执行 `db/init/V1.0__init_lowcode.sql`（同步）与 `V1.0__initdata_lowcode.sql`（异步） |
| 依赖（传递） | `spring-support-common-starter` | `AuthService` / `CurrentUser`（审计填充）、`ReturnPageResult`（分页响应） |
| 依赖 | `utils-support-common-starter`（`4.0.0.50`） | `ReturnResult`（统一响应，成功码 `00000`） |
| 依赖 | `spring-boot-starter-web` | REST 层；三个 Controller 都是 `@RestController` |
| 无耦合 | `spring-support-apm-*` | 与 APM 系列无依赖关系，互不影响 |

## 七、已知限制

1. **详情接口不返回 Schema 正文**：`LowFormVO` / `LowFormPageVO` 里没有 `schema` 字段（Service 注释写「详情包含 Schema 正文」，与出参类不符 —— 以代码为准）。能拿到 Schema 的只有两处：`GET /api/{code}` 的**已发布**快照（`String`），以及 `GET /versions` 返回的 `LowFormVersionVO#getLowFormVersionSchema` / `LowFormPageVersionVO`（历史快照）。
2. **发布必须有 Schema**：`requirePublishable` 判空，表单抛 `没有表单 Schema 不能发布`，页面抛 `没有页面 Schema 不能发布`。
3. **编码唯一是强约束**：库上有 `low_form_uk_code` / `low_page_uk_code` 唯一索引，Service 里还有 `requireCode` 查重（撞了报 `该表单编码已存在`）；新增时传入的 `lowFormId` 会被强制置空（`form.setLowFormId(null)`）。
4. **删除表单会连带删版本**：`deleteForm` 先删 `low_form_version` 再删主记录，历史快照不保留；页面同理。
5. **目录删除有前置条件**：存在子目录 → `请先删除子目录`；存在表单/页面 → `该目录下还有 N 个表单/页面，请先移走或删除`。
6. **目录搬迁依赖路径链**：`editDirectory` 改父级后通过 `cascadeTreeId` 用 `likeRight` 批量改子孙的 `low_form_directory_tree_id`，脏数据（`tree_id` 为空）会导致层级判定失效。
7. **运行期取数三重条件**：`publishStatus=1` + `status=1` + 优先取「当前版本号对应的快照」；设计器改了已发布表单但没重新发布时，草稿不会透给运行期。
8. **两种响应结构并存**：普通接口 `code` 是字符串 `"00000"`；分页接口 `ReturnPageResult` 的 `code` 是数值 `0`，列表在 `data.data`。前端需要分别处理。
9. **Schema 不做校验**：`lowFormSchema` / `lowPageSchema` 是 `longtext`，后端只判空，不校验 JSON 合法性。
10. **种子数据是异步写入**：`V1.0__initdata_lowcode.sql` 属于 `INITDATA` 通道（虚拟线程异步执行），极端情况下第一个请求可能早于根目录写入；而代码里默认目录硬编码为 `1`。
11. **审计人名依赖用户上下文**：`AuthService` 不可用或 `userId` 非整数时，`createBy` / `updateBy` / `createName` / `updateName` 都不填，只有时间列有值。
12. **关闭开关不删数据**：`plugin.lowcode.enable=false` 只是不装配能力（Controller 也随之失效），表与 Schema 原样保留。
13. **版本号只增不减**：发布时取历史最大版本号 +1（不用当前记录 +1），回滚后再次发布会继续用更大号，不会复用旧号。
