---
name: example-p3c-review
description: 对所有模块代码执行 P3C 规约审查（测试、开发、示例均需遵守），验证 example-starter 体系的命名与结构规范，扫描其他模块是否存在示例代码越界或演示依赖泄漏。
---

# Example P3C Review

本 skill 中的 P3C 规约**适用于所有场景**（测试代码、开发代码、示例代码），**不管测试还是开发都必须遵守**。既审查 `utils-support-example-starter` 模块的示例代码，也覆盖其他所有模块的生产代码。

## When to Use This Skill

以下场景均需使用本 skill 进行审查：

**测试/示例代码（example-starter）：**
- 用户要求审查 example-starter 中的某个 Java 文件
- 用户要求检查 example-starter 代码的 P3C 合规性
- 用户要求批量扫描 example-starter 的违规情况
- 用户要求确认某个 Example 文件的命名是否符合体系规范
- 用户要求检查 Example 的 main 自检是否真实（是否用假实现、吞异常后仍 [PASS]，见规约四十）

**开发/生产代码（所有模块）：**
- 用户要求审查 utils-support-*-starter 或 spring-support-*-starter 中的生产代码
- 用户要求检查生产代码是否复用了 utils-support-common-starter / spring-support-common-starter 的工具类
- 用户要求扫描生产代码中的 P3C 违规（命名风格、并发处理、异常控制流、参数校验等）
- 用户要求验证模块边界合规性（是否混入示例类、是否引入演示依赖）
- 用户要求检查代码是否使用了过程式编程而非面向对象（见规约三十三）
- 用户要求检查 BigDecimal 精确计算、ThreadLocal 清理、常量接口等规约合规性
- 用户要求检查 iBatis/MyBatis 复杂 SQL 是否违规使用 Lambda/Provider 内联编写（见规约三十九）
- 用户要求检查测试（JUnit / main / 前后端对接）是否使用 mock，测试必须真实（见规约四十）

## Context: example-starter 体系规则

在审查 P3C 之前，先验证以下体系级规范（已确立的规则）：

### 命名规范

1. **文件名必须以 `Example` 结尾**，不允许 `Test`、`Verify`、`Diag`、`Debug` 等后缀
2. **SPI 适配器保留 `*ExampleSpi.java` 命名**（可接受，不强制改名）
3. **类名与文件名一致**

### 入口规范

1. **所有 Example 必须有 `public static void main(String[] args)` 入口**
   - **豁免**：纯基类/抽象类/接口实现类（如 `BaseExample`、`SimpleEngineDataSourceExample`），不强制要求 main 方法
2. **参数通过 `args` 传递**，采用 `--key=value` 或 `--key value` 形式
3. **SPI 多实现场景**：args 中必须提供 `--spi=<implName>` 指定当前测试的实现
4. **不允许使用 JUnit**，结果通过 `System.exit(0/1)` 表达通过/失败
5. **`System.exit()` 仅允许在 `main` 方法中调用**，其他方法应返回状态码（int/String 等）

### 格式规范（强制）

1. **禁止将多行代码或注释压缩成一行**
   - 多条语句必须分行书写，每行一条语句
   - 多个注解、多个参数、链式调用过长时必须展开为多行
   - 连续的多条注释必须逐行展开，不允许 `// a; // b; // c` 式同行堆叠
   - 反例：`int a = 1; int b = 2; // 初始化 // 校验`
   - 正例：
     ```java
     // 初始化
     int a = 1;
     int b = 2;
     ```

### MyBatis 规范（强制）

1. **MyBatis 的 SQL 只允许写在 XML mapper 中，禁止使用注解编写 SQL**
   - 禁止：`@Select`、`@Insert`、`@Update`、`@Delete`、`@SelectProvider` 等注解 SQL
   - 正确做法：SQL 统一放在对应的 `*Mapper.xml` 中，接口方法仅做声明
   - 扫描方式：在含 MyBatis 依赖的模块中搜索 `org.apache.ibatis.annotations.Select|Insert|Update|Delete|SelectProvider|UpdateProvider|DeleteProvider|InsertProvider`
   - 发现即报告为 **[强制] 违规**

#### 三十九、iBatis/MyBatis 复杂 SQL 禁止使用 Lambda/Provider 内联编写（强制）

- **复杂 SQL 必须写在 XML mapper 文件中**，禁止通过 `@SelectProvider` / `@InsertProvider` / `@UpdateProvider` / `@DeleteProvider` 注解配合 Java lambda 或方法引用内联构建 SQL
- **适用场景**（满足任一即为复杂 SQL）：
  - 包含动态条件（`<if>` / `<where>` / `<foreach>` / `<choose>` 等 XML 标签）
  - 多表 JOIN 查询
  - 超过 5 行的 SQL 逻辑
  - 涉及子查询、 UNION、复杂排序/分页
- **禁止行为**：
  - `@SelectProvider(type = XxxProvider.class, method = "buildSql")` 通过 Java 类内联拼接 SQL 字符串 → [强制] 违规
  - 使用 lambda 表达式内联 SQL 构建逻辑（如 `context -> { StringBuilder sb = ...; return sb.toString(); }`）→ [强制] 违规
  - 将本应写在 `*Mapper.xml` 中的复杂 SQL 分散在多个 Provider 类中维护 → [强制] 违规
- **正确做法**：
  - 所有复杂 SQL（含动态 SQL）统一写入对应的 `*Mapper.xml` 文件
  - Mapper 接口仅保留方法签名，无注解或仅使用结果映射注解（`@Results`）
  - 简单单行 SQL（无动态条件、无 JOIN）可使用 XML mapper 或注解，但推荐统一走 XML
- **豁免场景**：
  - 极简 SQL（如 `SELECT 1`）可使用注解，不计入违规
  - `src/test/java` 中的测试用 Mapper 可放宽要求
- 反例：
  ```java
  // 反例：通过 Provider 类内联拼接复杂 SQL
  @SelectProvider(type = UserSqlProvider.class, method = "findUsers")
  List<User> findUsers(UserQuery query);

  // 反例：lambda 内联 SQL 构建
  @Select(context -> {
      StringBuilder sql = new StringBuilder("SELECT * FROM user WHERE 1=1");
      if (context.getCondition().getName() != null) {
          sql.append(" AND name = #{name}");
      }
      return sql.toString();
  })
  List<User> searchUsers(UserQuery query);
  ```
- 正例：
  ```xml
  <!-- 正确：复杂 SQL 写在 XML mapper 中 -->
  <select id="findUsers" resultType="User">
      SELECT * FROM user
      <where>
          <if test="name != null and name != ''">
              AND name = #{name}
          </if>
          <if test="status != null">
              AND status = #{status}
          </if>
      </where>
      ORDER BY create_time DESC
      LIMIT #{offset}, #{limit}
  </select>
  ```
- 扫描方式：搜索 `@SelectProvider` / `@InsertProvider` / `@UpdateProvider` / `@DeleteProvider` 注解，并检查对应 method 指向的 Provider 类中是否存在字符串拼接 SQL 逻辑（`StringBuilder` / `SQL` / `new SelectBuilder()` 等）

### 测试真实性规范（强制，最重要指标）

**本体系最重要的一条指标：测试不论是 JUnit、`main` 自检，还是前后端对接，只允许真实测试，不允许 mock。**

| 检查项 | 规则 | 级别 |
|---|---|---|
| 禁止一切 mock | `mock()`、`spy()`、`@Mock`、`@MockBean`、`@Spy`、`@SpyBean`、`when().thenReturn()`、`doReturn()`、`verify()` 等全部禁止 | [强制] |
| 禁止 mock 依赖库 | pom.xml 中禁止引入 `mockito`、`easymock`、`jmock`、`mockito-core`、`mockito-junit-jupiter` 等 mock 框架（含 test scope） | [强制] |
| JUnit 测试必须真实 | 单元测试必须构造真实对象、真实数据，调用真实方法，断言真实结果；禁止 mock 被测类的依赖 | [强制] |
| `main` 自检必须真实 | Example 必须连接真实组件（真实文件、真实网络、真实引擎），禁止为演示成功而 fake/替换真实调用 | [强制] |
| 前后端对接必须真实 | 前后端联调必须连真实后端服务与真实数据，禁止在前端测试/后端测试中 mock 整个接口 | [强制] |
| 真实测试数据 | 测试数据必须是真实可用的样本（真实 IP、真实图片、真实模型），禁止占位假数据冒充验证通过 | [强制] |

#### 四十、测试只允许真实测试，禁止 Mock（强制）

- **最重要指标**：测试不论是 JUnit、`main` 自检，还是前后端对接，只允许真实测试，不允许 mock。
- **禁止一切 mock 手法**（[强制] 违规）：
  - `Mockito.mock()`、`Mockito.spy()`
  - `@Mock`、`@MockBean`、`@Spy`、`@SpyBean` 注解
  - `when(...).thenReturn(...)`、`when(...).thenThrow(...)`、`doReturn(...)`、`doNothing().when(...)`、`verify(...)`、`mockStatic(...)`、`mockConstruction(...)`
- **禁止 mock 框架依赖**（[强制] 违规）：pom.xml 出现 `mockito`、`easymock`、`jmock`、`mockito-core`、`mockito-inline`、`mockito-junit-jupiter` 等（含 test scope）
- **真实测试的定义**：
  - JUnit 测试：构造**真实被测对象**及其**真实依赖**，喂真实数据，断言真实计算结果/真实状态变更
  - `main` 自检：Example 调用真实组件（真实 FileInputStream、真实网络连接、真实数据库、真实引擎），用 assert 断言真实输出
  - 前后端对接：前端请求**真实运行的后端服务**，后端返回**真实处理的数据**，断言真实响应
  - 第三方/硬件/外部服务：使用真实测试环境（如测试库、沙箱 API、本地真实引擎），禁止 mock 替代
- **豁免**：`System.out.println` 输出日志不算 mock；断言失败抛出 `AssertionError` 不算 mock；测试中构造真实输入数据不算 mock
- 反例：
  ```java
  // 错误：mock 被测类的依赖，测试未验证任何真实行为
  @MockBean
  private UserService userService;
  when(userService.findByName("张三")).thenReturn(realUser);

  // 错误：mock 数据访问层，等于没测
  UserDao dao = mock(UserDao.class);
  when(dao.findById(1L)).thenReturn(user);

  // 错误：前后端联调用假接口冒充
  mockServer.expect(request).respond(json);
  ```
- 正例：
  ```java
  // 正确：JUnit 构造真实对象与真实依赖
  UserService service = new UserService(realUserDao);
  service.createUser(realUser);
  User saved = realUserDao.findById(realUser.getId());
  assertEquals(realUser.getName(), saved.getName());

  // 正确：main 自检连真实数据源
  try (var conn = DriverManager.getConnection(REAL_URL, USER, PASS)) { ... }

  // 正确：前后端对接连真实后端
  RestTemplate client = new RestTemplate();
  var resp = client.postForEntity("http://localhost:8080/api/user", body, Map.class);
  assert resp.getBody() != null;
  ```
- **扫描方式**：搜索 `mockito`、`@Mock`、`@MockBean`、`@Spy`、`mock(`、`spy(`、`when(`、`thenReturn`、`thenThrow`、`doReturn`、`verify(`、`mockStatic` 等关键字，以及 pom.xml 中的 mock 依赖

### 边界规范（重要）

**Example 代码、JUnit 依赖、测试工具类只允许存在于 `example-starter` 模块中。**

| 位置 | 允许内容 |
|---|---|
| `utils-support-example-starter/` | 所有 Example、Spi 适配器、示例数据、`src/main/java` 中的测试代码 |
| 其他 starter 模块（`utils-support-*-starter`、`spring-support-*-starter`） | 仅正式生产代码，**禁止**出现 `*Example*`、`*Test*`、`*Verify*`、`*Diag*` 类，**禁止**引入 JUnit 依赖 |
| 其他 starter 模块的 `src/test/java` | 仅正式的单元测试（JUnit），不混入 Example 演示代码 |
| pom.xml | `example-starter` 可声明全量演示依赖；其他模块只声明生产依赖，不引入 `junit`、`mockito` 等测试作用域以外的演示库 |
| 测试方式（全部模块） | 测试（JUnit/`main`/前后端对接）只允许真实测试，**禁止 mock**；`example-starter` 与任何模块均不得引入 `mockito`/`easymock`/`jmock`（含 test scope），见规约四十 |

**审查其他模块时：**
- 扫描 `src/main/java` 中是否存在 `*Example*.java` / `*Test*.java` / `*Verify*.java` / `*Diag*.java`
- 检查该模块 pom.xml 是否意外引入了 `example-starter`、`junit`、`mockito`、`h2` 等仅用于演示的依赖
- 扫描测试代码是否使用 `@Mock`/`@MockBean`/`@Spy`/`when().thenReturn()`/`verify()` 等 mock 手法，或 pom.xml 引入 mockito/easymock/jmock → **[强制] 违规**（见规约四十）
- 发现即报告为 **[强制] 体系越界违规**，优先级高于普通 P3C 违规

### 调用模式

```bash
# 单实现示例
java ExampleRunner --example=vector-storage --type=memory
java ExampleRunner --example=json-provider --type=gson
java ExampleRunner --example=image-processor-spi --type=all

# SPI 多实现，指定具体实现
java ExampleRunner --example=vector-storage --type=jvector --mode=MEMORY
java ExampleRunner --example=json-provider --type=gson --spi=gson

# 独立 main 入口
java ThreadFlowExample --type=executor
java EngineExample --type=sqlite
```

---

## 核心工具模块复用规范（强制）

**架构分层原则（强制）**：

| 层级 | 模块 | 职责 |
|---|---|---|
| 顶层统一入口 | `utils-support-parent-starter` | 所有 utils-* 子模块的统一依赖父，所有项目的工具/通用方法应优先通过此父 POM 获取 |
| 基础工具层 | `utils-support-common-starter` | 零第三方依赖，纯 JDK 实现的工具类、算法、数据结构的唯一归属地；`utils-support-parent-starter` 内部各模块必须统一从此模块导入通用工具 |
| Spring 顶层入口 | `spring-support-parent-starter` | 所有 spring-* 子模块的统一依赖父，**复用 `utils-support-parent-starter` 的功能和代码** |
| Spring 工具层 | `spring-support-common-starter` | Spring Boot 环境下的工具基础，依赖 `utils-support-common-starter`，为所有 spring-* 模块提供公共能力 |

**模块间复用关系（强制，5 条铁律）**：

| 编号 | 复用关系 | 规则 |
|---|---|---|
| 1 | `spring-support-api-starter` → `spring-support-parent-starter` | **必须复用** spring-support-parent-starter 的功能和代码，禁止自己重复实现 |
| 2 | `spring-support-parent-starter` → `utils-support-parent-starter` | **必须复用** utils-support-parent-starter 的功能和代码，禁止自己重复实现 |
| 3 | `spring-support-parent-starter` 其它模块 → `spring-support-common-starter` | 其它 spring-* 模块必须复用 spring-support-common-starter 的功能和代码 |
| 4 | `utils-support-parent-starter` 其它模块 → `utils-support-common-starter` | 其它 utils-* 模块必须复用 utils-support-common-starter 的功能和代码 |
| 5 | 完善 common 后再复用 | **必须复用功能和代码，不允许冗余**；私有方法除外。若现有 common 功能不完善，必须先完善 `spring-support-common-starter` / `utils-support-common-starter`，然后各模块复用，禁止在业务模块中各自实现 |

**审查原则**：
- **必须复用，禁止冗余**：同一功能/同一段逻辑在两个及以上文件/模块中出现 → **[强制] 违规**，必须收敛到对应 common 模块
- **私有方法豁免**：仅类内部使用的 private 方法不属于"冗余复用"范畴，不判违规；但只要逻辑可能被多处使用，就必须下沉到 common 模块再复用
- **复用链路检查**：复用必须沿「上层 → 下层 common」单向链路，禁止反向依赖、禁止绕过对应 common 模块跨级引用同级模块
- 所有项目优先通过父 POM（`utils-support-parent-starter` / `spring-support-parent-starter`）依赖树获取工具，禁止在业务模块中自行实现可复用的通用逻辑

### utils-support-common-starter：通用工具基础模块（所有可复用代码的唯一归属）

**定位**：零第三方依赖，所有纯 JDK 实现的工具类、算法、数据结构的唯一归属地。无论是简单工具还是复杂业务逻辑，只要可被多个模块复用，就必须放在这里，而非在各业务模块中重复实现。

**核心能力清单**（其他模块必须通过 import 复用，禁止重新实现）：

