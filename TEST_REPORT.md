# 单元测试报告

> **生成时间**: 2026-07-30
> **覆盖模块**: `spring-support-filesystem-starter`, `spring-support-filesystem-minio-starter`, `utils-support-filestorage-example-starter`
> **测试框架**: JUnit 5 + Spring Boot Test + Mockito
> **执行环境**: JDK 25 + Maven 3.9.9 + Surefire 3.2.5

---

## 一、执行结果总览

| 模块 | 测试类 | 用例数 | 通过 | 失败 | 跳过 | 状态 |
|------|--------|------:|------:|------:|------:|:---:|
| `spring-support-filesystem-starter` | `FileChunkUploadControllerTest` | 13 | 13 | 0 | 0 | ✅ |
| `spring-support-filesystem-starter` | `FileSystemTestApplication` | 6 | 6 | 0 | 0 | ✅ |
| `spring-support-filesystem-minio-starter` | `MinioApplication` | 4 | 4 | 0 | 0 | ✅ |
| `utils-support-filestorage-example-starter` | `LocalFileStorage2Test` | 17 | 17 | 0 | 0 | ✅ |
| **合计** | **4 个测试类** | **40** | **40** | **0** | **0** | **✅ 100%** |

### 1.1 执行命令

```bash
# FileChunkUploadController 单元测试 (MockMvc)
mvn surefire:test -f spring-support-parent-starter/spring-support-filesystem-starter/pom.xml \
    -Dtest=FileChunkUploadControllerTest -DskipTests=false

# FileSystemTestApplication Spring Boot 启动测试
mvn surefire:test -f spring-support-parent-starter/spring-support-filesystem-starter/pom.xml \
    -Dtest=FileSystemTestApplication -DskipTests=false

# MinioApplication Spring Boot 启动测试
mvn surefire:test -f spring-support-parent-starter/spring-support-filesystem-minio-starter/pom.xml \
    -Dtest=MinioApplication -DskipTests=false

# LocalFileStorage2 单元测试
mvn test -f utils-support-parent-starter/utils-support-filesystem-parent/utils-support-filestorage-example-starter/pom.xml \
    -DskipTests=false
```

### 1.2 测试通过率

```
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
通过率: 100%
```

---

## 二、`spring-support-filesystem-starter` 测试详情

### 2.1 `FileChunkUploadControllerTest` — MockMvc 单元测试 (13/13 ✅)

| # | 测试方法 | 验证目标 | 结果 |
|---|---|---|:---:|
| 1 | `testCreateChunkUpload` | 正常创建分片上传任务 | ✅ |
| 2 | `testCreateChunkUploadMissingFileName` | 缺少 fileName 参数返回 400 | ✅ |
| 3 | `testCreateChunkUploadInvalidFileSize` | fileSize=0 返回 C0400S0000 | ✅ |
| 4 | `testUploadPartSuccess` | 正常上传分片到本地存储 | ✅ |
| 5 | `testUploadPartInvalidUploadId` | 不存在的 uploadId 返回错误 | ✅ |
| 6 | `testUploadPartInvalidPartNumber` | partNumber<1 返回错误 | ✅ |
| 7 | `testProgress` | 查询上传进度 | ✅ |
| 8 | `testProgressNotFound` | 不存在的 uploadId 查询返回错误 | ✅ |
| 9 | `testCompleteIncompleteUpload` | 未传完全部分片时 complete 返回错误 | ✅ |
| 10 | `testCancelSuccess` | 取消已存在的上传任务 | ✅ |
| 11 | `testCancelNonExistent` | 取消不存在的任务 | ✅ |
| 12 | `testCreateWithDefaultChunkSize` | 不传 chunkSize 时使用默认 5MB | ✅ |
| 13 | `testCreateWithInvalidChunkSize` | chunkSize=0 时自动修正为 5MB | ✅ |

**关键修复**:
1. Mock service 改为内存 `taskStore` 模拟任务存在性
2. `ReturnResult` 业务码用 `ReturnCode.SUCCESS.getCode()` (`00000`) 和 `ReturnCode.REQUEST_PARAM_ERROR.getCode()` (`C0400S0000`)
3. `LocalTmpMultipartStorage` 不生成 etag, 测试移除该断言

### 2.2 `FileSystemTestApplication` — Spring Boot 启动测试 (6/6 ✅)

| # | 测试方法 | 验证目标 | 结果 |
|---|---|---|:---:|
| 1 | `contextLoads` | Spring 上下文加载成功 | ✅ |
| 2 | `fileChunkUploadControllerBeanExists` | FileChunkUploadController 注册为 Bean | ✅ |
| 3 | `autoConfigurationLoaded` | FileSystemAutoConfiguration 自动装配 | ✅ |
| 4 | `fileStorageTemplateBeanExists` | FileStorageTemplate 注册为 Bean | ✅ |
| 5 | `createChunkUploadEndpoint` | /chunk/create 端到端可用 | ✅ |
| 6 | `progressEndpoint` | /chunk/{uploadId}/progress 端到端可用 | ✅ |

