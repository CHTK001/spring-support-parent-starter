# spring-support-filesystem-starter

文件存储模块（Spring Boot Starter）。提供**统一对象存储抽象**（多 Provider）与**内置 HTTP 文件服务器**能力。

## 功能特性

- 统一 `FileStorageTemplate` API：上传 / 回显 / 下载 / 删除 / 列表
- 多 Provider 存储：通过 SPI 接入 local / minio / oss / cos / s3 / obs 等
- 单文件上传与回显：`/v2/file/upload`、`/v2/file/view/**`（回显匿名直链，供 img/富文本引用）
- 内置 HTTP 文件服务器（`storage-server`）：随应用启动，提供对象浏览/预览/下载与文件管理动作
- 预览能力：图片/文本/PDF/Office/代码/压缩包等经 SPI 转 HTML 内联预览
- 访问控制：桶可设**公共**或**私有**，私有桶经 OAuth 过滤（`oauth-client-starter`，反射装配）校验访问令牌
- 运行时热更新：管理员 `setConfig` 可实时切换开关、布局、滤镜与私有桶，无需重启

## 快速开始

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-filesystem-starter</artifactId>
    <version>4.0.0.42</version>
</dependency>
```

配置 `plugin.filesystem.enable=true` 后，对象上传/回显接口与文件服务器自动启用。

## 配置说明

### 1. 总开关与通用配置

```yaml
plugin:
  filesystem:
    enable: true
    default-provider: local      # 未指定提供者时使用
    mode: both                   # view-仅预览 / download-仅下载 / both-两者皆可
    url-suffix: ""               # 访问地址后缀（可选）
    open-webjars: true           # 是否启用 webjars 资源
    open-remote-file: false      # 是否允许远程文件透传
```

### 2. 文件服务器（plugin.filesystem.storage-server）

随应用启动的独立 HTTP 文件存储网关（默认端口 18080），供"文件服务器"页面直连：

```yaml
plugin:
  filesystem:
    storage-server:
      enable: true               # 是否随应用启动（默认 true）
      host: 0.0.0.0
      port: 18080
      type: jdk                  # 服务器引擎 jdk / nio / aio
      open-preview: true         # 允许 ?preview 内联预览
      open-download: true        # 允许 ?download 附件下载
      open-range: true           # 允许 Range 分段下载
      open-watermark: false      # 附加水印响应头
      open-webjars: true
      open-remote-file: false
      layout: true               # 目录列表页
      layout-type: SIMPLE        # SIMPLE-简单列表 / SYSTEM-资源管理器
      image-filter: NONE         # 图片滤镜（ImageFilter SPI 扩展名）
      private-buckets: ""        # 私有桶（provider 名，逗号分隔；* 表示全部私有）
```

访问路径：`http://{host}:{port}/{bucket}/{key}`（`/{bucket}` 返回目录列表页/JSON，`?preview` 预览、`?download` 附件）。

> 另有一套历史 `plugin.filesystem.servers`（`FileServerManager`，默认关闭），与 `storage-server` 相互独立。

### 3. 访问控制（公共 / 私有）

- 私有桶请求需携带访问令牌：请求头 `x-oauth-token` / `Authorization: Bearer` / 查询参数 / Cookie。
- 鉴权由 `spring-support-oauth-client-starter` 的 `OAuthServerFilter` 完成，本模块经反射装配（类缺失时按公共桶运行）。
- 初始私有桶取 `storage-server.private-buckets`，留空回退 `plugin.oauth.file-storage.private-buckets`。
- 运行期可在文件服务器页「系统配置」热更新（`setConfig` 的 `private` / `privateBuckets`），实时生效；重启回到配置默认。

### 4. 存储 Provider（plugin.filesystem.providers）

每个 Provider 对应一个 `FileStorage` SPI 实现：

```yaml
plugin:
  filesystem:
    providers:
      local:
        bucket: system           # 存储桶
        type: local              # SPI 类型：local/minio/oss/cos/s3
        base-path: ./oss         # 本地存储根目录（type=local 时有效）
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

**type 推断规则**（未显式指定时）：
- `endpoint` 为空 → `local`
- 以 s3/amazonaws 开头 → `s3`
- 含 `oss.aliyuncs` → `oss`
- 含 `myhuaweicloud`/`obs.` → `obs`
- 含 `cos.`/`tencentyun` → `cos`
- 兜底 → `minio`

### 5. 上传与回显配置（plugin.filesystem.upload）

```yaml
plugin:
  filesystem:
    upload:
      # 单文件大小上限（MB）
      max-size-mb: 10
      # 扩展名白名单（默认仅图片）
      allowed-extensions: [png, jpg, jpeg, gif, webp]
      # 回显地址前缀（带上下文路径）
      view-prefix: /system/api/v2/file/view/
```

## 接口

| 接口 | 说明 |
|---|---|
| `PUT/POST /v2/file/upload` | 单文件上传（服务端生成随机键，返回回显地址） |
| `GET /v2/file/view/**` | 匿名回显（`?download=true` 附件下载），按流写出 |
| `GET /v2/file/dir/list` | 本机目录浏览（`plugin.filesystem.dir-list` 开关，默认关闭） |

文件服务器侧：`GET /{bucket}`（列表/JSON）、`GET /{bucket}/{key}`（预览/下载）、`PUT|POST` 上传、`DELETE` 删除、
`POST /{bucket}?action=mkdir|mkfile|rename|copy|move|unzip`、`GET /{bucket}?action=zip`、
`POST /{bucket}?action=setConfig&token=`（管理员热更新）、`/ot` 一次性链接。