| 能力 | 对应类 | 路径 |
|---|---|---|
| JSON 序列化 | `Json` | `com/chua/common/support/lang/json/Json.java` |
| 分页结果 | `PageResult` | `com/chua/common/support/lang/code/PageResult.java` |
| 返回码 | `ResultCode` / `ReturnResult` | `com/chua/common/support/lang/code/` |
| 日期工具 | `DateUtils` | `com/chua/common/support/utils/DateUtils.java` |
| 随机数 | `RandomUtils` | `com/chua/common/support/utils/RandomUtils.java` |
| 隐私脱敏 | `PrivacyUtils` | `com/chua/common/support/utils/PrivacyUtils.java` |
| 字符串 | `StringUtils` | `com/chua/common/support/utils/StringUtils.java` |
| 集合 | `CollectionUtils` | `com/chua/common/support/utils/CollectionUtils.java` |
| 数组 | `ArrayUtils` | `com/chua/common/support/utils/ArrayUtils.java` |
| Map | `MapUtils` | `com/chua/common/support/utils/MapUtils.java` |
| 对象 | `ObjectUtils` | `com/chua/common/support/utils/ObjectUtils.java` |
| Bean | `BeanUtils` | `com/chua/common/support/utils/BeanUtils.java` |
| 数字 | `NumberUtils` | `com/chua/common/support/utils/NumberUtils.java` |
| IO | `IoUtils` | `com/chua/common/support/utils/IoUtils.java` |
| 文件 | `FileUtils` | `com/chua/common/support/utils/FileUtils.java` |
| ID生成 | `IdUtils` | `com/chua/common/support/utils/IdUtils.java` |
| 加密 | `DigestUtils` | `com/chua/common/support/utils/DigestUtils.java` |
| 签名 | `SignUtils` | `com/chua/common/support/utils/SignUtils.java` |
| 反射 | `ClassUtils` / `ReflectUtils` | `com/chua/common/support/utils/` |
| 线程 | `ThreadUtils` | `com/chua/common/support/utils/ThreadUtils.java` |
| URL | `UrlUtils` | `com/chua/common/support/utils/UrlUtils.java` |

**审查规则**：
- **核心原则：能复用的必须复用，禁止重复造轮子**：其他模块 `src/main/java` 中出现与 `utils-support-common-starter` 已有实现功能重复的代码（无论简单或复杂）→ **[强制] 重复造轮子**
- **禁止冗余和重复功能**：同一段逻辑、同一种能力在两个及以上模块/文件中出现 → **[强制] 违规**，必须收敛到 `utils-support-common-starter` 或 `spring-support-common-starter`，禁止各自实现
- 必须通过 `import com.chua.common.support.*` 复用，禁止复制代码或重新实现
- 核心类（`Json`/`PageResult`/`ResultCode` 等）在多个模块中出现不同实现 → **[强制] 模块越界**，统一收敛到 `utils-support-common-starter`
- **工具类缺失时的处理**：若发现某模块需要某种通用能力（如列表过滤、字符串校验、数值范围检查等），而 `utils-support-common-starter` 中尚无对应工具类，则**必须先在 `utils-support-common-starter` 中补充实现该工具类**，再让各业务模块通过 import 复用，禁止在各业务模块中自行创建替代工具类
- **禁止在各模块中重复实现相同逻辑**：同一段逻辑在两个及以上模块中出现 → **[强制] 违规**，必须收敛到 `utils-support-common-starter` 或 `spring-support-common-starter`
- **层级合规检查**：`utils-support-parent-starter` 内各子模块引用的通用工具必须来自 `utils-support-common-starter`，不得从其他同级模块（如 `utils-support-network-starter`）导入工具类；非 parent-starter 的外部项目也应通过 parent-starter 统一获取，而非直接引用某个子模块

### spring-support-common-starter：Spring 工具基础模块

**定位**：Spring Boot 环境下的工具基础模块，依赖 `utils-support-common-starter`，向上为所有 spring-* 模块提供公共能力。同样遵循"能复用的必须复用"原则：无论是简单配置还是复杂业务逻辑，只要可被多个 spring-* 模块复用，就必须放在这里。

**核心能力清单**（其他模块应优先复用）：

| 能力 | 对应包/类 | 路径 |
|---|---|---|
| Spring 配置 | `configuration/` | `com/chua/starter/common/support/configuration/` |
| Jackson 定制 | `jackson/` | `com/chua/starter/common/support/jackson/` |
| API 层规范 | `api/` | `com/chua/starter/common/support/api/`（CORS/拦截器/版本控制） |
| 过滤器 | `filter/` | `com/chua/starter/common/support/filter/` |
| HTTP 客户端封装 | `http/` | `com/chua/starter/common/support/http/` |
| 日志/Logger | `log/` / `logger/` | `com/chua/starter/common/support/log/` |
| 转换器 | `converter/` | `com/chua/starter/common/support/converter/` |
| 表达式 | `expression/` | `com/chua/starter/common/support/expression/` |
| 异常 | `exception/` | `com/chua/starter/common/support/exception/` |
| 常量 | `constant/` | `com/chua/starter/common/support/constant/` |
| 工具集 | `utils/` | `com/chua/starter/common/support/utils/` |
| 网络 | `network/` | `com/chua/common/support/network/` |
| 对象上下文 | `objects/` | `com/chua/common/support/objects/` |

**审查规则**：
- 其他 spring-* 模块 `src/main/java` 中出现与上述包**同功能**的工具类（如自定义 `JsonUtil`、自定义 `PageResult`、自定义 `DateUtils`）→ **[强制] 未复用 spring-support-common-starter**
- spring-* 模块应优先从 `spring-support-common-starter` 导入，而非自己写一份
- `spring-support-common-starter` 已提供的能力在其他模块中被重复实现 → **[强制] 违反复用规范**
- **禁止冗余和重复功能**：同一段逻辑、同一种能力在两个及以上模块/文件中出现 → **[强制] 违规**，必须收敛到 `spring-support-common-starter` 或 `utils-support-common-starter`
- **层级合规检查**：spring-* 模块引用的通用工具必须来自 `spring-support-common-starter`（Spring 相关）或 `utils-support-common-starter`（JDK 相关），不得从其他同级 starter 模块导入工具类

---

## Workflow

### Step 1: 定位目标文件

确定要审查的文件路径，默认扫描整个 example-starter：

```
D:/ch/project/utils-support-example-starter/src/main/java/com/chua/example/
```

备用目录（`-new` 变体，若主目录为空或用户指定时使用）：
```
D:/ch/project/utils-support-example-starter-new/src/main/java/com/chua/example/
```

如果用户指定了具体文件或包路径，只审查该范围。

### Step 2: 体系规范预检

对每个文件执行以下检查，先于 P3C 检查：

**前置：判断文件所属模块**

| 检查项 | 规则 | 级别 |
|---|---|---|
| 文件是否在 `example-starter` 中 | 非 example-starter 出现 `*Example*`/`*Test*`/`*Verify*`/`*Diag*` → 体系越界 | [强制] |
| 文件名以 `Example` 结尾 | 不允许 `Test/Verify/Diag` | [强制] |
| 类名以 `Example` 结尾 | 不允许 `Test/Verify/Diag` | [强制] |
| 存在 `main` 方法 | 独立 Example 必须有；基类/抽象类豁免 | [强制] |
| `main` 通过 `args` 解析参数 | 不支持 args 属于体系违规 | [强制] |
| SPI 多实现有 `--spi` 参数说明 | 文档/Javadoc 中需说明 | [推荐] |
| 无 JUnit 依赖或注解 | `@Test`/`Assert`/`assertEquals` 等均不允许 | [强制] |
| 无单行压缩代码/注释 | 多语句、多注释压缩成一行 → 违规 | [强制] |
| 无 MyBatis 注解 SQL | `@Select`/`@Insert`/`@Update`/`@Delete` 等 → 违规 | [强制] |
| SPI 有对应主 Example | `*ExampleSpi.java` 必须存在同名主 Example | [强制] |
| `System.exit()` 仅在 main 中 | 其他方法调用 → 违规 | [强制] |
| Closeable 资源已关闭 | InputStream/Reader/Connection 等必须 try-with-resources | [强制] |
| 未重复实现工具类 | 未复用 `utils-support-common-starter` 已有能力 | [强制] |
| 无冗余重复代码 | 同一段逻辑在两个及以上文件/模块中出现，未提取为工具类 | [强制] |
| 层级合规 | utils-* 模块工具统一来自 `utils-support-common-starter`，spring-* 模块复用 `spring-support-common-starter`；spring-support-api 复用 parent，spring-support-parent 复用 utils-support-parent，禁止跨级/反向/绕过 common | [强制] |
| 无空实现方法 | 非 Override 方法体不能为空，不能直接 return 无副作用 | [强制] |
| 注释完整 | 非 Override 方法/类/属性必须有详细 Javadoc 或行内注释 | [强制] |
| 反射统一使用 ReflectUtils | 反射应尽量避免；确需反射时必须经 ReflectUtils，禁止直接使用 java.lang.reflect API | [强制] |
| Map 优先用实体 | 有明确语义的结构化数据用 record/@Data 代替 Map；动态场景用 Map 时须预估容量 | [强制] |
| NativeLoader 统一管理 | 动态库加载必须通过 NativeLoader.of(taskId).toTarget(dir).glob(pattern).load()，禁止 System.load | [强制] |
| 工具类优先无重复 | 相同/相似逻辑必须提取为工具类，禁止跨文件重复实现 | [强制] |
| 接口分发使用 ServiceProvider SPI | 多实现路由必须通过 `ServiceProvider.of(Xxx.class).getExtension(name)`，禁止硬编码 instanceof/switch/case | [强制] |
| 测试覆盖完整 | 有 src/main/java 代码必须有对应测试，覆盖全部场景且可复现 | [强制] |
| 测试输出目录统一 | 日志/临时文件必须输出到 `test-output/` 目录，禁止散落到源码/工作目录 | [强制] |
| Java 25+ 简化 | 数据传输对象必须用 record，局部变量优先 var，静态方法必须 import static | [强制] |
| Lombok 配置正确 | pom.xml 必须配置 annotationProcessorPaths，禁止手写 Lombok 已生成代码 | [强制] |
| 静态字段安全 | static 非 final 可变对象（List/Map/StringBuilder）→ 线程安全违规 | [强制] |
| 工具类构造 | 纯静态工具类必须有 private 构造函数 | [强制] |
| 参数校验前置 | public/protected 方法入口必须校验参数合法性 | [强制] |
| 接口实现真实落地 | 接口实现类所有方法必须有实际业务效果，禁止空壳/虚假实现 | [强制] |
| SQL拼接禁止 | 禁止字符串拼接构建SQL，必须使用参数化查询或XML占位符 | [强制] |
| 敏感信息不硬编码 | 禁止在源码中硬编码密码/密钥/Token/API Key | [强制] |
| Serializable序列号 | 实现Serializable的类必须声明serialVersionUID | [强制] |
| 不手动new Spring Bean | 禁止new应由Spring管理的Service/Repository/Client等 | [强制] |
| 生产级代码 | 所有代码必须是生产级别，覆盖全部场景，禁止半成品/TODO遗留 | [强制] |
| 高级功能有Example | 有状态/IO/并发/流程类功能必须在example-starter写Example并在docs生成测试报告HTML | [强制] |
| 完成即提交推送 | 功能+测试通过后立即commit+push，禁止本地改动超过1小时不推送 | [强制] |
| Example场景完整 | Example必须覆盖正常/边界/异常/多实现/并发/资源清理全部场景 | [强制] |
| 三方环境完备 | native-parent/resource-parent/models-parent 有对应文件才能开发 utils-support-parent-starter | [强制] |
| 三方文档必查 | 集成三方框架/库/SDK 前必须阅读官方文档，Javadoc 需注明文档链接 | [强制] |
| Example复杂度 | Example中不应有复杂业务逻辑，复杂逻辑应下沉到源码中 | [强制] |
| 异常控制流 | 禁止用异常做普通分支控制，禁止 new Throwable() | [强制] |
| 构造器循环 | this()/super() 不能形成调用环 | [强制] |
| 泛型类型安全 | 禁止裸类型 List/Map/Set，必须指定类型参数 | [强制] |
| 方法参数数量 | 超过 4 个参数必须提取为 record 参数对象 | [强制] |
| Javadoc 参数完整 | public/protected 方法必须有 @param/@return，参数名必须与签名一致 | [推荐] |
| 面向对象强制 | 业务逻辑必须采用 OOP（封装/继承/多态/接口+SPI），禁止过程式 static 方法串行；仅动态数据/动态加载场景可豁免 | [强制] |
| BigDecimal 精确计算 | 金额/高精度计算必须用 BigDecimal，禁止 double/float；禁止 new BigDecimal(double)，用 valueOf 或字符串构造 | [强制] |
| ThreadLocal 必须 remove | 使用 ThreadLocal 必须在 finally 中 remove，防止线程池内存泄漏 | [强制] |
| 禁止裸 return null | public/protected API 禁止裸 return null：集合返回空集合、可能缺失值用 Optional、有语义用 null 对象模式 | [强制] |
| 常量接口禁止 | 禁止用 interface 定义常量，必须用 final class + private 构造函数承载常量 | [强制] |
| SQL文件合并 | spring-support-* 模块只允许一个 schema.sql + 一个 data.sql，其它 SQL 必须合并；禁止散落多份初始化 SQL | [强制] |
| iBatis复杂SQL禁止Lambda | 复杂 SQL（动态条件/多表JOIN/子查询/超5行）禁止用 @SelectProvider + Provider 类内联构建或 lambda 内联 SQL；必须写入 *Mapper.xml | [强制] |
| 测试必须真实禁止Mock | 测试（JUnit/main/前后端对接）只允许真实测试，禁止 mock/mockito/@Mock/@MockBean/@Spy/when().thenReturn 等一切 mock 手法 | [强制] |

**非 example-starter 模块额外检查**

| 检查项 | 规则 | 级别 |
|---|---|---|
| pom.xml 是否引入演示库 | `junit`/`mockito`/`h2` 出现在非 test scope（scope=test 的 h2 不计入） | [强制] |
| src/main/java 是否混入示例类 | `*Example*`/`*Test*`/`*Verify*`/`*Diag*` 类名 | [强制] |
| MyBatis 注解 SQL | 含 MyBatis 依赖的模块中出现注解 SQL | [强制] |
| 是否重复实现 utils-support-common-starter 工具 | 自定义 `Json`/`DateUtils`/`PageResult`/`StringUtils`/`CollectionUtils` 等 | [强制] |
| 是否重复实现 spring-support-common-starter 工具 | 自定义 `Json`/`PageResult`/`DateUtils` 等 | [强制] |
| 是否存在冗余重复代码 | 同一段逻辑在两个及以上模块/文件中出现，未提取为工具类 | [强制] |
| 层级是否合规 | utils-* 子模块统一来自 utils-support-common-starter；spring-* 子模块复用 spring-support-common-starter；spring-support-api → spring-support-parent → utils-support-parent 单向复用，禁止跨级/反向/绕过 | [强制] |
| 是否直接使用反射 API | 反射应尽量避免；确需反射时必须经 ReflectUtils，禁止直接使用 Class.forName/getMethod/getField/invoke/Proxy 等 | [强制] |
| 是否滥用 Map 替代实体 | 有固定结构的业务数据用 Map 承载（应改用 record/@Data）；动态 Map 未预估容量 | [强制] |
| 是否绕过 NativeLoader | 使用 System.load/System.loadLibrary 直接加载动态库，未走 NativeLoader 统一管理 | [强制] |
| 是否存在重复代码 | 相同/相似逻辑在多个文件中出现，未提取为工具类 | [强制] |
| 是否硬编码多实现分发 | 使用 instanceof/switch/case 按类型选择实现，应改为 SPI 扩展点 | [强制] |
| 是否包含空实现方法 | 非 Override 方法体为空或仅 `return null/0/emptyList` | [强制] |
| 是否缺少注释 | 非 Override 方法/类/属性无 Javadoc 或行内注释 | [强制] |
| 是否过程式编程 | 业务逻辑用 public static 方法串行、数据行为分离，未用 OOP 封装（动态场景豁免） | [强制] |
| 是否用 double 计算金额 | 金额/高精度计算使用 double/float 或 new BigDecimal(double) | [强制] |
| ThreadLocal 是否清理 | ThreadLocal.set 后无 finally remove，存在线程池内存泄漏 | [强制] |
| 是否裸 return null | 集合/数组/可选值方法返回裸 null（应返回空集合或 Optional） | [强制] |
| 是否定义常量接口 | interface 定义常量或用 implements 常量接口，应改 final class + private 构造 | [强制] |
| spring-support-* SQL 文件合并 | spring-support-*-starter 模块只允许一个 schema.sql + 一个 data.sql；散落的其它 SQL 文件必须合并 | [强制] |
| 是否引入 mock 依赖 | pom.xml 引入 mockito/easymock/jmock 等（含 test scope）；JUnit 测试使用 @Mock/@MockBean/when().thenReturn 等 | [强制] |