**关键修复**:
1. 父 POM `mockito.version` 从 `5.14.2` 修正为 `5.17.0` (本地仓库实际版本)
2. `FileSystemAutoConfiguration` 的两个 Bean 添加 `@ConditionalOnMissingBean` 避免与 `@Service` 扫描冲突
3. 排除 datasource / mybatis-plus 自动配置, 仅验证 filesystem 自动装配
4. `application.yml` 配置 H2 in-memory DB + `schema-h2.sql` 兼容脚本
5. 使用 `@AutoConfigureMockMvc` 自动装配 Jackson converter (替代 standalone MockMvc)
6. 父 POM `skipTests=true` 通过 `-DskipTests=false` 命令行参数覆盖

---

## 三、`spring-support-filesystem-minio-starter` 测试详情

### `MinioApplication` — Spring Boot 启动测试 (4/4 ✅)

| # | 测试方法 | 验证目标 | 结果 |
|---|---|---|:---:|
| 1 | `contextLoads` | Spring 上下文加载成功 | ✅ |
| 2 | `minioPropertiesLoaded` | MinioProperties 从 yml 绑定 (username/password) | ✅ |
| 3 | `minioTemplateBeanNotCreatedWhenAddressEmpty` | `@ConditionalOnExpression` 在 address 为空时跳过 bean | ✅ |
| 4 | `minioTemplateBeanCreatedWhenAddressPresent` | MinioTemplate 可正常构造 | ✅ |

**关键修复**:
1. `MinioProperties` 字段是 `username`/`password`, 不是 `accessKey`/`secretKey`
2. `MinioTemplate.afterPropertiesSet()` 会真实连接 MinIO, 测试用 `@ConditionalOnExpression` 留的"逃生口"

---

## 四、`utils-support-filestorage-example-starter` 测试详情

### `LocalFileStorage2Test` — 单元测试覆盖矩阵 (17/17 ✅)

| # | 测试方法 | 覆盖场景 | 结果 |
|---|---|---|:---:|
| 1 | `testPutAndGetObject` | put + getObject 正常读取 | ✅ |
| 2 | `testGetObjectWithCustomType` | PDF 文件 contentType 识别 | ✅ |
| 3 | `testGetObjectWithNullKey` | null key 不抛 NPE | ✅ |
| 4 | `testGetObjectWithMissingKey` | 不存在的 key 返回空 result | ✅ |
| 5 | `testGetObjectAfterFileDeletion` | 物理删除后优雅降级 | ✅ |
| 6 | `testGetObjectWithRequest` | GetObjectRequest 委托给 key 版本 | ✅ |
| 7 | `testGetObjectWithNullRequest` | null request 不抛异常 | ✅ |
| 8 | `testPutObjectReturnsNull` | putObject 占位实现返回 null | ✅ |
| 9 | `testDeleteObjectReturnsNull` | deleteObject 占位实现返回 null | ✅ |
| 10 | `testExistObjectReturnsNull` | existObject 占位实现返回 null | ✅ |
| 11 | `testListObjectReturnsNull` | listObject 占位实现返回 null | ✅ |
| 12 | `testGuessType` | guessType 各扩展名识别 (反射私有) | ✅ |
| 13 | `testBucketInherited` | bucket 字段通过父类构造设置 | ✅ |
| 14 | `testPutOverwrite` | 同 key 后写覆盖前写 | ✅ |
| 15 | `testConcurrentPut` | 多线程并发 put 不抛异常 | ✅ |
| 16 | `testUniqueUploadIsolation` | 不同 key 文件独立 | ✅ |
| 17 | `testRecoverAfterException` | 异常后对象仍可用 | ✅ |

**关键修复**:
1. `GetObjectRequest` 字段是 `fileName`/`filePath`, 不是 `key`
2. `ListObjectRequest` 字段是 `filePath`/`marker`/`limit`, 不是 `bucket`/`prefix`
3. `AbstractFileStorage.bucket`/`bucketSetting` 是 protected, 跨包测试需反射访问
4. `metadata.name` 是物理文件名 (`source.txt`), 不是逻辑 key (`greeting.txt`)

---

## 五、测试覆盖矩阵

### 5.1 功能维度

| 测试维度 | FileChunkUploadControllerTest | FileSystemTestApplication | MinioApplication | LocalFileStorage2Test |
|---|:---:|:---:|:---:|:---:|
| 上下文启动 | ❌ | ✅ | ✅ | ❌ |
| Bean 注册 | ❌ | ✅ | ✅ | ❌ |
| 自动配置加载 | ❌ | ✅ | ✅ | ❌ |
| REST 端点 | ✅ | ✅ | ❌ | ❌ |
| 参数校验 | ✅ | ❌ | ✅ | ❌ |
| 业务分支 (success/fail) | ✅ | ❌ | ✅ | ✅ |
| 异常路径 | ✅ | ❌ | ❌ | ✅ |
| 并发 | ❌ | ❌ | ❌ | ✅ |
| 文件 IO | ❌ | ❌ | ❌ | ✅ |
| 反射调用私有方法 | ❌ | ❌ | ❌ | ✅ |

