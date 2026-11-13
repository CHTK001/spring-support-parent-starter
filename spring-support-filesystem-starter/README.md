# spring-support-filesystem-starter

文件存储模块（Spring Boot Starter）：提供统一对象存储抽象（多 Provider）与内置 HTTP 文件服务器，
覆盖上传、回显、下载、预览、目录浏览与任务化分片上传。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| 统一存储模板 | `FileStorageTemplate` | 注入 Bean 调用 |
| 多 Provider 路由 | `FileStorageProperties#providers` | `plugin.filesystem.providers` |
| 单文件上传 | `FileObjectController#upload` | `PUT/POST /v2/file/upload` |
| 匿名回显 | `FileObjectController#view` | `GET /v2/file/view/**` |
| 任务化分片上传 | `UploadTaskManager` / `UploadTaskController` | `/v2/file/upload-task/**` |
| 内置文件服务器 | `FileStorageEmbeddedFilter` | 应用端口 + `/fs/{bucket}/{key}` |
| 独立文件服务器 | `FileStorageServerLauncher` | `http://host:18082/{bucket}/{key}` |
| 本机目录浏览 | `FileDirController` | `GET /v2/file/dir/list` |
| 服务器配置下发 | `FileServerConfigController` | `GET /v2/file/server-config` |
| 协同文档 | `FileCollabStore` / `FileCollabHub` / `FileCollabController` | `/v2/file/collab/**` |
| 私有桶鉴权 | `OAuthServerFilter`（oauth 模块，反射装配） | 请求令牌 |
| 图片滤镜 SPI | `FileStorageImageFilterProvider` | `storage-server.image-filter` |
| 服务信息登记 | `fileSystemServiceInfoContributor` | 控制台模块能力 |

补充能力：预览（图片/文本/PDF/Office/代码/压缩包）；目录列表页（`SIMPLE`/`SYSTEM`）、
Range 下载、水印响应头；上传任务由 common-starter 时间轮 + 虚拟线程合并；
私有桶可在管理页 `setConfig` 热更新，重启回到配置默认。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-filesystem-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 版本 | 作用 |
|:-----|:-----|:-----|
| `utils-support-filestorage-starter` | 4.0.0.50 | `FileStorage` SPI 与各存储实现 |
| `utils-support-resource-filestorage` | 4.0.0.43 | 文件预览前端资源（file-viewer） |
| `utils-support-ssh-starter` | 4.0.0.50 | 远程文件读取（SFTP 预览） |
| `utils-support-cad-starter` | 4.0.0.50 | CAD 图纸预览（DXF 渲染 / DWG 头信息） |
| `commons-compress` | 1.28.0 | 压缩包预览与解压 |
| `spring-boot-starter-web` | 父 pom 管理 | 控制器与文件服务器过滤器 |
| `spring-support-common-starter` | 父 pom 管理 | 服务信息、模块日志 |
| `spring-support-mybatis-starter` | 父 pom 管理 | 上传任务相关持久化基础设施 |
| `spring-support-datasource-starter` | 父 pom 管理 | 数据源路由 |
| `spring-jdbc` | 父 pom 管理 | 上传任务 MD5 落库（可选降级） |
| `spring-support-swagger-starter` | provided | 接口文档注解 |

模块引入了 MyBatis / 数据源 starter，但上传任务仓储在没有 `JdbcTemplate` 时会降级为不落库，
存储主体本身不依赖数据库。

## 三、配置项

### 3.1 总开关与通用配置（`plugin.filesystem`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.filesystem.enable` | `false` | 总开关；**必须显式设为 true**，否则整个自动配置不生效 |
| `plugin.filesystem.default-provider` | 无 | 默认 Provider 名称；为空时取第一个注册成功的 |
| `plugin.filesystem.mode` | `BOTH` | `VIEW` 仅预览 / `DOWNLOAD` 仅下载 / `BOTH` |
| `plugin.filesystem.dir-list` | `false` | 是否开放本机目录浏览接口 |
| `plugin.filesystem.url-suffix` | 无 | 生成访问地址时拼接的后缀 |
| `plugin.filesystem.open-webjars` | `true` | webjars 静态资源 |
| `plugin.filesystem.open-remote-file` | `false` | 是否允许远程文件透传 |
| `plugin.filesystem.temp-path` | 系统临时目录 | 分片上传临时目录 |
| `plugin.filesystem.task-expire-minutes` | `60` | 无分片活动的任务过期回收时间 |
| `plugin.filesystem.task-sync-max-mb` | `5` | 小文件同步直传阈值；`0` 表示全部走任务 |