### Step 3: P3C 逐维度审查

按以下优先级顺序检查，每发现一条违规记录文件路径 + 行号 + 规约编号 + 问题描述。

#### 1.1 命名风格

- 变量/方法名使用 `lowerCamelCase`，禁止 `_name`、`name_` 等
- 常量全部大写 + 下划线：`MAX_SIZE`，禁止 `maxSize`、`MAX_size`
- 类名 `UpperCamelCase`，禁止 `xmlService`、`UserDo`
- 禁止拼音混合：`getDaZhePromotion()`
- 布尔类型不加 `is` 前缀：用 `deleted` 而非 `isDeleted`（POJO 属性）

#### 1.2 常量定义

- 禁止魔法值：`"admin"`、`30` 等直接出现在代码中需提取为常量
- `long` 字面量使用大写字母 `L`：`1000L`，禁止 `1000l`
- **同一魔法值在同一文件中出现超过 5 次** → 升级为 [强制]

#### 1.3 代码格式

- **禁止多行代码/注释压缩成一行**：一条语句一行；多个连续注释逐行展开；链式调用与长参数列表展开换行
  - **禁止将多条独立语句压缩到同一行**：`int a = 1; int b = 2;` 违规，必须每行一条语句
  - **禁止将多条连续注释压缩到同一行**：`// 初始化 // 校验` 违规，必须逐行展开
  - **禁止将多行 Javadoc/@param/@return 标签压缩到同一行**：`/** @param a 参数a @param b 参数b */` 违规，每个标签必须独占一行
  - **禁止将多行字符串或 SQL 用字符串拼接方式压缩**：`String sql = "SELECT * FROM " + table + " WHERE id = " + id;` 违规，必须用 text block 或变量拼接
- 左大括号前不换行，右大括号后换行（else 除外）
- 运算符左右加空格：`a = b + c`
- 保留字与括号间加空格：`if (condition)`
- 4 空格缩进，禁止 tab
- 注释 `//` 与内容间一个空格
- 单行不超过 120 字符，超出行换行

**反例**：
```java
// 错误：多条语句压缩到一行
int a = 1; int b = 2; // 初始化 // 校验

// 错误：多行 Javadoc 压缩到一行
/** @param a 被加数 @param b 加数 @return 两数之和 */
public int add(int a, int b) { return a + b; }

// 错误：多行 SQL 压缩为字符串拼接
String sql = "SELECT * FROM user WHERE name = '" + name + "' AND age = " + age + " AND status = 'active'";
```

**正例**：
```java
// 正确：每行一条语句，每条注释独占一行
// 初始化
int a = 1;
int b = 2;

// 正确：每个 Javadoc 标签独占一行
/**
 * 计算两个整数的和。
 * @param a 被加数，不能为负
 * @param b 加数，不能为负
 * @return 两数之和
 * @throws IllegalArgumentException 当 a 或 b 为负时
 */
public int add(int a, int b) {
    if (a < 0) throw new IllegalArgumentException("a must >= 0");
    if (b < 0) throw new IllegalArgumentException("b must >= 0");
    return a + b;
}

// 正确：SQL 使用 text block
String sql = """
    SELECT * FROM user
    WHERE name = #{name}
      AND age = #{age}
      AND status = 'active'
    """;
```

#### 1.4 OOP 规约

- 静态访问直接用类名，禁止 `obj.staticMethod()`
- `@Override` 必须标注所有覆写方法
- POJO 属性用包装类型，局部变量用基本类型
- POJO 不设定属性默认值
- 构造方法禁止业务逻辑，用 `init()` 替代

#### 1.5 集合处理

- `hashCode()` 与 `equals()` 必须同时重写
- `ArrayList.subList()` 结果禁止强转 `ArrayList`
- foreach 中禁止 `remove/add`，用 Iterator
- 集合初始化指定 initialCapacity
- 遍历 Map 用 `entrySet()` 而非 `keySet()`

#### 1.6 并发处理

- 禁止 `Executors.newFixedThreadPool()` 等工厂方法，必须用 `ThreadPoolExecutor` 构造
- 线程池必须指定有意义的线程名称
- 禁止在循环中创建线程，使用线程池
- `SimpleDateFormat` 禁止 static 变量，用 `ThreadLocal` 或 `DateTimeFormatter`

#### 1.7 控制语句

- switch 每个 case 必须 `break/return` 或注释说明 fall-through
- `if/for/while` 必须有大括号，即使单行
- 异常分支优先使用卫语句

#### 1.8 注释规约（强制）

- **类必须有 Javadoc `/** */`**：必须包含 `@author` 和 `@since`，并详细说明类的用途、职责、使用场景及依赖关系
- **方法（所有非 `@Override` 方法）必须有完整 Javadoc**：必须说明方法用途、每个参数的含义与约束（输入）、返回值含义（输出）、可能抛出的异常及触发条件
  - **Javadoc 不允许空洞描述**：如 `/** 初始化 */`、`/** 处理方法 */` 这类无实质内容的注释属于违规，必须补充具体说明
  - **每个 `@param` 必须说明**：参数的用途、合法性约束、是否允许 null、边界值等
  - **每个 `@return` 必须说明**：返回值的含义、null 语义、可能的异常情况
  - **每个 `@throws` 必须说明**：在什么条件下抛出该异常
- **属性（字段）必须有行内注释**：不允许裸字段无注释；注释需说明字段用途、取值范围、是否可 null
  - **禁止将多个字段注释压缩到一行**：`private String name; // 姓名 // 用户名` 违规，每个字段注释独占一行
- **枚举字段必须有注释**：每个枚举值需有行内注释说明含义
- **Javadoc 中禁止出现 `TODO` 注释替代正式说明**：`// TODO: 后续补充注释` 属于违规，注释必须在提交前完成

**反例**：
```java
// 错误：类注释空洞
/** 工具类 */
public class DateUtils { ... }

// 错误：方法注释空洞，缺少参数和返回值说明
/** 处理数据 */
public Result process(String input) { ... }

// 错误：多个字段注释压缩到一行
private String name; // 姓名 // 用户名
private int age; // 年龄 // 限制 0-150

// 错误：Javadoc 中用 TODO 替代说明
/** @param input 输入 TODO */
public String handle(String input) { ... }
```

**正例**：
```java
/**
 * 日期时间工具类，提供常用的日期格式化、解析、计算等操作。
 * 本类为纯静态工具类，禁止实例化。
 *
 * @author developer
 * @since 4.0.0
 */
public class DateUtils {

    /** 默认日期格式化模式：yyyy-MM-dd */
    private static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd";

    /**
     * 将字符串解析为 LocalDateTime。
     *
     * @param dateStr 日期字符串，格式为 yyyy-MM-dd，不能为 null 或空字符串
     * @param pattern 日期格式模式，不能为 null 或空字符串，如 yyyy-MM-dd HH:mm:ss
     * @return 解析后的 LocalDateTime 对象
     * @throws IllegalArgumentException 当 dateStr 为 null 或空时
     * @throws DateTimeParseException   当 dateStr 无法按 pattern 解析时
     */
    public static LocalDateTime parse(String dateStr, String pattern) {
        Objects.requireNonNull(dateStr, "dateStr must not be null");
        if (StringUtils.isEmpty(pattern)) throw new IllegalArgumentException("pattern must not be empty");
        return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
    }
}
```

#### 1.9 空实现禁止（强制）

- **禁止任何方法体为空且无 `@Override` 豁免**：方法必须有实际业务逻辑，不允许 `{ }`、`return null;`、`return Collections.emptyList();`、`return 0;`、`throw new UnsupportedOperationException("not implemented");` 等占位实现
- **禁止属性/字段只有声明没有赋值且无注释说明**：必须有明确的初始化或清晰的 `// TODO` 标注
- **禁止内部逻辑直接 return 而不做任何处理**：如 `if (condition) return;` 没有任何副作用或日志输出
- 正确做法：实现业务逻辑，或使用 `throw new AssertionError("unreachable")` 标注确实不该执行到的分支
- 反例：
  ```java
  public void doSomething() { }
  public String getName() { return null; }
  public int calculate() { return 0; }
  if (user == null) return;  // 无任何处理
  ```
- 正例：
  ```java
  public void doSomething() {
      // 实际业务逻辑
      doRealWork();
  }
  public String getName() {
      return Objects.requireNonNull(name, "name must not be null");
  }
  ```

#### 1.10 尽可能避免反射（强制）

- **反射应尽量避免**：优先通过正常 API、构造器调用、接口方法、设计模式（如 SPI/扩展点）实现功能，反射仅作为无法替代的最后手段
- **即使使用反射，也必须走 `ReflectUtils`**（位于 `com.chua.common.support.reflection.ReflectUtils`），禁止在业务代码中直接使用 `java.lang.reflect` API
- 禁止直接使用以下 `java.lang.reflect` API：
  - `Class.forName(...)`、`clazz.getMethod(...)`、`clazz.getField(...)`
  - `method.invoke(...)`、`field.set(...)`、`field.get(...)`
  - `Proxy.newProxyInstance(...)`、`Constructor.newInstance(...)`
  - `LambdaMetafactory`、`MethodHandles.lookup()`
- 需要反射能力的场景统一调用 `ReflectUtils` 对应方法：
  - `ReflectUtils.instantiate(Class)` / `ReflectUtils.forName(String)` → 替代 `Class.forName().newInstance()`
  - `ReflectUtils.invoke(obj, method, args)` → 替代 `method.invoke(obj, args)`
  - `ReflectUtils.setField(obj, field, value)` → 替代 `field.set(obj, value)`
  - `ReflectUtils.newProxy(interfaces, handler)` → 替代 `Proxy.newProxyInstance()`
- `ReflectUtils` 本身内部可以使用 `java.lang.reflect` API（这是其职责）
- 反例：
  ```java
  // 错误：直接使用反射 API
  Class<?> clazz = Class.forName("com.example.Foo");
  Method m = clazz.getMethod("doStuff");
  m.invoke(obj);
  Field f = clazz.getDeclaredField("name");
  f.setAccessible(true);
  f.set(obj, "value");
  ```
- 正例（仅在无法避免时）：
  ```java
  // 正确：通过 ReflectUtils（且仅在无法避免时使用）
  Object obj = ReflectUtils.forName("com.example.Foo").instantiate();
  ReflectUtils.invoke(obj, "doStuff");
  ReflectUtils.setField(obj, "name", "value");
  ```
- **`ReflectUtils` 的定位**：基于 `MethodHandle + LambdaMetafactory` 实现的统一反射封装，缓存 method/field handle 避免重复查找；**但即便通过 ReflectUtils，也应先评估是否可以通过正常 API 或设计模式替代**

#### 1.11 接口多实现必须使用 ServiceProvider SPI（强制）

- **禁止在业务代码中使用 `instanceof` + 类型转换、`switch` + 字符串/枚举 case 来路由多实现**
- 接口多实现的分发必须通过 `ServiceProvider` 扩展点完成
- **`ServiceProvider` 定义**：`com.chua.common.support.spi.ServiceProvider`（位于 `utils-support-common-starter`），不是 Java 标准 `ServiceLoader`

**核心 API 与选型（强制理解）：**

| 方法 | 行为 | 适用场景 |
|---|---|---|
| `ServiceProvider.of(Xxx.class)` | 获取 ServiceProvider 实例（有缓存，同 class 返回同一实例） | 获取 Provider 本身 |
| `getExtension(name)` | 返回**缓存的单例**（同 name 多次调用返回同一对象） | 无状态服务、共享实例 |
| `getNewExtension(name, args)` | **每次创建新对象**（不缓存） | 有状态服务、每次需独立实例 |
| `getNewExtensions(name, args)` | 返回多个新实例的 List | 批量创建 |
| `getKeepExtension(uid, name, args)` | 按唯一标识 uid 创建并缓存 | 需要手动管理的生命周期 |
| `closeKeepExtension(uid)` | 关闭 keep 的实例 | 配合 getKeepExtension 释放资源 |
| `collect()` | 收集所有实现 | 遍历全部 |
| `getPriority()` / `top1()` | 获取最高优先级实现 | 选一个最优的 |
| `getDefault()` | 获取默认实现 | 兜底选择 |
| `register(name, ref)` | 手动注册实现 | 动态注册 |
| `isSupport(name)` / `has(name)` | 检查是否支持某实现 | 判断可用性 |

- 判断方式：代码中出现以下模式即视为违规：
  - `if (obj instanceof XxxImpl)` + 强制转换调用
  - `switch (type)` + `case "implA": return new ImplA();`
  - `if ("gson".equals(provider)) { return new GsonProvider(); } else if ("jackson"...`
  - Map<String, Provider> 硬编码注册表手动 new
- 正确做法：
  ```java
  // 无状态单例：用 getExtension（缓存）
  DataProcessor processor = ServiceProvider.of(DataProcessor.class).getExtension("json");
  
  // 有状态每次新建：用 getNewExtension（不缓存）
  DataProcessor processor = ServiceProvider.of(DataProcessor.class).getNewExtension("json", context);
  
  // 指定参数构造：用 getNewExtension(name, args...)
  Cache cache = ServiceProvider.of(Cache.class).getNewExtension("redis", host, port);
  
  // 获取最高优先级：用 getPriority()
  DataProcessor best = ServiceProvider.of(DataProcessor.class).getPriority();
  
  // 遍历所有：用 collect()
  List<DataProcessor> all = ServiceProvider.of(DataProcessor.class).collect();
  ```
- 反例：
  ```java
  // 错误：硬编码 if/switch 分发
  if (type instanceof GsonProvider) {
      return ((GsonProvider) type).toJson(obj);
  } else if (type instanceof JacksonProvider) {
      return ((JacksonProvider) type).toJson(obj);
  }
  // 错误：直接 new 而不是通过 ServiceProvider
  var provider = new GsonProvider();
  ```
- **SPI 实现类注解规范（强制）**：
  - 每个 SPI 实现类**必须**使用 `@Spi` 注解声明扩展名，支持多值：`@Spi({"json", "application/json"})`
  - 可选 `@SpiOrder(int)` 指定优先级（值越大优先级越高，默认 0）
  - 可选 `@SpiDescribe(value, type, desc)` 提供描述信息
  - 可选 `@Extension("name")` 声明扩展点名称
  - 反例：SPI 实现类缺少 `@Spi` 注解， ServiceProvider 无法扫描到
- **豁免**：单实现的简单判断（`instanceof` 仅用于空安全检查，非路由分发）不视为违规；已有 ServiceProvider 扩展点但尚未迁移的模块可标注 `// TODO: 迁移到 ServiceProvider` 作为临时状态

#### 2.2 日志规范

- 禁止直接使用 `System.out.println` 输出日志，使用 SLF4J `log.info()`
- 日志拼接使用占位符 `{}`，禁止字符串 `+` 拼接
- 禁止捕获 `RuntimeException` 后不处理（最低层），必须向上抛出或记录

#### 三、单元测试（适用于 Example 自检逻辑）

- Example 中的断言使用 `assert` 抛出 `AssertionError`，不用 JUnit `assertEquals` 等
- 禁止 `System.out.println` 做结果验证，必须用 assert
- 测试用例之间独立，不依赖执行顺序
- **测试必须真实，禁止 mock**（规约四十）：无论 JUnit 单测、`main` 自检还是前后端对接，只允许构造真实对象、连真实资源、跑真实逻辑、断言真实结果；禁止 `Mockito.mock`、`@Mock`/`@MockBean`/`@Spy`、`when().thenReturn()`、`verify()` 等一切 mock 手法，禁止引入 mockito/easymock/jmock 依赖（含 test scope）

#### 四、MyBatis SQL 规约（强制）

- **SQL 一律写在 XML mapper 中，禁止使用注解编写 SQL**
  - 禁止注解：`@Select`、`@Insert`、`@Update`、`@Delete`、`@SelectProvider`、`@InsertProvider`、`@UpdateProvider`、`@DeleteProvider`
  - 禁止 `@Results` + 注解 SQL 组合
  - 动态 SQL 必须用 XML 的 `<if>/<where>/<foreach>/<choose>` 等标签，禁止用注解 `<script>` 拼接