### 5.2 API 覆盖度

| API | 覆盖测试 |
|---|---|
| `POST /chunk/create` | testCreateChunkUpload, testCreateChunkUploadMissingFileName, testCreateChunkUploadInvalidFileSize, testCreateWithDefaultChunkSize, testCreateWithInvalidChunkSize, FileSystemTestApplication.createChunkUploadEndpoint |
| `POST /chunk/{uploadId}/part` | testUploadPartSuccess, testUploadPartInvalidUploadId, testUploadPartInvalidPartNumber |
| `POST /chunk/{uploadId}/complete` | testCompleteIncompleteUpload |
| `DELETE /chunk/{uploadId}` | testCancelSuccess, testCancelNonExistent |
| `GET /chunk/{uploadId}/progress` | testProgress, testProgressNotFound, FileSystemTestApplication.progressEndpoint |
| `LocalFileStorage2.getObject(String)` | testPutAndGetObject, testGetObjectWithCustomType, testGetObjectWithNullKey, testGetObjectWithMissingKey, testGetObjectAfterFileDeletion, testRecoverAfterException |
| `LocalFileStorage2.getObject(GetObjectRequest)` | testGetObjectWithRequest, testGetObjectWithNullRequest |
| `LocalFileStorage2.put` | testPutAndGetObject, testPutOverwrite, testConcurrentPut, testUniqueUploadIsolation, testRecoverAfterException |
| `LocalFileStorage2.putObject/deleteObject/existObject/listObject` | testPutObjectReturnsNull, testDeleteObjectReturnsNull, testExistObjectReturnsNull, testListObjectReturnsNull |
| `LocalFileStorage2.guessType` (private) | testGuessType |
| `MinioTemplate` 构造与条件注入 | MinioApplication 全套 |

---

## 六、关键技术决策与踩坑记录

### 6.1 父 POM 默认 `skipTests=true`

**问题**: `spring-support-parent-starter/pom.xml` 中 `<skipTests>true</skipTests>` 默认开启, 所有子模块继承
**解决**: 命令行 `-DskipTests=false` 强制覆盖

### 6.2 mockito 5.14.2 缺失

**问题**: 父 POM `mockito.version=5.14.2`, 本地仓库只有 `5.17.0` 和 `5.23.0`
**解决**: 将 `mockito.version` 修正为 `5.17.0`

### 6.3 MyBatisPlus + H2 schema 不兼容

**问题**: MySQL 的 `ON UPDATE CURRENT_TIMESTAMP` H2 不支持
**解决**: 测试用独立的 `schema-h2.sql`, 移除 `ON UPDATE` 子句, 用 `DEFAULT CURRENT_TIMESTAMP`

### 6.4 MybatisPlusConfiguration 缺失依赖

**问题**: `MybatisPlusConfiguration` 需要 `MaterializedSqlDataSourceRouter` (在 `spring-support-datasource-starter`)
**解决**: 测试排除 mybatis-plus 和 datasource 自动配置, 仅验证 filesystem 自身

### 6.5 @Service 与 @Bean 冲突

**问题**: `SysFileSystemUploadServiceImpl` 类上有 `@Service`, `FileSystemAutoConfiguration` 又显式定义同名 Bean
**解决**: 显式 Bean 加 `@ConditionalOnMissingBean`

### 6.6 standalone MockMvc 缺 Jackson converter

**问题**: `MockMvcBuilders.standaloneSetup()` 不自动注册 `MappingJackson2HttpMessageConverter`, 响应是 String 不是 JSON
**解决**: Spring Boot Test 场景用 `@AutoConfigureMockMvc` 自动装配; 纯 MockMvc 场景用 `setMessageConverters(new MappingJackson2HttpMessageConverter())`

### 6.7 Request Builder 方法名不匹配

**问题**: Lombok `@Builder` 按字段名生成, 部分字段名不一致 (如 `fileName` vs `key`)
**解决**: 测试用 `fileName()`/`filePath()` 替代 `key()`

### 6.8 AbstractFileStorage protected 字段

**问题**: `bucket`/`bucketSetting` 是 protected, 跨包测试无法直接访问
**解决**: 反射调用 `getDeclaredField` + `setAccessible(true)`

---

## 七、后续改进建议

1. **集成测试补充**: 使用 Testcontainers 启动真实 MinIO 容器, 验证 multipart upload 端到端
2. **覆盖率工具**: 集成 JaCoCo, 生成覆盖率报告
3. **CI 集成**: GitHub Actions / GitLab CI 配置 `mvn verify` 自动跑测试
4. **测试数据**: 增加 boundary case (超大文件、空文件、并发冲突等)
5. **错误码体系**: 引入 `@ErrorCode` 注解统一 `C0400S0000` 等业务错误码