### 3.2 文件服务器（`plugin.filesystem.storage-server`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `enable` | `true` | 是否启用文件服务器 |
| `mode` | `EMBEDDED` | `embedded` 挂应用端口 / `standalone` 独立端口 |
| `path-prefix` | `/fs` | 内置形态路径前缀；`/` 或空串表示根路径 |
| `host` | `0.0.0.0` | 监听地址（仅 standalone） |
| `port` | `18082` | 监听端口（仅 standalone） |
| `public-host` | 回退 `host` | 对外可达地址，`0.0.0.0` 归一为 `127.0.0.1` |
| `type` | `jdk` | 服务器引擎 `jdk` / `nio` / `aio`（仅 standalone） |
| `open-preview` | `true` | 允许 `?preview` 内联预览 |
| `open-download` | `true` | 允许 `?download` 附件下载 |
| `open-range` | `true` | 允许 Range 分段下载 |
| `open-watermark` | `false` | 附加水印响应头 |
| `watermark` | 无 | 水印文本 |
| `layout` | `true` | 目录列表页 |
| `layout-type` | `SIMPLE` | `SIMPLE` 简单列表 / `SYSTEM` 资源管理器 |
| `image-filter` | `NONE` | 图片滤镜 SPI 名称 |
| `image-filter-enabled` | `false` | 是否启用全局滤镜链 |
| `filter-chain` | 无 | 紧凑滤镜链 `id:key=value;id2` |
| `file-setting-key` | `jdk` | URL 参数能力实现名 |
| `open-path-guard` | `true` | 路径穿越防护 |
| `open-remote-file` | `false` | 远程文件透传 |
| `third-url-preview` | `NONE` | 三方 URL 预览 SPI 名（内置 `SERVER`） |
| `private-buckets` | 无 | 私有桶（provider 名，逗号分隔；`*` 表示全部） |
| `frame-ancestors` | `*` | CSP `frame-ancestors`，同时关闭 X-Frame-Options |
| `cors.enable` | `true` | 跨域开关 |
| `cors.allowed-origins` / `allow-credentials` | `*` / `true` | 允许来源与凭据 |
| `cors.allowed-headers` / `expose-headers` | 见源码 | 已含 `x-oauth-token`、`X-Trace-Id` 等头 |

访问路径：

- embedded：`http://应用host:应用端口/fs/{bucket}/{key}`；
- standalone：`http://host:18082/{bucket}/{key}`（无前缀）。

> 另有一套历史 `plugin.filesystem.servers`（`FileServerManager`，默认空列表），
> 与 `storage-server` 相互独立，仅用于兼容旧配置。

### 3.3 存储 Provider（`plugin.filesystem.providers`）

每个 key 是一个 Provider 名称，对应一个 `FileStorage` SPI 实例：

```yaml
plugin:
  filesystem:
    providers:
      local:
        bucket: system
        type: local
        base-path: ./oss
      minio:
        bucket: backup
        type: minio
        endpoint: http://172.16.0.40:9000
        access-key: minioadmin
        secret-key: minioadmin
        region: us-east-1
        connection-timeout: 10000
        read-timeout: 30000
        write-timeout: 30000
        secure: false
```

`type` 推断规则（未显式指定时）：

| 条件 | 推断结果 |
|:-----|:---------|
| `endpoint` 为空 | `local` |
| endpoint 含 `oss.aliyuncs` | `oss` |
| endpoint 含 `myhuaweicloud` 或 `obs.` | `obs` |
| endpoint 含 `cos.` 或 `tencentyun` | `cos` |
| endpoint 含 `amazonaws` | `s3` |
| 其它非空 endpoint | `minio` |