- **接口方法仅作声明**：Mapper 接口中只定义方法签名，SQL 逻辑全部在对应 `*Mapper.xml` 中实现
- **禁止在 Java 代码中拼 SQL 字符串**：无论是否在 MyBatis 模块中，均禁止字符串拼接构建 SQL（见规约二十二）
- **扫描方式**：在含 MyBatis 依赖（含 mybatis-plus）的模块中，搜索以下注解即视为违规：
  `org.apache.ibatis.annotations.Select|Insert|Update|Delete|SelectProvider|UpdateProvider|DeleteProvider|InsertProvider`
- 发现即报告为 **[强制] 违规**

**反例**：
```java
// 错误：注解 SQL
@Select("SELECT * FROM user WHERE name = #{name} AND status = #{status}")
List<User> findByNameAndStatus(@Param("name") String name, @Param("status") String status);

// 错误：注解动态 SQL
@SelectProvider(type = UserSqlProvider.class, method = "findUsers")
List<User> findUsers(UserQuery query);

// 错误：XML 中用 script 注解
@Select("<script>" +
        "SELECT * FROM user WHERE 1=1" +
        "<if test='name != null'>AND name = #{name}</if>" +
        "</script>")
List<User> findByCondition(@Param("name") String name);
```

**正例**：
```java
// 正确：接口仅声明方法
public interface UserMapper extends BaseMapper<User> {
    List<User> findByNameAndStatus(@Param("name") String name, @Param("status") String status);
}

// 正确：对应 UserMapper.xml
<!-- src/main/resources/mapper/UserMapper.xml -->
<select id="findByNameAndStatus" resultType="com.example.User">
    SELECT id, name, status, created_at
    FROM user
    WHERE name = #{name}
      AND status = #{status}
</select>
```

#### 五、资源关闭规约（强制）

- `InputStream`、`Reader`、`Connection`、`Client`、`Channel` 等 Closeable 资源
- 必须使用 try-with-resources，禁止手动 close 后不在 finally 块中
- 禁止在 finally 之外调用 `.close()`

#### 六、测试覆盖规约（强制）

- **所有业务逻辑必须有对应测试**：有 `src/main/java` 代码，必须有对应的 `src/test/java` 测试覆盖
- **测试必须真实，禁止 mock（最重要指标，规约四十）**：测试不论是 JUnit 单测、`main` 自检，还是前后端对接，只允许真实测试——构造真实对象、连接真实资源（真实文件/真实库/真实网络/真实引擎）、调用真实方法、断言真实结果；禁止 `Mockito.mock`/`@Mock`/`@MockBean`/`@Spy`/`when().thenReturn()`/`verify()` 等一切 mock 手法，禁止 pom.xml 引入 mockito/easymock/jmock 依赖（含 test scope）
- **测试路径裁决（2026-08-25）**：非 example-starter 模块的 pom **一律不引入 JUnit（含 test scope）**，「规约六测试覆盖」在非 example-starter 模块通过以下等价方式满足：① `example-starter` 中编写覆盖全部场景的 Example（main + args + [PASS]/[FAIL] + System.exit 表达结果）；② `docs/` 目录生成 `<功能名>测试报告.html`（含场景矩阵与真实耗时）。审查时**不得**因非 example-starter 模块缺少 src/test 或未接 JaCoCo 而报违规；发现模块 pom 残留 junit/mockito 依赖 → 报 **[强制] 边界违规**（test scope 亦不允许，h2 除外）
- **测试必须覆盖全部业务场景**：正常路径、异常路径、边界条件、空值/null、空集合等
- **测试必须是可复现的**：不依赖外部环境状态（网络、文件系统随机性），每次运行结果一致；禁止使用 `Thread.sleep()` 等待不确定时长，用 `CountDownLatch` 或轮询替代
- **测试日志和临时文件必须输出到统一目录**：禁止散落到源码目录（`src/`）、用户工作目录（`~`、`.`）、系统临时目录（`/tmp`、`C:\Temp`）
  - 统一输出目录：`${project.build.directory}/test-output/`（Maven）或 `${buildDir}/test-output/`（Gradle）
  - 测试中创建临时目录使用：`Paths.get(System.getProperty("java.io.tmpdir"), "test-output", "module-name").toAbsolutePath()`
  - 测试结束后必须清理：`@AfterEach` / `@AfterAll` 中删除临时文件和目录
  - 禁止在 `src/main/java` 或 `src/test/java` 同级目录下写入临时文件
- 反例：
  ```java
  // 错误：临时文件散落到工作目录
  Files.writeString(Path.of("result.json"), data);
  Files.createTempDirectory("test-");  // 散落到系统临时目录
  
  // 错误：测试依赖网络状态且不可复现
  Thread.sleep(5000);  // 等待不确定时长
  ```
- 正例：
  ```java
  private Path testOutputDir;
  
  @BeforeEach
  void setUp() {
      testOutputDir = Paths.get(System.getProperty("java.io.tmpdir"), "test-output", "my-module")
          .toAbsolutePath();
      Files.createDirectories(testOutputDir);
  }
  
  @AfterEach
  void tearDown() throws IOException {
      if (Files.exists(testOutputDir)) {
          Files.walk(testOutputDir)
              .sorted(Comparator.reverseOrder())
              .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
      }
  }
  ```

#### 七、Java 25+ 代码简化（强制）

- **优先使用 `record`**：不可变数据传输对象（DTO、VO、参数对象）必须用 `record` 替代 `class` + `final` 字段 + `equals/hashCode/toString`
  - 反例：`public class UserDTO { private final String name; ... @Data public class ... }`
  - 正例：`public record UserDTO(String name, String email) {}`
- **优先使用 `var`**：局部变量类型明确可推断时必须用 `var`，禁止显式写出已知类型
  - 反例：`HashMap<String, List<Foo>> cache = new HashMap<>();`
  - 正例：`var cache = new HashMap<String, List<Foo>>();`
- **优先使用 `import static`**：静态工具方法调用必须用 `import static` 简写，禁止全限定名调用
  - 反例：`com.chua.common.support.utils.StringUtils.isEmpty(s)`
  - 正例：`import static com.chua.common.support.utils.StringUtils.*; isEmpty(s)`
- **禁止使用已过时 API**：`new Date()` 替代 `LocalDateTime`、`Collections.emptyList()` 替代自定义空列表工具、`System.out` 替代日志框架
- **使用 Java 21+ 特性**：switch 表达式、sealed classes（用于有限类型体系）、text block（多行字符串）

#### 八、Map 使用规约（强制）

- **优先使用实体类代替 Map**：凡是有明确语义的结构化数据，必须用 `record` 或 `@Data` 类代替 `Map<String, Object>` / `HashMap`
  - 反例：
    ```java
    // 错误：用 Map 承载有固定结构的业务数据
    Map<String, Object> user = new HashMap<>();
    user.put("name", "张三");
    user.put("age", 25);
    String name = (String) user.get("name");
    ```
  - 正例：
    ```java
    // 正确：用 record 替代
    public record UserDTO(String name, int age) {}
    UserDTO user = new UserDTO("张三", 25);
    String name = user.name();
    ```
- **动态场景必须使用 Map 时，初始化需预估容量**：避免频繁 resize
  - 反例：
    ```java
    // 错误：无容量预估，可能多次扩容
    Map<String, Object> config = new HashMap<>();
    config.put("host", host);
    config.put("port", port);
    ```
  - 正例：
    ```java
    // 正确：预估容量，减少扩容次数
    Map<String, Object> config = new HashMap<>(8);
    ```
- **禁止在业务代码中将 Map 作为返回值传递结构化数据**：调用方无法得知字段含义，易出错
- **枚举、常量、record 替代 Map Key 字符串魔法值**

#### 九、NativeLoader 统一管理动态库（强制）

- **所有原生动态库（`.dll`/`.so`/`.dylib`）加载必须通过 `NativeLoader`**（`com.chua.common.support.utils.NativeLoader`），禁止使用 `System.load()` / `System.loadLibrary()` 直接加载
- `NativeLoader` 提供链式 API：`NativeLoader.of(taskId).toTarget(dir).glob(pattern).load()`
- 动态库统一从 `utils-support-native-parent` 获取，`src/main/resources/native/{platform}/` 下打包
- **禁止在业务代码中自行实现 Native 库加载逻辑**（自己写 JNI、自己写路径拼接、自己调用 `System.load`）
- 反例：
  ```java
  // 错误：直接 System.load，无统一管理
  System.load("/usr/lib/libarcsoft_face.so");
  System.loadLibrary("face-engine");
  ```
- 正例：
  ```java
  // 正确：通过 NativeLoader 统一加载
  NativeLoader.of("arcsoft")
      .toTarget(modelPath + "/arcsoft")
      .glob("libarcsoft_face*.so")
      .load();
  ```
- Example 中如需演示 Native 库加载，同样走 `NativeLoader`，禁止绕过
- **越界违规**：若 `utils-support-native-parent` 中无对应平台动态库，不得在 `utils-support-parent-starter` 中实现该 native 功能（见规约三十）

#### 十、工具类优先与禁止重复代码（强制）

- **相同或高度相似的逻辑必须提取为工具类**，禁止在同一模块或跨模块中重复出现
- **判断标准**（满足任一即触发）：
  1. 同一段逻辑在 2 个及以上文件中出现（复制粘贴或手写重复）
  2. 逻辑结构相似，仅变量名不同（如多个地方都做同样的参数校验、同样的 IO 读取）
  3. 存在通用算法/计算/转换，可被多个场景复用
- **工具类归属**：
  - 纯 JDK 实现的通用工具 → `utils-support-common-starter`（`com.chua.common.support.utils.*`）
  - Spring 相关的工具 → `spring-support-common-starter`（`com.chua.starter.common.support.*`）
  - Example 专用工具 → `example-starter`（仅限示例用途）
- **禁止行为**：
  - 在业务模块中复制 `DateUtils`/`Json`/`CollectionUtils` 等功能
  - 多处写相同的 null 检查 / 类型转换 / 字符串处理
  - 同一个工具类以不同包名在多个模块中存在
- 反例：
  ```java
  // 错误：A.java 和 B.java 各自实现相同逻辑
  // A.java
  if (obj == null) throw new IllegalArgumentException("obj must not be null");
  // B.java（完全相同）
  if (obj == null) throw new IllegalArgumentException("obj must not be null");
  ```
- 正例：
  ```java
  // 正确：统一抽取为工具方法，import 复用
  Objects.requireNonNull(obj, "obj must not be null");
  // 或直接使用已有工具：
  if (StringUtils.isEmpty(name)) { ... }
  ```
- **发现重复逻辑的修复流程**：
  1. 先在 `utils-support-common-starter`（或对应模块）中补充工具方法
  2. 再在所有引用处改为 import 复用
  3. 删除重复代码

#### 十一、Lombok 使用规约（强制）

- **必须使用 Lombok 简化样板代码**：`@Data`、`@Builder`、`@AllArgsConstructor`、`@NoArgsConstructor`、`@Slf4j`、`@RequiredArgsConstructor`、`@EqualsAndHashCode` 等
- **Java 25+ 必须配置 `annotationProcessorPaths`**：Lombok 1.18.34+ 支持 Java 21+，但必须在 pom.xml 的 `maven-compiler-plugin` 中配置 `annotationProcessorPaths`，否则注解处理器无法在 Java 25 上运行
  - 配置模板：
    ```xml
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
            <annotationProcessorPaths>
                <path>
                    <groupId>org.projectlombok</groupId>
                    <artifactId>lombok</artifactId>
                    <version>${lombok.version}</version>
                </path>
            </annotationProcessorPaths>
        </configuration>
    </plugin>
    ```
  - **缺少 `annotationProcessorPaths` 导致编译失败 → [强制] 违规**
- **禁止手写 Lombok 已生成的代码**：如手写 `equals/hashCode/toString/getter/setter`，与 Lombok 注解冲突
- **`@Data` vs `@Getter @Setter`**：纯数据载体用 `@Data`；需要控制访问权限的用 `@Getter @Setter` 分别标注
- **日志统一用 `@Slf4j`**：禁止手动 `private final Logger log = LoggerFactory.getLogger(...)`

#### 十二、静态可变字段禁止（强制）

- **禁止 `static` + 非 `final` 的可变对象字段**：`static List`、`static Map`、`static Set`、`static StringBuilder` 等在多线程环境下存在竞态条件
- 正确做法：
  - 不可变数据用 `static final` + `Collections.unmodifiableList/Map/Set`
  - 可变数据用 `ConcurrentHashMap`、`CopyOnWriteArrayList` 等线程安全容器
  - 或者不使用静态字段，改为实例字段
- 反例：
  ```java
  // 错误：静态可变字段，线程不安全
  static List<String> cache = new ArrayList<>();
  static Map<String, Object> data = new HashMap<>();
  static StringBuilder sb = new StringBuilder();
  ```
- 正例：
  ```java
  // 正确：不可变静态常量
  static final List<String> ALLOWED_TYPES = Collections.unmodifiableList(List.of("a", "b"));
  // 正确：线程安全可变
  static final ConcurrentHashMap<String, Object> data = new ConcurrentHashMap<>();
  // 正确：实例字段
  private final List<String> cache = new ArrayList<>();
  ```

#### 十三、工具类私有构造（强制）

- **纯工具类（只有静态方法）必须有私有构造函数**，防止被意外实例化
- 反例：
  ```java
  public class StringUtils {
      public static String isEmpty(String s) { return s == null || s.isEmpty(); }
  }
  // 可被 new StringUtils() 实例化
  ```
- 正例：
  ```java
  public class StringUtils {
      private StringUtils() { }  // 防止实例化
      public static String isEmpty(String s) { return s == null || s.isEmpty(); }
  }
  ```

#### 十四、Java 21+ 现代特性（推荐）

- **switch 表达式**：多分支返回应使用 switch 表达式替代 if-else 链
  - 反例：`if ("a".equals(x)) return 1; else if ("b".equals(x)) return 2;`
  - 正例：`return switch (x) { case "a" -> 1; case "b" -> 2; default -> 0; };`
- **文本块（text block）**：多行字符串（SQL、JSON、HTML）必须用 text block
  - 反例：`String sql = "SELECT * FROM user WHERE id = ?";`
  - 正例：
    ```java
    String sql = """
        SELECT u.name, o.total
        FROM users u
        JOIN orders o ON u.id = o.user_id
        WHERE u.status = ?
        """;
    ```
- **Sealed classes**：有限类型体系（如状态枚举、消息类型）应使用 `sealed` 替代大量 `instanceof` 判断
- **Pattern matching instanceof**：Java 16+ 可用 `if (obj instanceof String s)` 替代 `if (obj instanceof String) { String s = (String) obj; }`

#### 十五、参数校验前置（强制）

- **所有 `public` / `protected` 方法的入口处必须校验参数合法性**，禁止把校验推迟到方法内部逻辑中
- 必检项：null 检查（`Objects.requireNonNull`）、空字符串（`StringUtils.isEmpty`）、空集合（`CollectionUtils.isEmpty`）、范围边界（如分页 pageNo >= 1）
- 校验失败必须抛出具体的受检/非受检异常（`IllegalArgumentException`、`IllegalStateException`），禁止静默吞掉或返回 null
- 反例：
  ```java
  // 错误：内部才校验，且吞掉异常
  public void process(String name) {
      if (name == null) return;
      doWork(name.toLowerCase());
  }
  // 错误：外部调用方才能发现 null
  public Result query(int pageNo, int pageSize) {
      // 没有校验 pageNo < 1 或 pageSize <= 0
      return dao.findAll(pageNo, pageSize);
  }
  ```
- 正例：
  ```java
  public void process(String name) {
      Objects.requireNonNull(name, "name must not be null");
      doWork(name.toLowerCase());
  }
  public Result query(int pageNo, int pageSize) {
      if (pageNo < 1) throw new IllegalArgumentException("pageNo must >= 1, got: " + pageNo);
      if (pageSize <= 0 || pageSize > 100) throw new IllegalArgumentException("pageSize must in (0, 100], got: " + pageSize);
      return dao.findAll(pageNo, pageSize);
  }
  ```
- **豁免**：private 方法由上层 public 方法保证参数合法的可不重复校验；@Override 方法依赖父类/接口契约的不强制要求

#### 十六、禁止用异常做控制流（强制）

- **禁止捕获异常后将其作为普通分支逻辑处理**：`catch` 块中不应该有"继续执行主逻辑"的路径
- **禁止 `new Throwable()` 或自定义异常作为流程控制手段**：异常是异常情况，不是普通返回值
- 禁止用 try-catch 包裹本可用 if-else 判断的代码
- 反例：
  ```java
  // 错误：用异常判断文件是否存在
  try {
      Files.readString(path);
  } catch (NoSuchFileException e) {
      return defaultValue;  // 正常分支
  }
  
  // 错误：new Throwable 做流程控制
  try {
      throw new MyFlowControlException();  // 故意抛异常
  } catch (MyFlowControlException e) {
      doNext();
  }
  ```
- 正例：
  ```java
  // 正确：用 if 判断
  if (Files.exists(path)) {
      return Files.readString(path);
  }
  return defaultValue;
  ```

#### 十七、禁止构造器循环调用（强制）

- **`this()` 和 `super()` 调用不能形成调用环**，编译器通常能检测，但代码审查时必须检查
- 禁止通过多个构造器互相调用来形成隐式循环
- 反例：
  ```java
  public Foo(String name) { this(name, 0); }  // 循环！
  public Foo(String name, int age) { this(name); }
  ```
- 正确做法：只有一个构造器调用 `super()`，其余构造器调用其中一个

#### 十八、泛型必须指定类型参数（强制）

- **禁止使用裸类型（raw type）**：`List`、`Map`、`Set` 等必须指定类型参数
- 禁止 `List list = new ArrayList()`、`Map map = new HashMap()` 等写法
- 允许 `var` 推断的情况例外：`var list = new ArrayList<String>();`
- 方法声明的泛型参数可以省略（如 `public <T> T identity(T t)`），但局部变量和字段必须显式指定
- 反例：
  ```java
  // 错误：裸类型
  List users = new ArrayList();
  Map<String, Object> config = new HashMap();  // 右边也应为 Map<String, Object>
  ```
- 正例：
  ```java
  // 正确：明确指定
  List<User> users = new ArrayList<>();
  Map<String, Object> config = new HashMap<>();
  ```

#### 十九、方法参数数量限制（强制）

- **公共方法参数超过 4 个时，必须提取为参数对象（record）**，禁止继续使用多个独立参数
- 参数对象用 `record` 声明，包含所有参数的不可变数据，并提供完整的 Javadoc
- 豁免：构造函数、`@Override` 方法、SPI 接口的标准回调方法、简单工厂方法（参数少且语义明确）
- 参数对象的字段命名须遵循 lowerCamelCase，每个字段必须有行内注释说明用途

**反例**：
```java
// 错误：6 个独立参数，应提取为 record
public User create(String name, String email, int age, String phone,
                   String address, String department) { ... }

// 错误：5 个独立参数，应提取为 record
public PageResult<Order> query(int pageNo, int pageSize, String keyword,
                               String status, LocalDateTime startTime) { ... }
```

**正例**：
```java
// 正确：提取为 record 参数对象
/**
 * 创建用户请求参数对象。
 *
 * @param name       用户名，不能为空，长度 2-50
 * @param email      邮箱地址，不能为空，需符合邮箱格式
 * @param age        年龄，范围 0-150
 * @param phone      手机号，中国 11 位数字格式
 * @param address    收货地址，允许为空
 * @param department 所属部门，允许为空
 */
public record CreateUserRequest(
        String name,
        String email,
        int age,
        String phone,
        String address,
        String department
) {}

public User create(CreateUserRequest req) {
    Objects.requireNonNull(req.name(), "name must not be null");
    if (req.age() < 0 || req.age() > 150) throw new IllegalArgumentException("age must be 0-150");
    // ... 业务逻辑
}
```

#### 二十、public 方法 Javadoc 参数完整性（推荐）

- **public / protected 方法的 Javadoc 必须包含 `@param` 和 `@return` 标签**，且参数名必须与签名一致
- 禁止只写 `/** 处理方法 */` 而缺少参数说明
- 禁止 `@param` 参数名与 `public void foo(String userName)` 中的参数名不一致
- 反例：
  ```java
  // 错误：缺少参数说明
  /** 计算结果 */
  public int calculate(int a, int b) { return a + b; }
  // 错误：参数名不一致
  /** @param x 第一个数 */  // 实际签名是 arg1
  public int calculate(int arg1, int arg2) { ... }
  ```
- 正例：
  ```java
  /**
   * 计算两个整数的和。
   * @param a 被加数，不能为负
   * @param b 加数，不能为负
   * @return 两数之和
   * @throws IllegalArgumentException 当 a 或 b 为负时
   */
  public int calculate(int a, int b) {
      if (a < 0) throw new IllegalArgumentException("a must >= 0");
      if (b < 0) throw new IllegalArgumentException("b must >= 0");
      return a + b;
  }
  ```


#### 二十一、接口实现必须真实落地（强制）

- **所有接口的实现类必须完整实现接口声明的所有方法，不允许只实现部分方法后留空壳**
- **禁止"虚假实现"**：方法体中仅做 `return null`、`return 0`、`return Collections.emptyList()`、`System.out.println()` 输出后不处理、或在内存中操作后无持久化等**无实际业务效果**的写法
- **"真实落地"定义**：实现必须产生可观测的业务效果，包括但不限于：
  - 数据持久化（写入数据库、Redis、文件等）
  - 网络通信（调用外部 API、发送消息、RPC 等）
  - 状态变更（修改实体状态、触发事件、更新缓存等）
  - 有意义的返回值（经过计算或查询得到的真实数据）
- **豁免场景**（需 Javadoc 标注原因）：
  - `Serializable` 接口的 `writeObject`/`readObject` 为空，且类中有 `serialVersionUID`
  - `Comparable` 接口的compareTo 方法因类型本身有序而不需要比较逻辑
  - `Cloneable` 接口没有 clone() 方法（Java 惯例）
  - SPI 接口的默认方法（`default` 关键字标注，有实现体）
  - 抽象类的抽象方法由子类实现的过渡层
- 判断方式：
  - 检查实现类中所有非 default、非 abstract 的方法体
  - 方法体仅包含 `{ }`、`return null/0/false/empty`、`System.out.println` → [强制] 违规
  - 方法体有真实业务逻辑但注释标注 `// TODO: 待实现` → [强制] 违规，必须完成
- 反例：
  ```java
  // 错误：空实现
  public class MemoryCache implements Cache {
      @Override
      public void put(String key, Object value) { }  // 什么都没做
      @Override
      public Object get(String key) { return null; }  // 永远返回 null
      @Override
      public boolean delete(String key) { return false; }  // 永远返回 false
  }

  // 错误：伪实现 — 看起来像工作但无实际效果
  public class FakeEmailService implements EmailService {
      @Override
      public void send(String to, String subject, String body) {
          System.out.println("Send email to: " + to);  // 只打印不发送
      }
  }
  ```
- 正例：
  ```java
  // 正确：真实落地
  public class RedisCache implements Cache {
      private final RedisTemplate<String, Object> redisTemplate;
      @Override
      public void put(String key, Object value) {
          redisTemplate.opsForValue().set(key, value, expiry, TimeUnit.SECONDS);
      }
      @Override
      public Object get(String key) {
          return redisTemplate.opsForValue().get(key);
      }
  }

  // 正确：真实发送
  public class SmtpEmailService implements EmailService {
      private final JavaMailSender mailSender;
      @Override
      public void send(String to, String subject, String body) {
          MimeMessage message = mailSender.createMimeMessage();
          // ... 构建并发送邮件
          mailSender.send(message);
      }
  }
  ```

#### 二十二、禁止 SQL 字符串拼接（强制）

- **禁止使用字符串拼接构建 SQL**：`"SELECT * FROM t WHERE id = " + id`、`String.format("SELECT ...", value)` 等写法存在 SQL 注入风险
- **所有 SQL 必须使用参数化查询**：MyBatis XML 的 `#{param}` 占位符、JDBC 的 `PreparedStatement`、或框架提供的条件构造器
- 豁免：测试代码、示例代码中的简单演示 SQL 可使用拼接，但主代码严禁
- 反例：
  ```java
  // 错误：字符串拼接 SQL
  String sql = "SELECT * FROM user WHERE name = '" + username + "'";
  stmt.executeQuery(sql);

  // 错误：MyBatis 注解中拼接
  @Select("SELECT * FROM order WHERE status = '" + status + "'")
  List<Order> findByStatus(String status);
  ```
- 正例：
  ```java
  // 正确：XML mapper 中使用 #{} 占位符
  <!-- mapper.xml -->
  <select id="findByName" resultType="User">
      SELECT * FROM user WHERE name = #{name}
  </select>

  // 正确：MyBatis Plus 条件构造器
  queryWrapper.eq("name", username);
  ```

#### 二十三、禁止硬编码敏感信息（强制）

- **禁止在源代码中硬编码密码、密钥、Token、API Key 等敏感信息**
- 敏感信息必须通过配置文件（`application.yml` / 环境变量 / 密钥管理服务）注入
- 检查范围：`password`、`passwd`、`secret`、`api_key`、`token`、`privateKey`、`accessKey`、`aesKey` 等变量赋值
- 豁免：占位符模板（如 `${db.password}`）、加密后的密文（需标注加密方式）、测试用的 Mock 数据（需在 `src/test/java` 中）
- 反例：
  ```java
  // 错误：硬编码密码
  private static final String PASSWORD = "admin123";
  private static final String API_KEY = "sk-abc123xyz";
  ```
- 正例：
  ```java
  // 正确：从配置读取
  @Value("${app.api.key}")
  private String apiKey;
  ```

#### 二十四、Serializable 类必须声明 serialVersionUID（强制）

- **所有实现 `Serializable` 接口的类必须声明 `private static final long serialVersionUID` 字段**
- 缺少该字段会导致序列化/反序列化版本不兼容时静默失败（不同 JVM 生成不同默认值）
- 值建议：使用 IDE 自动生成（如 `1L`），或在变更序列化结构时手动更新
- 反例：
  ```java
  // 错误：缺少 serialVersionUID
  public class UserDTO implements Serializable {
      private String name;
  }
  ```
- 正例：
  ```java
  public class UserDTO implements Serializable {
      private static final long serialVersionUID = 1L;
      private String name;
  }
  ```

#### 二十五、禁止手动 new Spring Bean（强制）

- **禁止在业务代码中使用 `new` 创建应由 Spring 管理的 Bean**
- 包括：Service、Repository、Client、Template、Converter、Manager 等所有属于 Spring 容器管理的类
- 正确做法：通过构造器注入（`@RequiredArgsConstructor`）或 `@Autowired` 注入
- 豁免：
  - `new` 纯 JDK 对象（如 `new ArrayList<>()`、`new HashMap<>()`）
  - 工具类实例（无 Spring 注解的纯静态工具）
  - Example 演示代码中为简化演示而 new 的对象
- 反例：
  ```java
  // 错误：手动 new Spring Bean
  private final RedisTemplate<String, Object> redis = new RedisTemplate<>();
  private final EmailService emailService = new SmtpEmailService();
  ```
- 正例：
  ```java
  // 正确：构造器注入
  @RequiredArgsConstructor
  public class OrderService {
      private final RedisTemplate<String, Object> redisTemplate;
      private final EmailService emailService;
  }
  ```

#### 二十六、生产级代码标准（强制）

- **所有代码必须是生产级别**：不允许存在"演示就完事"的代码进入 `src/main/java`
- **场景覆盖完整**：一个业务功能或工具类必须覆盖全部使用场景，包括但不限于：
  - 正常输入路径
  - 边界条件（空值、null、空集合、零值、最大值、最小值）
  - 异常输入（非法参数、格式错误、超时、网络中断）
  - 并发场景（多线程同时调用、竞态条件、死锁风险）
  - 资源耗尽（内存不足、连接池满、磁盘满）
  - 降级和兜底（依赖服务不可用时是否有 fallback）
- **禁止的"半成品"写法**：
  - `throw new UnsupportedOperationException("TODO")` — 必须有真实实现或移除
  - `return null` 作为通用返回值 — 应返回 `Optional` 或抛出明确异常
  - `catch (Exception e) { }` 空捕获 — 至少记录日志或重新抛出
  - `// TODO: 后续完善` 长期遗留 — 必须在当次迭代完成或创建追踪 issue
  - 仅支持单实现不支持扩展 — 多实现场景必须通过 SPI 机制
- **生产级检查清单**（每个 public API 必须满足）：
  1. 参数校验完整
  2. 异常处理有兜底
  3. 资源正确关闭（try-with-resources）
  4. 线程安全（并发场景可复现）
  5. 日志输出规范（SLF4J，无敏感信息）
  6. 有对应测试覆盖全部场景

#### 二十七、高级功能必须有 Example 和测试报告（强制）

- **"高级功能"定义**：不是简单静态工具方法（如 `StringUtils.isEmpty()`），而是包含以下特征的代码：
  - 有状态（维护内部数据/缓存/连接池）
  - 涉及 I/O（网络、文件、数据库）
  - 涉及并发（多线程、异步、回调）
  - 涉及配置（外部配置驱动行为）
  - 涉及插件/扩展点（SPI 多实现）
  - 涉及业务流程（多步骤编排、Pipeline、工作流）
- **强制要求**：高级功能必须在以下位置提供演示和验证：
  1. **`example-starter` 中编写 Example**：
     - 文件名以 `Example` 结尾，有 `public static void main` 入口
     - 覆盖该功能的**所有核心场景**（正常、边界、异常）
     - 每个场景有明确的 `[PASS]` / `[FAIL]` 输出标记
  2. **`docs/` 目录生成单元测试报告 HTML**：
     - 文件名格式：`<功能名>-test-report.html`（如 `vector-storage-test-report.html`）
     - 内容必须包含：测试场景列表、每个场景的通过/失败状态、执行时间、覆盖率统计
     - HTML 必须是可直接在浏览器中打开的独立文件（无外部依赖）
     - 每次功能变更必须更新对应报告
- 豁免：纯工具类（如 `DateUtils`、`Json`）无需 Example 和报告，但仍需满足生产级标准

#### 二十八、完成即提交推送（强制）

- **一个业务功能或完整实现 + 测试通过后，必须立即提交并推送到远程仓库**
- **禁止"本地改了一堆但还没提交"的状态持续超过 1 小时**
- **原因**：防止电脑故障、意外断电、误操作等导致代码丢失
- **流程规范**：
  1. 完成功能实现 → 编写测试 → 测试通过
  2. 立即 `git add -A` → `git commit -m "feat: <功能描述>"`
  3. 立即 `git push origin <branch>`
  4. 不要等待"凑一堆一起提交"——每个独立功能单独提交
- **禁止行为**：
  - 本地有未提交的大量改动超过 1 小时不推送
  - 为"方便"把多个不相关功能合到一个 commit 中
  - 认为"本地有备份就不推送"——本地不等于备份
- **例外**：正在调试且改动尚未完成时，可暂不提交，但必须在 `.gitignore` 中排除调试文件，调试完成后立即提交

#### 二十九、Example 场景完整性（强制）

- **Example 代码必须覆盖被测功能的全部使用场景**，不得仅演示"Happy Path"
- **最低覆盖要求**：
  - 正常路径：标准入参，预期输出
  - 边界值：0、null、空集合、最大/最小值
  - 异常输入：非法类型、格式错误、空值
  - 多实现选择（SPI）：每个 `@Spi` 声明的扩展名都要有独立测试用例
  - 并发场景（涉及并发）：多线程同时调用，验证线程安全
  - 资源清理（涉及 IO）：验证流/连接/文件正确关闭
- **判断方式**：扫描 Example 文件中所有 `main` 方法内的调用，检查是否覆盖了该功能的所有分支
- 反例：
  ```java
  // 错误：只测了正常路径
  public static void main(String[] args) {
      processor.process("valid_input");  // 只有一种输入
      System.out.println("[PASS]");
  }
  ```
- 正例：
  ```java
  public static void main(String[] args) {
      // 正常路径
      assert process("valid_input").equals(expected) : "[FAIL] normal";
      // 边界值
      assert process("").equals(emptyResult) : "[FAIL] empty";
      assert process(null) == null : "[FAIL] null";
      // 异常输入
      try { process(invalid); assert false; } catch (IllegalArgumentException e) { /* 预期 */ }
      // 多实现
      ServiceProvider.of(Processor.class).forEach((name, p) -> {
          assert p.process("test").isValid() : "[FAIL] impl: " + name;
      });
      System.out.println("[PASS] all scenarios covered");
  }
  ```

#### 三十、三方依赖环境是实现的必要前提（强制）

- **三个基础仓库是 `utils-support-parent-starter` 实现的必要环境依赖**，缺少任一环境不得在该仓库中开展功能开发：