### 3.4 上传（`plugin.filesystem.upload`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `max-size-mb` | `10` | 单文件大小上限（MB） |
| `allowed-extensions` | `jpg,jpeg,png,gif,bmp,webp` | 扩展名白名单，默认只放行图片 |
| `chunk-size` | `5242880` | 分片大小（字节） |
| `temp-dir` | `${java.io.tmpdir}/filesystem-upload` | 上传临时目录 |
| `view-prefix` | `/v2/file/view/` | 回显地址前缀（有上下文路径时需一并写入） |
| `retention-days` | `30` | 终态任务保留天数 |
| `merge-interval-ms` | `10000` | 合并调度间隔 |
| `upload-interval-ms` | `10000` | 对象上传调度间隔 |
| `retry-interval-ms` | `60000` | 失败任务重试间隔 |
| `cleanup-interval-ms` | `86400000` | 过期数据清理间隔 |

### 3.5 协同文档（`plugin.filesystem.collab`）

`persist-enable`（`true`）、`persist-path`（`data/file-collab/collab-store.json`）、
`persist-seconds`（`30`）、`hub-path`（`data/file-collab/hub`，空串表示纯内存）。

## 四、快速开始

**第一步：引入依赖**（见上）。

**第二步：开启模块并配置一个本地 Provider**

```yaml
plugin:
  filesystem:
    enable: true
    default-provider: local
    storage-server:
      enable: true
      mode: embedded
      path-prefix: /fs
    providers:
      local:
        bucket: system
        type: local
        base-path: ./oss
```

**第三步：上传并回显**

```bash
curl -X POST http://127.0.0.1:8080/v2/file/upload \
  -F 'file=@./demo.png' -F 'fileBucket=system' -F 'ossType=local'
```

返回的 `accessUrl` 形如 `/v2/file/view/local/system/<key>`，浏览器可直接打开；
文件服务器页面地址为 `http://127.0.0.1:8080/fs`。

## 五、运行示例

### 5.1 调用示例

本模块**无链式 API**：`FileStorageTemplate` 的方法直接返回 `*Result` 对象，
请求对象用 `PutObjectRequest.builder()` 构造，按步骤调用：

```java
@Service
public class FileDemo {

    private final FileStorageTemplate template;

    public FileDemo(FileStorageTemplate template) {
        this.template = template;
    }

    public void demo(File file) {
        // 默认 Provider
        PutObjectResult put = template.putObject(file);
        String key = put.getKey();

        // 指定 Provider + 自定义路径
        PutObjectResult custom = template.putObject("minio", PutObjectRequest.builder()
                .filePath("backup")
                .fileName("demo.png")
                .inputStream(in)
                .build());

        // 读取 / 删除 / 列表
        GetObjectResult got = template.getObject("minio", custom.getKey());
        DeleteObjectResult deleted = template.deleteObject("minio", custom.getKey());
        ListObjectResult list = template.listObject(ListObjectRequest.builder().build());

        // 动态增删 Provider
        template.addStorage("extra", storage);
        template.setDefaultStorage("extra");
        template.removeStorage("extra");
    }
}
```

任务化分片上传（接口）：

| 接口 | 说明 |
|:-----|:-----|
| `GET /v2/file/upload-task/buckets` | 已开启的 bucket/提供者列表 |
| `POST /v2/file/upload-task/task` | 创建任务：`{fileName,totalSize,chunkSize,provider,bucket}` |
| `PUT/POST /v2/file/upload-task/task/{id}/chunk?index=n` | 上传分片，可并发、幂等 |
| `POST /v2/file/upload-task/task/{id}/complete` | 分片完毕，触发后台合并 |
| `PUT/POST /v2/file/upload-task/simple` | 单文件直传：小于阈值同步返回，大文件转任务 |
| `GET /v2/file/upload-task/tasks` / `.../task/{id}` | 任务列表 / 详情 |
| `GET /v2/file/upload-task/events` | 任务状态 SSE |
| `DELETE /v2/file/upload-task/task/{id}` | 取消任务并清理分片 |

任务状态：`UPLOADING → MERGING → COMPLETED / FAILED`，另有 `CANCELLED`。

文件服务器侧动作：

```
GET    /{bucket}                       目录列表 / JSON
GET    /{bucket}/{key}                 预览 / 下载（?preview、?download）
PUT|POST /{bucket}/{key}               上传
DELETE /{bucket}/{key}                 删除
POST   /{bucket}?action=mkdir|mkfile|rename|copy|move|unzip
GET    /{bucket}?action=zip            打包下载
POST   /{bucket}?action=setConfig&token=   管理员热更新
GET    /ot                             一次性链接
```