| 仓库 | 职责 | 存放内容 | 依赖场景 |
|---|---|---|---|
| `utils-support-native-parent` | 三方语言源码 + 动态库 | C/C++/Rust 源码、编译产物 `.dll`/`.so`/`.dylib`、JNI/JNA 桥接代码、编译脚本 | 任何需要调用 native 代码、性能敏感计算、硬件交互的功能 |
| `utils-support-resource-parent` | 资源文件 | 配置文件（yml/properties/json/xml）、静态数据（IP库、词库、字典、正则规则）、模板文件、证书/密钥文件 | 任何需要外部数据驱动、配置化、模板渲染的功能 |
| `utils-support-models-parent` | 嵌入模型文件 | ONNX/GGUF/BPE/Tokenizer/权重文件等 AI 模型文件（LFS 托管） | 任何涉及 AI 推理、模型调用、 embeddings、LLM 集成的功能 |

- **依赖关系**：
  - `utils-support-parent-starter` 的所有功能实现都建立在上述三个环境之上
  - 开发流程：先在对应环境仓库准备好资源 → 再在 utils-support-parent-starter 中引用和实现
  - 禁止在 utils-support-parent-starter 中直接存放大体积资源文件（模型/动态库/资源文件应放入对应环境仓库）

- **检查规则**：
  1. 功能涉及 native 调用时，必须确认 `utils-support-native-parent` 有对应的源码和编译产物
  2. 功能涉及配置/数据/模板时，必须确认 `utils-support-resource-parent` 有对应的资源文件
  3. 功能涉及 AI 模型时，必须确认 `utils-support-models-parent` 有对应的模型文件（LFS）
  4. 违反上述任一条 → **[强制] 环境缺失违规**，功能不得合入

- **反例**：
  ```
  // 错误：utils-support-parent-starter 中直接 hardcode 模型路径
  private static final String MODEL_PATH = "D:/models/gemma-3-270m/model.onnx";
  // 错误：native 调用没有对应的 native-parent 模块
  // 错误：资源文件散落在 utils-support-parent-starter/src/main/resources/
  ```

- **正例**：
  ```
  // 正确：从 resource-parent 加载配置
  @Value("${app.model.path}")
  private String modelPath;
  // 正确：native 库由 native-parent 编译后通过 Maven 依赖引入
  // utils-support-parent-starter/pom.xml 中声明 native-starter 依赖即可
  ```

#### 三十一、集成三方框架必须先查看官方文档（强制）

- **所有引入三方框架/库的代码，必须在实现前阅读并理解官方文档**，禁止仅凭猜测或经验直接编码
- **适用场景**：
  - 引入新的 Maven/Gradle 依赖（如 Redis SDK、MQ Client、AI SDK、数据库驱动等）
  - 调用三方 API（REST API、gRPC、WebSocket、SDK 方法等）
  - 使用三方中间件（Kafka、RabbitMQ、Elasticsearch、MinIO、Nginx 等）
  - 配置三方框架（Spring Boot AutoConfiguration、MyBatis Plus、Quartz 等）
  - 集成第三方认证/授权（OAuth、JWT、LDAP、CAS 等）

- **强制要求**：
  1. **阅读官方文档**：在编写任何集成代码之前，必须先阅读对应版本的官方文档
     - 文档地址必须在 Javadoc 或类注释中注明（`@see <url>`）
     - 禁止仅通过 StackOverflow、博客文章、AI 生成结果作为唯一依据
  2. **确认版本兼容性**：明确当前项目使用的 JDK 版本、框架版本与三方库版本的兼容性
     - 在 pom.xml 的依赖注释中注明最低兼容版本
  3. **最小可运行示例**：先在 Example 中验证三方 API 的核心用法，再集成到业务代码
  4. **错误处理按官方规范**：异常类型、错误码、重试策略遵循官方建议，不自行发明

- **反例**：
  ```java
  // 错误：未查阅文档，凭猜测调用 API
  redisTemplate.set("key", value);  // 实际 API 是 opsForValue().set()
  kafkaProducer.send(topic, key, value);  // 异步 send 返回 SendResult，需处理回调
  ```

- **正例**：
  ```java
  /**
   * Redis 缓存操作封装。
   * @see <a href="https://docs.redis.com/latest/">Redis Documentation</a>
   * @since 4.0.0.50
   */
  public class RedisCacheServiceImpl implements CacheService {
      // 先阅读官方文档确认 opsForValue().set(key, value, expire, unit) 的正确用法
      @Override
      public void set(String key, Object value, long expire, TimeUnit unit) {
          redisTemplate.opsForValue().set(key, value, expire, unit);
      }
  }
  ```

- **违规判定**：
  - 代码中调用了三方 API 但 Javadoc 中无官方文档链接 → [强制] 违规
  - 引入新依赖但无最小可运行示例验证 → [强制] 违规
  - 异常处理与官方文档不符（如忽略 checked exception、错误的重试策略）→ [强制] 违规

#### 三十二、Example 代码必须简洁，复杂逻辑应在源码中实现（强制）

- **Example 的定位是演示和验证**，不是业务逻辑的承载者
- **禁止在 Example 中编写复杂的业务处理逻辑**：如果 Example 中出现大量数据处理、条件分支、循环编排、多步骤计算等复杂逻辑，说明对应的源码功能不够完整，应将复杂逻辑下沉到正式代码中

- **判断标准**：以下情况属于「Example 过于复杂」，应触发本规则：
  1. Example 中有超过 3 层嵌套的条件分支（if/else/switch）
  2. Example 中有复杂的循环编排、数据转换、Pipeline 处理
  3. Example 中有大段的业务规则判断或状态机逻辑
  4. Example 中调用了多个内部辅助方法（private helper methods）
  5. Example 文件超过 200 行（不含注释和 import）

- **正确做法**：
  - 将复杂逻辑封装到正式的服务类/工具类中
  - 通过 ServiceProvider SPI 扩展点暴露多实现
  - Example 只负责：参数解析 → 调用正式 API → 断言结果 → 输出 [PASS]/[FAIL]
  - Example 的行为应该是线性的、可预测的、易于理解的

- **反例**（Example 中写了复杂逻辑）：
  ```java
  // 错误：Example 中实现了复杂的数据处理和条件分支
  public static void main(String[] args) {
      for (String input : inputs) {
          if (input.startsWith("A")) {
              if (input.length() > 10) {
                  var result = processComplex(input);  // 复杂逻辑在 Example 中
              } else {
                  result = handleShort(input);
              }
          } else if (input.startsWith("B")) {
              result = handleB(input);
          }
          // ... 还有更多分支
      }
  }
  ```

- **正例**（Example 简洁，逻辑在源码中）：
  ```java
  // 正确：Example 只负责演示调用和验证结果
  public static void main(String[] args) {
      // 解析参数
      String type = parseArg(args, "--type", "default");
      String input = parseArg(args, "--input", "");
      
      // 调用正式 API（逻辑在源码中）
      Processor processor = ServiceProvider.of(Processor.class).getExtension(type);
      Result result = processor.process(input);
      
      // 断言验证
      assert result != null : "[FAIL] result is null";
      assert result.isValid() : "[FAIL] result not valid";
      System.out.println("[PASS] type=" + type + " input=" + input);
  }
  ```

- **违规判定**：
  - Example 文件中包含大量业务逻辑代码（非参数解析、非 API 调用、非断言）→ [强制] 违规
  - Example 中定义了 private 辅助方法 → [强制] 违规（应移到正式代码中）
  - Example 文件超过 200 行有效代码 → [强制] 违规

- **豁免**：纯展示性 Example（如 `HelloWorldExample`、`QuickStartExample`）可适量包含演示逻辑，但仍应保持简洁可读


#### 三十三、代码必须面向对象（强制）

- **业务代码必须采用面向对象范式**：通过**类的封装**（private 字段 + 行为方法）、**继承/组合**、**多态**（接口 + ServiceProvider SPI 扩展点）组织代码；数据与行为必须统一封装在对象中
- **禁止过程式（procedural）编程风格**：
  - 业务逻辑以大段 `public static` 方法串联实现（非纯工具类场景）
  - 用无状态的静态工具类堆叠实现业务流程，而非通过对象协作
  - 数据与行为分离：固定结构业务数据裸 Map 传递（见规约八）+ 外部 static 方法操作
- **例外：动态场景（允许面向数据/动态结构，不强制 OOP）**：
  - 运行时 schema 不稳定的动态数据（外部 JSON/配置文件/第三方响应解析的中间态）
  - 动态扩展/插件载入（反射、ServiceProvider 动态加载，扩展名不确定）
  - 通用 JSON 序列化/反序列化的中间表示
  - 框架/底层基础设施的动态编排与元编程
- 判定方式（满足任一即违规）：
  - 业务逻辑集中在 `public static` 方法中且调用链无对象实例参与
  - 用 `static` 状态 + 静态方法实现有状态业务流程
  - 固定结构业务数据散落为独立参数或裸 Map，且行为方法在外部操作这些数据
- 反例：
  ```java
  // 错误：过程式业务逻辑，全 static 方法串联，无对象封装
  public class OrderBiz {
      public static BigDecimal calcTotal(Map<String, Object> order) { ... }
      public static void validate(Map<String, Object> order) { ... }
      public static Map<String, Object> applyDiscount(Map<String, Object> order) { ... }
  }
  ```
- 正例：
  ```java
  // 正确：数据与行为封装在对象中（OOP）
  public class Order {
      private final List<OrderItem> items;
      public BigDecimal calcTotal() { ... }
      public void applyDiscount(Discount discount) { ... }
  }
  // 正确：多实现通过接口 + ServiceProvider SPI 多态分发
  public interface OrderProcessor { Result process(Order order); }
  ```
- **豁免**：纯静态工具类（`utils-support-common-starter` 中的 `DateUtils`/`Json` 等）不适用本规则，但必须遵守"工具类私有构造"规约

#### 三十四、BigDecimal 与精确计算（强制）

- **金额、单价、比例、精度敏感的运算必须使用 `BigDecimal`**，禁止使用 `double`/`float` 参与精确计算
- **禁止 `new BigDecimal(double)`**，会产生二进制浮点误差（如 `new BigDecimal(0.1)` ≠ 0.1）；必须用：
  - `BigDecimal.valueOf(double)`（优先，走字符串缓存）
  - `new BigDecimal("0.1")`（字符串构造）
- 比较用 `compareTo()` 而非 `equals()`（equals 会因 scale 不同返回 false）
- 除法必须指定 `MathContext` 或 `scale` + `RoundingMode`，禁止无限循环除法抛 `ArithmeticException`
- 反例：
  ```java
  // 错误：double 计算金额
  double total = 0.1 + 0.2;           // 0.30000000000000004
  BigDecimal d = new BigDecimal(0.1); // 误差
  a.divide(b);                        // 除不尽抛 ArithmeticException
  ```
- 正例：
  ```java
  // 正确：BigDecimal 精确计算
  BigDecimal total = BigDecimal.valueOf(0.1).add(BigDecimal.valueOf(0.2));
  BigDecimal share = a.divide(b, 2, RoundingMode.HALF_UP);
  int cmp = a.compareTo(b);
  ```
- 豁免：非精度敏感计算（统计近似值、百分比展示四舍五入后的场景）可用 double

#### 三十五、ThreadLocal 必须 remove（强制）

- **使用 `ThreadLocal` 的场景，线程结束/请求结束后必须在 `finally` 中调用 `remove()`**，否则高并发线程池复用线程时发生内存泄漏、脏数据串扰
- 禁止仅 set 不清理，禁止依赖 static 初始值兜底
- 反例：
  ```java
  // 错误：set 后不清理，线程池线程复用导致值串扰
  USER_CONTEXT.set(user);
  doWork();
  // 缺少 remove
  ```
- 正例：
  ```java
  // 正确：finally 中清理，并 try-with-resources 风格封装（如 AutoCloseable 上下文）
  try {
      USER_CONTEXT.set(user);
      doWork();
  } finally {
      USER_CONTEXT.remove();
  }
  ```
- **豁免**：`ThreadLocal` 作为静态 final 缓存且值不随线程变化的场景、`InheritableThreadLocal` 有明确清除机制的框架场景

#### 三十六、禁止裸 return null（强制）

- **public/protected 方法禁止返回裸 `null`**，必须用更安全的替代：
  - 集合/数组返回类型：空值时返回 `Collections.emptyList()` / `List.of()` 等空集合，调用方无需判空
  - 可能缺失的单个值：返回 `Optional<T>`（`Optional.empty()`）
  - 有状态语义的场景：用 null 对象模式（Null Object）或明确自定义异常
- **例外场景**（不判违规）：
  - 明确表达"未初始化"的懒加载字段访问（如 fixed 缓存字段首次访问）
  - JPA/MyBatis Mapper 接口约定 `null` 表示"无记录"且调用方有判空
  - SPI 接口契约中明确声明"可能返回 null"的场景
- 反例：
  ```java
  // 错误：裸 return null
  public List<User> findUsers(String name) { ... return null; }
  public User findById(Long id) { ... return null; }
  ```
- 正例：
  ```java
  // 正确：空集合 / Optional
  public List<User> findUsers(String name) { ... return List.of(); }
  public Optional<User> findById(Long id) { ... return Optional.empty(); }
  ```

#### 三十七、禁止常量接口（强制）

- **禁止用 `interface` 定义常量**（Type/Object/If 常量接口反模式），Java 常量接口中的成员默认 `public static final`，实现类会被迫继承命名空间、产生冲突
- 常量必须承载在 `final class` + `private` 构造函数中：
  - `public final class StatusConstant { public static final int SUCCESS = 1; private StatusConstant() {} }`
  - 或用 `enum`（枚举常量）、`record`（不可变对象）
- **严禁 `implements XxxConstant` 后直接用 `SUCCESS` 引用常量**，必须用全限定名 `StatusConstant.SUCCESS` 或 `import static`
- 反例：
  ```java
  // 错误：常量接口
  public interface OrderStatus {
      int CREATED = 1;
      int PAID = 2;
  }
  public class Order implements OrderStatus { ... PAID ... }
  ```
- 正例：
  ```java
  // 正确：final 常量类 + private 构造
  public final class OrderStatus {
      public static final int CREATED = 1;
      public static final int PAID = 2;
      private OrderStatus() { }
  }
  // 引用：OrderStatus.PAID 或 import static com.example.OrderStatus.PAID
  ```

#### 三十八、SQL 初始化文件统一合并（强制）

- **spring-support-*-starter 模块的 SQL 文件由 `spring-support-datasource-starter` 统一处理**，每个 spring-support-*-starter 模块中只允许存在以下两类 SQL 初始化文件：
  1. **一个初始化建表 SQL**：如 `schema.sql` / `init-schema.sql`，只包含建表语句（CREATE TABLE）
  2. **一个初始化数据 SQL**：如 `data.sql` / `init-data.sql`，只包含初始数据插入语句（INSERT INTO）
- **其它 SQL 文件必须合并**：除上述两类外的所有 SQL 文件（迁移脚本、测试数据、临时 SQL 等）必须合并到对应的 schema.sql 或 data.sql 中，禁止散落多份 SQL 文件
- **禁止行为**：
  - 同一模块中存在多个 schema/*.sql 或 data/*.sql 文件 → [强制] 违规
  - 存在 migrate/ 或 migration/ 子目录下的多个迁移文件（除非是 Flyway/Liquibase 约定的迁移流程，且仅 spring-support-datasource-starter 负责编排）→ [强制] 违规
  - 非初始化用途的 SQL 文件（如示例 SQL、调试 SQL）散落至 src/main/resources 下 → [强制] 违规
- **豁免场景**：
  - `src/test/resources/` 下的测试专用 SQL（H2 内存库初始化等）可独立存在，不计入本规约
  - Flyway/Liquibase 迁移脚本（`V{version}__*.sql`）由 `spring-support-datasource-starter` 统一管理，各业务模块不直接持有
- 反例：
  ```
  spring-support-example-starter/src/main/resources/
  ├── schema.sql          ← 仅保留一个建表文件 ✓
  ├── data.sql            ← 仅保留一个数据文件 ✓
  ├── init-role.sql       ← ✗ 应合并到 schema.sql
  ├── init-permission.sql ← ✗ 应合并到 schema.sql
  ├── test-data.sql       ← ✗ 移至 src/test/resources/ 或合并到 data.sql
  └── migrate-v1.sql      ← ✗ 由 datasource-starter 统一管理，业务模块不得持有
  ```
- 正例：
  ```
  spring-support-example-starter/src/main/resources/
  ├── schema.sql   ← 包含所有建表语句（已合并）
  └── data.sql     ← 包含所有初始化数据（已合并）
  # 其它：
  src/test/resources/schema-test.sql   ← 测试专用，豁免
  ```
### Step 4: 输出报告

对每个违规点输出：

```
[文件路径:行号] [规约编号] [级别] 问题描述
  反例: ...
  正例: ...
```

最后输出汇总统计：
- 总违规数（按级别分组）
- 涉及文件列表
- 最严重的前 3 个违规

## Output Format

```
## P3C 审查报告

**扫描范围**: <文件或目录路径>
**文件数**: N
**违规数**: M（强制 X / 推荐 Y / 参考 Z）

### 违规明细

#### [强制] 1.1 命名风格
- `example/onnx/SomeExample.java:45`
  - **问题**: 变量命名使用下划线 `max_count`
  - **反例**: `int max_count = 100;`
  - **正例**: `int maxCount = 100;`

#### [强制] 1.2 常量定义
- `example/pipeline/PipelineBasicExample.java:30`
  - **问题**: 魔法值 `"all"` 未提取为常量
  - **正例**: `private static final String DEFAULT_TYPE = "all";`

#### [强制] 五、资源关闭
- `example/onnx/WhisperAudioExample.java:103`
  - **问题**: InputStream 未使用 try-with-resources
  - **反例**: `InputStream is = new FileInputStream(...); is.read(); is.close();`
  - **正例**: `try (InputStream is = new FileInputStream(...)) { is.read(); }`

#### [强制] 1.9 空实现禁止
- `example/pipeline/PipelineExample.java:58`
  - **问题**: 非 Override 方法体为空，无任何业务逻辑
  - **反例**: `public void configure() { }`
  - **正例**: `public void configure() { applyDefaults(); validateConfig(); }`
- `example/pipeline/PipelineExample.java:72`
  - **问题**: 直接 return null 无实质处理
  - **反例**: `public String resolve() { return null; }`
  - **正例**: `public String resolve() { return Objects.requireNonNull(resolver.apply(context), "resolver result must not be null"); }`

#### [强制] 1.8 注释规约
- `example/ai/ChatClientExample.java:15`
  - **问题**: 非 Override 方法缺少 Javadoc，无参数说明
  - **反例**: `public void run(String model) { ... }`
  - **正例**: `/**\n * 执行 AI 聊天对话。\n * @param model 模型标识，如 gpt-4o\n * @throws IllegalArgumentException 当 model 为空时\n */\npublic void run(String model) { ... }`
- `example/ai/ChatClientExample.java:8`
  - **问题**: 字段缺少行内注释
  - **反例**: `private String apiKey;`
  - **正例**: `/** API 密钥，用于认证 AI 服务请求 */ private String apiKey;`

#### [强制] 1.10 尽可能避免反射
- `example/oauth/LoginCheck.java:407`
  - **问题**: 直接使用 `Class.forName` 和 `getMethod().invoke()`，应改用 ReflectUtils
  - **反例**: `Method m = clazz.getMethod("check"); m.invoke(obj);`
  - **正例**: `ReflectUtils.invoke(obj, "check");`
- `example/network/rpc/RpcEchoServiceImplExample.java:55`
  - **问题**: 直接使用 `Proxy.newProxyInstance`，应改用 ReflectUtils.newProxy
  - **反例**: `Proxy.newProxyInstance(cls.getClassLoader(), interfaces, handler);`
  - **正例**: `ReflectUtils.newProxy(interfaces, handler);`

#### [强制] 1.11 接口多实现必须使用 ServiceProvider SPI
- `example/ai/support/nomifun/service/NomifunContractService.java:42`
  - **问题**: 使用 `if (type instanceof GsonImpl)` + 强制转换硬编码分发，且未使用 ServiceProvider
  - **反例**: `if (obj instanceof GsonProvider) { return ((GsonProvider)obj).toJson(x); } else if (obj instanceof JacksonProvider)...`
  - **正例**: `ServiceProvider.of(JsonProvider.class).getExtension(type).toJson(x);`
- `example/common/support/api/feature/ApiFeatureConfiguration.java:159`
  - **问题**: switch-case 按字符串类型选择实现类，应改为 ServiceProvider
  - **反例**: `switch (provider) { case "gson": return new GsonFeature(); case "jackson": return new JacksonFeature(); }`
  - **正例**: `return ServiceProvider.of(ApiFeature.class).getNewExtension(provider);`

#### [强制] 八、Map 使用规约
- `example/ai/chat/ChatExample.java:55`
  - **问题**: 用 `Map<String, Object>` 承载固定结构的业务数据，字段含义不明确
  - **反例**: `Map<String, Object> user = new HashMap<>(); user.put("name", "张三");`
  - **正例**: `public record UserDTO(String name) {}`

#### [强制] 九、NativeLoader 统一管理动态库
- `example/onnx/FaceDetectExample.java:30`
  - **问题**: 直接使用 `System.load()` 加载动态库，未通过 NativeLoader 统一管理
  - **反例**: `System.load("/usr/lib/libarcsoft_face.so");`
  - **正例**: `NativeLoader.of("arcsoft").toTarget(modelPath + "/arcsoft").glob("libarcsoft_face*.so").load();`

#### [强制] 十、工具类优先与禁止重复代码
- `example/pipeline/PipelineBasicExample.java:80` 与 `example/pipeline/PipelineRetryExample.java:92`
  - **问题**: 两段完全相同的参数解析逻辑，应提取为工具方法
  - **反例**: 两处各自写 `if (args == null || args.length == 0) ...`
  - **正例**: 提取为 `CommandLine.parse(args)` 公共工具方法后 import 复用

### 体系规范预检

| 文件 | 模块 | 文件名合规 | 类名合规 | main存在 | 无JUnit | 边界合规 | SPI有主Example | System.exit合规 | 资源已关闭 | 无空实现 | 注释完整 | 反射合规 | SPI分发合规 | 测试覆盖 | 测试输出统一 | Java简化 | Lombok配置 | 静态字段安全 | 工具类构造 | 参数校验 | 异常控制流 | 构造器循环 | 泛型安全 | 参数数量 | Javadoc完整 | 面向对象 | SQL文件合并 | iBatis复杂SQL | 生产级代码 | 高级功能Example | 完成即提交 | Example场景完整 | 三方环境完备 | 三方文档必查 | Example复杂度 | 真实测试无Mock |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ExampleA.java | example-starter | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| ExampleB.java | example-starter | ✗ Test结尾 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ 缺校验 | ✓ | ✓ | ✓ | ✗ 7个参数 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ 使用 @MockBean |
| SomeClass.java | utils-support-common | — | — | — | — | ✗ 含 Example 命名 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |

### 建议修复优先级
1. 先修复 [强制] 级别违规
2. 再处理 [推荐] 级别违规
3. 最后优化 [参考] 级别违规
```

## Examples

### 示例 1: 审查单个文件
```
审查 example/onnx/ChatClientExample.java
→ 扫描该类，输出 P3C 违规报告
```

### 示例 2: 批量审查某包
```
扫描 example/pipeline/ 下所有 Example 文件
→ 输出该包内所有文件的 P3C 违规汇总
```

### 示例 3: 体系规范全检
```
检查 example-starter 命名规范
→ 列出所有文件名不以 Example 结尾的文件
→ 列出所有没有 main 方法的独立 Example 文件
→ 列出所有仍在使用 Test/Verify/Diag 命名的文件
→ 列出所有 SPI 适配器缺少对应主 Example 的情况
```

### 示例 4: 跨模块边界扫描
```
扫描 utils-support-common-starter 模块
→ 检查 src/main/java 是否存在 *Example* / *Test* / *Verify* / *Diag* 类
→ 检查 pom.xml 是否引入 junit / mockito / h2 / example-starter 依赖
→ 检查测试是否使用 mock（@Mock/@MockBean/@Spy/when().thenReturn()/verify()）→ [强制] 违规（规约四十）
→ 输出越界违规清单
```

### 示例 5: 综合审查
```
审查 example/pipeline/PipelineBasicExample.java
→ 先执行体系预检（文件名/类名/main/边界）
→ 再执行 P3C 逐维度检查
→ 输出合并报告
```

### 示例 6: 工具复用扫描
```
扫描 spring-support-*-starter 各模块
→ 检查是否重复实现了 Json/PageResult/DateUtils 等工具
→ 检查是否应复用 spring-support-common-starter 的能力
→ 检查是否应复用 utils-support-common-starter 的能力
→ 输出违规清单
```

### 示例 7: 空实现与注释完整性扫描
```
批量审查 example/onnx/ 下所有 Example 文件
→ 检查所有非 Override 方法是否为空实现（{} / return null）
→ 检查所有非 Override 方法/类/属性是否有 Javadoc 或行内注释
→ 输出违规清单，按文件分组
```

### 示例 8: 反射使用与 SPI 分发扫描
```
扫描 spring-support-*-starter 各模块 src/main/java
→ 先评估各反射调用是否无法避免（应优先替换为正常 API / SPI 扩展点）
→ 查找 Class.forName / getMethod / getField / invoke / Proxy.newProxyInstance / Constructor.newInstance
→ 列出所有非 ReflectUtils 中的直接反射调用，标注应替换的 ReflectUtils 方法
→ 查找 instanceof XxxImpl + 强制转换、switch-case 类型路由、if-else 多分支选择实现
→ 列出应改为 ServiceProvider.of(Xxx.class).getExtension(name) 的硬编码分发位置
```

### 示例 9: 测试覆盖与输出目录扫描
```
扫描 utils-support-*-starter 和 spring-support-*-starter 各模块
→ 检查 src/main/java 中有业务逻辑的类，对应 src/test/java 是否有测试
→ 检查测试中是否使用 Files.createTempDirectory() / Paths.get(".") 散落临时文件
→ 检查测试是否使用 Thread.sleep() 等待不确定时长（不可复现）
→ 检查测试是否使用 mock（@Mock/@MockBean/@Spy/when().thenReturn()/verify()）或 pom.xml 引入 mockito/easymock/jmock → [强制] 违规（规约四十）
→ 输出缺失测试和临时文件散落的清单
```

### 示例 10: Java 25+ 简化与 Lombok 配置扫描
```
扫描各模块源码
→ 查找可用 record 替代的 data class（final 字段 + equals/hashCode/toString）
→ 查找可推断类型的 var 使用场景
→ 查找未使用 import static 的全限定名工具方法调用
→ 检查各模块 pom.xml 的 maven-compiler-plugin 是否配置 annotationProcessorPaths
→ 查找手写 equals/hashCode/toString/getter/setter 与 Lombok 注解冲突的代码
→ 输出违规清单
```

### 示例 11: 静态可变字段与工具类构造扫描
```
扫描各模块 src/main/java
→ 查找 static 非 final 的 ArrayList/HashMap/HashSet/StringBuilder/LinkedList 等可变字段
→ 查找纯静态工具类缺少 private 构造函数的情况
→ 标注线程安全风险等级（高：多模块共享；中：同模块内；低：示例代码）
→ 输出违规清单
```

### 示例 12: Java 21+ 现代特性扫描
```
扫描各模块源码
→ 查找可用 switch 表达式替代的 if-else 链（3 个以上分支）
→ 查找多行字符串（SQL/JSON/HTML）未使用 text block 的情况
→ 查找可用 pattern matching instanceof 替代的类型转换
→ 输出改进建议清单
```


### 示例 13: 参数校验、异常控制流与泛型安全扫描
```
扫描各模块 src/main/java
→ 查找 public/protected 方法入口缺少 Objects.requireNonNull 或边界校验的代码
→ 查找 try-catch 中包含正常业务分支（catch 后继续执行）的代码
→ 查找 this()/super() 可能形成调用环的构造器
→ 查找裸类型 List/Map/Set（无泛型参数）的变量声明
→ 查找超过 5 个参数的 public 方法
→ 查找缺少 @param/@return 的 public/protected 方法
→ 输出违规清单，按模块分组
```

### 示例 14: 接口实现真实落地扫描
```
扫描各模块 src/main/java 中实现接口的类
→ 查找实现了某个接口但方法体为空或仅 return null/0 的类
→ 查找仅有 System.out.println 而无实际业务效果的"伪实现"
→ 查找标注了 // TODO 但未完成的接口实现
→ 检查 SPI 接口 (@Spi 注解) 的实现类是否完整实现了所有方法
→ 输出缺失实现的清单，标注应补充的业务效果类型
```

### 示例 15: SQL注入、敏感信息与Spring Bean扫描
```
扫描各模块 src/main/java
→ 查找字符串拼接SQL的代码（+、String.format、concat 含 SELECT/INSERT/UPDATE/DELETE）
→ 查找源码中硬编码的 password/secret/apiKey/token 字面量
→ 查找 implements Serializable 但缺少 serialVersionUID 的类
→ 查找 new XxxService() / new XxxRepository() / new XxxClient() 等手动new Spring Bean的代码
→ 输出违规清单，标注修复方向
```

### 示例 16: 生产级代码、高级功能Example与提交推送扫描
```
扫描各模块 src/main/java
→ 查找 throw new UnsupportedOperationException / 空 catch / // TODO 长期遗留的半成品代码
→ 查找高级功能（有状态/IO/并发/流程/SPI）缺少 example-starter Example 的情况
→ 查找 docs/ 目录中缺少对应功能测试报告 HTML 的高级功能
→ 查找 Example 文件未覆盖边界值/null/异常输入/多实现分支的场景
→ 输出违规清单，按优先级分组（强制 > 推荐）
```

### 示例 17: 三方框架文档合规扫描
```
扫描各模块 src/main/java
→ 查找引入三方依赖（Maven/Gradle）但 Javadoc 无 @see 官方文档链接的类
→ 查找调用三方 API 但无最小可运行示例验证的代码
→ 查找异常处理与官方文档不一致的集成代码
→ 查找 pom.xml 中新引入的依赖无版本兼容性说明
→ 输出违规清单，标注应查阅的官方文档地址
```

### 示例 18: Example 复杂度与逻辑下沉扫描
```
扫描 example-starter 中所有 Example 文件
→ 查找包含超过 3 层嵌套条件分支的 Example
→ 查找定义了 private 辅助方法的 Example
→ 查找有效代码行数超过 200 行的 Example
→ 查找 Example 中有复杂循环编排/数据转换/Pipeline 处理的代码
→ 对每个违规 Example，标注应下沉到源码中的逻辑位置
→ 输出违规清单，按"应新增源码类/应改为 SPI 扩展"分类
```

### 示例 19: 面向对象与过程式代码扫描
```
扫描各模块 src/main/java
→ 查找 public static 方法直接实现业务逻辑（非纯工具方法）的场景
→ 查找数据与行为分离：固定结构业务数据用 Map/独立参数 + 外部 static 方法操作
→ 查找用 static 状态 + 静态方法实现有状态业务流程的代码
→ 检查动态场景豁免：schema 不稳定的 JSON 解析、动态扩展加载可豁免
→ 输出违规清单，按"应提取为对象/应保留静态工具方法"分类
```

### 示例 20: 真实测试扫描（禁止 mock，最重要指标）
```
扫描各模块的测试代码（src/test/java）、example-starter 的 main 自检、以及前后端对接脚本
→ JUnit：查找 Mockito.mock/spy、@Mock/@MockBean/@Spy/@SpyBean、when().thenReturn()、doReturn()、verify()、mockStatic、@WebMvcTest 隔离
→ main 自检：查找为"演示通过"而 fake 真实调用、吞异常后仍 [PASS]、用占位数据冒充真实资源
→ 前后端对接：查找 mockServer/拦截器替换真实后端、前端 mock 整个接口返回假数据
→ pom.xml：查找 mockito / easymock / jmock / mockito-core / mockito-junit-jupiter 依赖（含 test scope）
→ 对每条违规标注应改为"真实测试"的替代方案（真实对象/真实资源/真实断言）
→ 输出违规清单，全部为 [强制] 级
```
## 批量修改安全规范

**严禁使用通配符/正则批量操作修改多个 Java 文件**，原因：Windows 终端编码（GBK/UTF-8 混用）极易导致文件内容损坏。

允许的修改方式：
- 逐个文件使用 `edit` 工具进行精确替换
- 手动确认每个改动后再保存
- 使用 IDE 的全文替换功能（会提示编码问题）

禁止的操作：
- `sed -i 's/old/new/g' *.java`
- `find . -name "*.java" -exec sed -i ... {} +`
- PowerShell `Get-ChildItem *.java | ForEach-Object { (Get-Content $_) -replace 'x','y' | Set-Content $_ }`
- 任何不带编码声明的批量文件操作