### 5.2 SPI 扩展示例

以图片滤镜为例，实现 → 登记 → 生效：

```java
@Spi("image-filter-pixelate")
public class PixelateImageFilterProvider implements FileStorageImageFilterProvider {

    @Override
    public String getName() {
        return "PIXELATE";
    }

    @Override
    public String getLabel() {
        return "马赛克";
    }

    @Override
    public String getCssFilter() {
        return "blur(2px)";
    }

    @Override
    public String getPathParam() {
        return "";
    }
}
```

登记到 `src/main/resources/META-INF/extensions/com.chua.starter.filesystem.support.spi.FileStorageImageFilterProvider`：

```
com.example.PixelateImageFilterProvider
```

生效：`plugin.filesystem.storage-server.image-filter=PIXELATE`，
管理页的滤镜下拉由 `ServiceProvider` 枚举全部实现后下发；选中后缩略图/预览地址带滤镜参数。

其他可扩展点（同样走 `META-INF/extensions/`）：
`com.chua.common.support.storage.FileStorage`（新增存储后端，实现位于 utils 层）、
`FileStorageRemoteReader`（远程文件读取）、`FileStorageUrlPreviewProvider`（三方 URL 预览）。

## 六、与其它 starter 的关系

| 模块 | 关系 | 说明 |
|:-----|:-----|:-----|
| `utils-support-filestorage-starter` | 被依赖 | 提供 `FileStorage` SPI 与 local/minio/oss/cos/s3/obs 实现 |
| `utils-support-resource-filestorage` | 被依赖 | 预览前端资源（纯资源制品，不再内置于 starter） |
| `utils-support-ssh-starter` | 被依赖 | 远程文件读取，供 SFTP 预览使用 |
| `utils-support-cad-starter` | 被依赖 | CAD 预览（`?format=cad`） |
| `spring-support-oauth-client-starter` | 可选，反射装配 | 私有桶鉴权；类缺失时按公共桶运行 |
| `spring-support-mybatis-starter` / `spring-support-datasource-starter` | 被依赖 | 上传任务基础设施；无数据源时任务仍可跑 |
| `spring-support-common-starter` | 被依赖 | 时间轮调度、模块日志、服务信息 |
| `spring-support-ssh-starter` | 无依赖 | 那是 SSH **服务端**；本模块的远程读取走 utils 层客户端 |

## 七、已知限制

- **默认关闭**：`plugin.filesystem.enable` 默认 `false`，只引入依赖不会注册任何接口。
- **没有 Provider 就没有存储**：`providers` 为空时 `FileStorageTemplate#initialize` 只打印
  「未配置任何 Provider」，上传返回「未配置文件存储提供者」。
- **两种文件服务器形态地址不同**：`embedded` 走应用端口 + `/fs`，`standalone` 才监听
  `18082`；把 `18082` 当成默认地址会在 embedded 形态下连不上。`port`、`host`、`type`
  对 embedded 无意义。
- **私有桶依赖 oauth 模块**：鉴权由 `spring-support-oauth-client-starter` 的
  `OAuthServerFilter` 完成，本模块反射装配；未引入时私有桶配置不会生效。
  令牌入口包括 `x-oauth-token` 请求头、`Authorization: Bearer`、查询参数与 Cookie
  （具体解析在 oauth 侧）。
- **默认只允许图片上传**：`upload.allowed-extensions` 默认 `jpg/jpeg/png/gif/bmp/webp`，
  需要其它类型必须显式放开，并自行评估回显风险。
- **上传任务落库可降级**：没有 `JdbcTemplate` 时任务 MD5 记录不落库，分片与合并仍可工作。
- **协同文档是内存态**：靠 `collab.persist-seconds` 定时刷盘，最多丢失一个间隔的改动；
  `hub-path` 置空则重启丢操作日志。
- **历史 `plugin.filesystem.servers` 与 `storage-server` 独立**：两套都开时会各起一套，
  排查端口占用时不要只看 `storage-server`。
- **预览/下载开关是安全边界**：`open-preview`、`open-download`、`open-range`、`open-path-guard`
  不要为了省事关掉路径穿越防护。
- **对象键即路径**：`/{bucket}/{key}` 的 key 来自上传时生成的桶内路径，
  自行拼接 key 时需保证已做 URL 编码，否则含空格/中文的键会 404。