每次批量规则变更（如新增命名规约），先对单文件验证修改效果，确认无编码问题后再扩展到同类型文件。

## Key Tips

- **边界优先**：发现非 example-starter 模块中出现 `*Example*`/JUnit 依赖，直接报 **[强制] 体系越界违规**，优先级高于任何 P3C 级别
- **重点优先**：[强制] 级别必须修复，[推荐] 级别建议修复，[参考] 级别可选
- **不要误报**：`assertEquals` 在 Example 自检中是**允许的**（非 JUnit 断言库，而是自定义静态方法）
- **日志 vs 输出**：Example 中使用 `System.out.println` 做结构化输出（如 `[PASS]`/`[FAIL]` 标记）是允许的，但日志细节应用 SLF4J
- **魔法值容忍**：Example 中短小的字符串如 `"all"`、`"sqlite"` 作为 type 参数，提取为常量属于 [推荐] 而非 [强制]；同一魔法值在同一文件中出现超过 5 次升级为 [强制]
- **SPI 适配器豁免**：`*ExampleSpi.java` 是薄包装，主要检查其调用的底层 Example 是否符合规范；每个 Spi 文件必须存在同名主 Example
- **pom.xml 依赖扫描**：审查非 example-starter 模块时，必须检查 pom.xml 中是否意外引入 `junit`/`mockito`/`example-starter` 等演示依赖；`h2` 在 `test` scope 内是允许的
- **单行压缩判定**：一行中出现两个及以上 `;` 语句结尾、或同行堆叠多条 `//` 注释即判定为压缩违规；单个短语句 + 尾随单条注释不判违规
- **MyBatis 扫描范围**：只要模块 classpath 中存在 MyBatis 依赖（含 mybatis-plus），其 src/main/java 即纳入注解 SQL 扫描
- **工具复用判定（核心原则：能复用的必须复用，禁止重复造轮子）**：
  - `utils-support-common-starter` 中已有实现（无论简单或复杂）在其他模块中被重新实现 → **[强制] 重复造轮子**，必须改为 import 复用
  - `spring-support-common-starter` 中已有实现（无论简单或复杂）在 spring-* 模块中被重新实现 → **[强制] 未复用**，必须改为 import 复用
  - 同一个工具类在两个或以上模块中各自有一份实现（如 `Json.java` 在 3 个不同包中）→ **[强制] 工具类重复**，统一收敛
  - **禁止冗余和重复功能**：同一段逻辑、同一种能力在两个及以上文件/模块中出现 → **[强制] 违规**，必须收敛到 `utils-support-common-starter` 或 `spring-support-common-starter`
- **层级合规判定（强制，5 条铁律）**：
  - `spring-support-api-starter` 必须复用 `spring-support-parent-starter` 的功能和代码，禁止自己重复实现 → [强制]
  - `spring-support-parent-starter` 必须复用 `utils-support-parent-starter` 的功能和代码，禁止自己重复实现 → [强制]
  - `spring-support-parent-starter` 其它模块必须复用 `spring-support-common-starter` 的功能和代码，不得从其他同级 starter 模块导入工具类 → [强制]
  - `utils-support-parent-starter` 其它模块必须复用 `utils-support-common-starter`（JDK 工具）的功能和代码，不得从其他同级 starter 模块导入工具类 → [强制]
  - **必须复用功能和代码，不允许冗余**：同一功能/同一逻辑在两个及以上模块/文件中出现（私有方法除外）→ [强制] 违规；若 common 功能不完善，必须先完善 `spring-support-common-starter` / `utils-support-common-starter`，再让各模块 import 复用，禁止在业务模块中各自实现
  - 非 `utils-support-parent-starter` 的外部项目，应通过 parent-starter 统一获取工具依赖，而非直接引用某个子 starter 模块的工具类 → [推荐] 层级违规
- **base 类豁免**：`BaseExample`、`SimpleEngineDataSourceExample` 等纯基类/抽象类不强制要求 main 方法
- **System.exit 位置**：仅在 `main` 方法本体中允许 `System.exit()`；工具方法、回调方法、内部 helper 方法中禁止使用
- **空实现判定**：方法体只有 `{ }`、`return null;`、`return 0;`、`return Collections.emptyList();`、`return Collections.emptyMap();`、`throw new UnsupportedOperationException();` 视为空实现，全部 [强制] 违规；`@Override` 方法和方法签名本身为空但实现由父类/接口提供的不计入
- **注释完整性判定**：非 `@Override` 方法必须有 `/** */` Javadoc 说明用途、参数、返回值；类必须有完整 Javadoc；字段（属性）必须有一行注释说明含义；`// TODO` 仅作为临时占位，不替代正式注释
- **批量修改安全**：禁止对多个文件使用通配符批量替换（如 `sed -i`、`find + xargs`、全局正则替换），必须逐个文件操作，防止编码损坏和误改；批量变更通过脚本逐个处理而非命令行通配符
- **反射唯一入口**：除 `ReflectUtils.java` 自身外，任何业务代码中出现 `Class.forName`、`.getMethod()`、`.getField()`、`.invoke()`、`Proxy.newProxyInstance`、`Constructor.newInstance`、`LambdaMetafactory` 均为 [强制] 违规；**反射应尽量避免**，优先通过正常 API / 构造器 / SPI 扩展点实现；确需反射时一律经 `ReflectUtils`
- **Map 使用判定**：有固定结构的业务数据用 `Map<String, Object>` 承载 → [强制] 违规；必须提取为 `record` 或 `@Data` 类；动态场景必须用 Map 时，`new HashMap<>()` 须显式指定初始容量
- **NativeLoader 判定**：业务代码中使用 `System.load()` / `System.loadLibrary()` 直接加载动态库 → [强制] 违规；必须通过 `NativeLoader.of(taskId).toTarget(dir).glob(pattern).load()` 统一管理；越界加载（native-parent 无对应平台库）→ [强制] 环境缺失违规
- **重复代码判定**：同一段逻辑在 2 个及以上文件中出现 → [强制] 违规；必须提取为工具类并 import 复用；工具类归属原则：纯 JDK → `utils-support-common-starter`，Spring → `spring-support-common-starter`
- **SPI分发判定**：`instanceof XxxImpl` + 强制转换后调用方法、`switch(type)` + `case "implA": return new ImplA()`、`if ("gson".equals(x))` 多分支选择实现 → [强制] 违规；应改为 `ServiceProvider.of(XxxService.class).getExtension(name)`（单例）或 `.getNewExtension(name, args)`（新建）；单实现的类型安全判断（如 `if (obj instanceof Foo) return obj;` 非路由分发）不违规
- **SPI 定义明确**：本体系中「SPI」特指 `com.chua.common.support.spi.ServiceProvider`（位于 `utils-support-common-starter`），不是 Java 标准 `ServiceLoader`；检查方式：搜索 `ServiceProvider.of(`、`@Spi`、`@Extension`、`@SpiOrder`、`@SpiDescribe` 注解；正确用法示例：`ServiceProvider.of(DataProcessor.class).getExtension("json")`
- **SPI 实现注解判定**：实现了 SPI 接口的类必须有 `@Spi` 注解，否则 ServiceProvider 无法扫描到；`@Spi` 支持多值声明扩展名，如 `@Spi({"json", "application/json"})`
- **真实测试判定（最重要指标，规约四十）**：测试不论 JUnit 单测、`main` 自检还是前后端对接，发现 `mockito`、`@Mock`、`@MockBean`、`@Spy`、`@SpyBean`、`mock(`、`spy(`、`when(...).thenReturn(...)`、`doReturn(...)`、`doNothing().when(...)`、`verify(...)`、`mockStatic`、`mockConstruction`、`@WebMvcTest`/`@MockBean`（Spring 上下文隔离）→ **[强制] 违规**；pom.xml 引入 mockito/easymock/jmock（含 test scope）→ **[强制] 违规**；测试必须构造真实对象、连接真实资源、断言真实结果
- **测试覆盖判定**：`src/main/java` 有业务逻辑代码的模块，`src/test/java` 必须有对应测试类；测试需覆盖：正常输入、边界值、空值/null、异常输入、**所有 SPI 实现分支**（每个 @Spi 声明的扩展名都应有对应测试）；测试中创建临时文件/目录必须输出到统一目录（`test-output/`），禁止散落到源码树或系统临时目录
- **测试路径裁决（2026-08-25 已裁决）**：非 example-starter 模块的 pom **一律不引入 JUnit（含 test scope）**；规约六已内联测试覆盖等价方式说明，不再重复。发现模块 pom 残留 junit/mockito 依赖 → 报 **[强制] 边界违规**（test scope 亦不允许，h2 除外）
- **测试可复现判定**：禁止 `Thread.sleep()` 等待不确定时长，改用 `CountDownLatch` 或轮询；禁止依赖文件系统随机状态；依赖网络/数据库等外部服务时**必须使用真实测试环境**（测试库、沙箱 API、本地真实服务或内嵌 H2 内存库等真实实现），**禁止 mock 替代**（见规约四十）
- **Java 25+ 简化判定**：纯数据类（DTO/VO/参数对象）使用 `class` + Lombok 而可用 `record` 替代 → [强制]；局部变量类型可推断却显式写出 → [强制]；静态工具方法未用 `import static` 而用全限定名 → [强制]
- **Lombok 配置判定**：模块 pom.xml 的 `maven-compiler-plugin` 缺少 `annotationProcessorPaths` 配置 Lombok → [强制] 违规（Java 25+ 必需）；手写 `equals/hashCode/toString/getter/setter` 与 Lombok 注解冲突 → [强制]
- **静态可变字段判定**：`static` + 非 `final` 的 `ArrayList`/`HashMap`/`HashSet`/`StringBuilder` 等可变对象 → [强制] 线程安全风险；应改为 `static final` + 不可变包装或 `ConcurrentHashMap`，或降级为实例字段
- **工具类构造判定**：纯静态方法类（无实例字段、无实例方法）缺少 `private ClassName() {}` 私有构造 → [强制] 违规；可被意外 `new` 实例化
- **参数校验前置判定**：public/protected 方法入口没有 `Objects.requireNonNull` / 边界校验就进入业务逻辑 → [强制] 违规
- **异常控制流判定**：try-catch 块中包含正常业务分支（catch 后继续执行主逻辑）、或故意 `throw new XxxException()` 做流程控制 → [强制] 违规
- **构造器循环判定**：`this(...)` 或 `super(...)` 调用形成调用环 → [强制] 违规
- **泛型裸类型判定**：`List`、`Map`、`Set` 等未指定类型参数的裸类型用于变量声明或字段 → [强制] 违规；`var` 推断例外
- **方法参数数量判定**：public 方法超过 4 个参数且未提取为 record 参数对象 → [强制] 违规
- **Javadoc 参数完整判定**：public/protected 方法缺少 `@param` 或 `@return` 标签，或参数名与实际签名不一致 → [推荐] 违规
- **接口实现真实落地判定**：SPI 接口或业务接口的实现类中，存在方法体仅为 `{ }`、`return null/0/false`、`System.out.println` 无后续处理 → [强制] 违规；所有接口方法必须产生可观测的业务效果（持久化/网络/状态变更/真实计算）；TODO 注释标注未完成不是合法状态，必须完成或移除该实现类
- **SQL拼接判定**：代码中出现字符串拼接构建SQL语句（`+` 拼接 `SELECT`/`INSERT`/`UPDATE`/`DELETE`、`String.format` 带 SQL 关键字）→ [强制] 违规；必须改用 `#{param}` 占位符或条件构造器
- **敏感信息判定**：源码中出现 `password=`、`secret=`、`apiKey=`、`token=` 等字面量赋值（非 `${...}` 占位符、非注释、非 test 代码）→ [强制] 违规
- **serialVersionUID判定**：类实现了 `Serializable` 接口但未声明 `private static final long serialVersionUID` 字段 → [强制] 违规
- **Spring Bean new判定**：业务代码中使用 `new` 创建标注了 Spring 注解（`@Service`/`@Repository`/`@Component` 等）或属于 Spring 容器管理的类实例 → [强制] 违规；应通过构造器注入获取
- **生产级代码判定**：代码中存在 `throw new UnsupportedOperationException("TODO")`、空 `catch(Exception e) {}`、`// TODO` 长期遗留、仅覆盖 Happy Path 的 API → [强制] 违规；每个 public API 必须满足6项生产级检查清单
- **高级功能判定**：有状态/涉及I/O/涉及并发/涉及配置/涉及SPI扩展/涉及业务流程的功能属于高级功能，必须有 example-starter Example + docs/ 测试报告 HTML；纯静态工具类豁免
- **提交推送判定**：功能实现+测试通过后超过 1 小时未 commit+push → [强制] 违规；多个不相关功能合并到同一个 commit → [推荐] 违规
- **Example场景完整性判定**：Example 文件未覆盖边界值/null/异常输入/多实现分支/并发场景/资源清理中的任一类别 → [强制] 违规
- **三方环境判定**：utils-support-parent-starter 中实现 native/资源/AI 模型功能时，对应环境仓库（native-parent/resource-parent/models-parent）缺少源码、动态库或模型文件 → [强制] 违规；功能开发必须遵循"先环境后实现"原则
- **三方文档必查判定**：代码中调用了三方 API/SDK/框架方法，但 Javadoc 中无官方文档链接（`@see <url>`）→ [强制] 违规；引入新 Maven 依赖前未阅读官方文档确认版本兼容性 → [强制] 违规
- **MyBatis SQL 判定**：含 MyBatis 依赖的模块中，接口方法上使用 `@Select`/`@Insert`/`@Update`/`@Delete`/`@SelectProvider` 等注解编写 SQL，或未将 SQL 写入对应 `*Mapper.xml` → [强制] 违规；动态 SQL 必须使用 XML 标签，禁止 `@Select("<script>...")` 形式
- **SQL 初始化文件合并判定（三十八）**：spring-support-*-starter 模块中存在多个 schema/*.sql 或 data/*.sql 文件 → [强制] 违规；存在非 init-schema/init-data 命名的散落 SQL 文件 → [强制] 违规；src/test/resources/ 下测试 SQL 豁免；Flyway/Liquibase 迁移脚本由 spring-support-datasource-starter 统一管理，业务模块不得直接持有
- **Example复杂度判定**：Example 文件中存在超过 3 层嵌套分支、复杂循环编排、大量 private 辅助方法、或有效代码超过 200 行 → [强制] 违规；应将复杂逻辑下沉到正式服务类/SPI 扩展点中，Example 只保留参数解析→API 调用→断言的线性流程
- **Java 21+ 特性提示**：多分支 switch 建议用 switch 表达式；多行字符串（SQL/JSON/HTML）必须用 text block；有限类型体系优先用 sealed class
- **面向对象判定（三十三）**：业务逻辑必须通过对象封装（封装字段+行为方法），多实现走接口+SPI 多态，禁止大段 public static 方法串行实现业务流程；运行时 schema 不稳定的动态数据/动态扩展加载场景可豁免 OOP 强制
- **BigDecimal 精确判定（三十四）**：金额/单价/比例计算使用 `double`/`float` → [强制] 违规；`new BigDecimal(double)` 构造 → [强制] 违规（用 `BigDecimal.valueOf` 或字符串构造）；除法未指定 `scale` + `RoundingMode` → [强制] 违规
- **ThreadLocal remove 判定（三十五）**：`ThreadLocal.set()` 后没有对应的 `remove()`（在 finally 中）→ [强制] 违规；线程池环境下未清理 ThreadLocal 值 → [强制] 违规
- **禁止裸 return null 判定（三十六）**：public/protected 方法返回集合类型时 return null → [强制] 违规（必须返回空集合）；返回单个对象可能缺失时 return null 且无 Optional 包裹 → [强制] 违规；返回数组时 return null → [强制] 违规（返回空数组或用 Optional）
- **常量接口判定（三十七）**：`interface XxxConstants` / `interface XxxConstant` 定义常量（public static final 成员）→ [强制] 违规，改用 final class + private 构造；`implements XxxConstants` 后直接引用常量 → [强制] 违规
- **iBatis/MyBatis 复杂 SQL 判定（三十九）**：存在 `@SelectProvider` / `@InsertProvider` / `@UpdateProvider` / `@DeleteProvider` 注解配合 Provider 类内联构建复杂 SQL → [强制] 违规；使用 lambda 表达式内联 SQL 构建逻辑 → [强制] 违规；复杂 SQL（动态条件/多表 JOIN/子查询/超5行）必须写入 `*Mapper.xml`，Mapper 接口仅保留方法签名
