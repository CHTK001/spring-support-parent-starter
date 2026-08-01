# spring-support-filesystem-starter

文件存储模块（Spring Boot Starter）。提供**分片上传**、**异步合并**、**上传对象存储**、**HTTP 文件服务器**能力。

## 功能特性

- 分片上传：创建任务 → 上传分片 → 异步合并 → 上传对象存储（OSS/MinIO/S3/本地等）
- 两阶段异步调度：合并调度与上传调度独立、独立重试、全程审计
- 定时清理：终态任务（完成/失败/取消）超期级联清理
- HTTP 文件服务器：按 bucket 提供文件访问，支持 预览/下载 模式
- 多 Provider 存储：通过 SPI 接入 local/minio/oss/cos/s3 等

## 快速开始

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-filesystem-starter</artifactId>
    <version>4.0.0.42</version>
</dependency>
```

配置 `plugin.filesystem.enable=true` 后，分片上传接口与文件服务器即自动启用。

## 配置说明

### 1. 总开关与通用配置

```yaml
plugin:
  filesystem:
    # 是否启用文件存储模块
    enable: true
    # 分片上传临时目录（存储分片与合并中间文件），不配置默认用系统临时目录
    temp-path: /data/filesystem-chunks
    # 默认 Provider 名称（FileStorageTemplate.getDefaultStorage() 返回该 Provider）
    default-provider: local
    # 访问模式：view-仅预览 / download-仅下载 / both-两者皆可（默认 both）
    mode: both
    # 访问 URL 后缀（可选），生成访问地址时拼接，如 "?size=800x600&format=jpg"
    url-suffix: ""
```

### 2. 访问模式 mode 详解

`mode` 决定服务端开放的最强能力，前端无论请求 `view` 还是 `download` 前缀都能得到响应，冲突时自动降级：

| mode 配置 | 前端调 `/view/{bucket}/{key}` | 前端调 `/download/{bucket}/{key}` | 说明 |
|---|---|---|---|
| `view` | 200，内联预览 | 200，内联预览（**降级**） | 只开放预览，下载请求降级为预览 |
| `download` | 200，附件下载（**降级**） | 200，附件下载 | 只开放下载，预览请求降级为下载 |
| `both` | 200，内联预览 | 200，附件下载 | 按前端意图响应 |

- 预览：响应体直接展示（`Content-Disposition` 为空）
- 下载：响应带 `Content-Disposition: attachment; filename="xxx"`，浏览器触发下载

### 3. 分片上传配置（plugin.filesystem.upload）

```yaml
plugin:
  filesystem:
    upload:
      # 分片大小（字节），默认 5MB
      chunk-size: 5242880
      # 终态任务保留天数，超过后定时清理（含分片/记录/文件表级联删除），默认 30 天
      retention-days: 30
      # 合并调度间隔（毫秒），默认 10 秒
      merge-interval-ms: 10000
      # 上传 OSS 调度间隔（毫秒），默认 10 秒
      upload-interval-ms: 10000
      # 失败任务重试调度间隔（毫秒），默认 60 秒
      retry-interval-ms: 60000
      # 过期数据清理调度间隔（毫秒），默认 24 小时
      cleanup-interval-ms: 86400000
```

### 4. 存储 Provider 配置（plugin.filesystem.providers）

支持多 Provider，每个 Provider 对应一个 `FileStorage` SPI 实现：

```yaml
plugin:
  filesystem:
    providers:
      # 本地存储
      local:
        bucket: default          # 存储桶
        type: local              # SPI 类型：local/minio/oss/cos/s3
        base-path: /data/uploads # 本地存储根目录（type=local 时有效）
      # MinIO / S3 兼容
      minio:
        bucket: backup
        type: minio
        endpoint: http://172.16.0.40:9000
        access-key: minioadmin
        secret-key: minioadmin
        region: us-east-1
        # 连接/读写超时（毫秒）
        connection-timeout: 10000
        read-timeout: 30000
        write-timeout: 30000
        secure: false
```

**type 推断规则**（未显式指定 type 时自动推断）：
- `endpoint` 为空 → `local`
- `endpoint` 以 s3/amazonaws 开头 → `s3`
- `endpoint` 包含 `oss.aliyuncs` → `oss`
- `endpoint` 包含 `myhuaweicloud`/`obs.` → `obs`
- `endpoint` 包含 `cos.`/`tencentyun` → `cos`
- 兜底 → `minio`

### 5. HTTP 文件服务器配置（plugin.filesystem.servers）

```yaml
plugin:
  filesystem:
    servers:
      - name: default            # 服务器名称（也是 bucket 校验名）
        enable: true             # 是否启用
        type: jdk                # server SPI 类型，默认 jdk（内置 JDK HttpServer）
        host: 0.0.0.0
        port: 9000               # 文件服务器端口
        ssl: false
        # SSL 证书（ssl=true 时配置）
        # ssl-cert-path: /path/cert.p12
        # ssl-cert-password: xxx
        context-path: /files     # 访问前缀，JDK HttpServer 不允许 "/"
        max-request-size: 10485760      # 最大请求大小（字节）
        max-connections: 512            # 最大连接数
        worker-threads: 8               # 工作线程数
        response-timeout-millis: 60000  # 响应超时（毫秒）
        read-timeout-millis: 30000      # 读超时（毫秒）
        write-timeout-millis: 30000     # 写超时（毫秒）
        content-type: application/octet-stream  # 默认内容类型
        gzip-enabled: false             # 是否启用 gzip 压缩
        gzip-min-size: 1024             # gzip 压缩最小字节数
        charset: UTF-8                  # 字符集
        filters: []                     # 自定义过滤器类名列表
```

文件服务器访问路径格式：

```
http://{host}:{port}{context-path}/{bucket}/{key}           # 默认按 mode 响应
http://{host}:{port}{context-path}/view/{bucket}/{key}      # 强制按预览响应（受 mode 降级约束）
http://{host}:{port}{context-path}/download/{bucket}/{key}  # 强制按下载响应（受 mode 降级约束）
```

示例：`http://127.0.0.1:9000/files/view/default/uploads/xxx/demo.bin`

### 6. 图片处理与预览配置（plugin.filesystem.operation）

对应底层 `FileOperationSetting`，用于图片缩放/裁剪/水印等处理：

```yaml
plugin:
  filesystem:
    operation:
      size: 800x600          # 输出尺寸，如 "800x600" 或 "800"（等比）
      format: jpg            # 输出格式，如 "jpg"、"png"
      quality: 90            # 图片质量 (1-100)
      crop: 100,100,200,200  # 裁剪区域
      rotate: 0              # 旋转角度 (0-360)
      flip: ""               # 翻转方向: "horizontal"/"vertical"
      grayscale: false       # 是否灰度
      blur: 0.0              # 模糊半径
      sharpen: 0.0           # 锐化强度
      auto-orient: true      # 自动旋转校正
      watermark-text: ""     # 文字水印
      watermark-image: ""    # 水印图片 URL
      force-download: false  # 强制下载（预览时也走 attachment）
      pdf-page: 0            # PDF 页码 (1-based)
      pdf-page-size: ""      # PDF 页面尺寸
      pdf-orientation: ""    # PDF 页面方向
```

### 7. 其他开关

```yaml
plugin:
  filesystem:
    open-webjars: true       # 是否启用 webjars 资源（默认 true）
    open-remote-file: false  # 是否允许远程文件（默认 false）
```

## 数据表

| 表名 | 说明 |
|---|---|
| `filesystem_upload_task` | 上传任务表（含 stage 阶段：分片上传/合并/上传OSS） |
| `filesystem_upload_chunk` | 分片表（合并成功后标记 MERGED） |
| `filesystem_upload_file` | 文件主体表（上传成功后写入最终文件信息） |
| `filesystem_upload_merge_log` | 合并记录表（审计：每次合并尝试成败均记录） |
| `filesystem_upload_upload_log` | 上传 OSS 记录表（审计：每次上传尝试成败均记录） |

表结构脚本：`db/migration/V1.0__init_filesystem_upload.sql`、`V1.1`、`V1.2`、`V1.3`

## 分片上传接口

### 1. 创建任务

`POST /chunk/create`

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `fileName` | String | 是 | - | 文件名 |
| `fileSize` | Long | 是 | - | 文件大小（字节） |
| `md5` | String | 否 | - | 文件 MD5（秒传校验用） |
| `bucket` | String | 否 | `default` | 目标 bucket，必须已被文件服务器启动注册 |
| `contentType` | String | 否 | - | 内容类型 |

分片大小由服务端 `upload.chunk-size` 决定，客户端不可指定。

**响应**：`uploadId`、`chunkSize`、`totalChunks`、`fileSize`、`key`、`bucket`、`md5`、`parts[]`（每个分片的 partNumber/size）

### 2. 上传分片

`POST /chunk/{uploadId}/part?partNumber=1` （`multipart/form-data`，字段名 `file`）

### 3. 查询进度

`GET /chunk/{uploadId}/progress`

**响应**：`status`、`statusText`、`uploadedChunks`、`totalChunks`、`percent`、`url`（完成后）

### 4. 完成分片（异步入队）

`POST /chunk/{uploadId}/complete`

校验分片齐全后入队为"待合并"状态，由异步调度依次执行合并与上传对象存储，**同步返回，不阻塞**。

### 5. 取消上传

`DELETE /chunk/{uploadId}`

## 任务状态机

```
分片上传阶段(Stage=0)      合并阶段(Stage=1)        上传OSS阶段(Stage=2)
0 待上传 ──上传分片──▶ 1 上传中
                                    ──分片齐──▶ 2 待合并 ──调度──▶ 3 合并中
                                                     │合并成功
                                                     ▼
                                         5 待上传OSS ──调度──▶ 6 上传OSS中
                                                     │上传成功
                                                     ▼
                                                     4 已完成
失败 → -1  取消 → -2
```

## 异步流程

```
complete ──入队──▶ 任务置为"待合并"(stage=1)
合并调度(merge-interval-ms) ──▶ 合并分片 ──▶ 写 merge_log(成败) ──成功──▶ markMerged分片 + 任务置"待上传OSS"(stage=2)
上传调度(upload-interval-ms) ──▶ putObject ──▶ 写 upload_log(成败) ──成功──▶ 写 file表 + 任务完成
失败 ──▶ 写日志 + 回退到待执行状态，由 retry-interval-ms 重试（最多3次）
定时清理(cleanup-interval-ms) ──▶ 清理超期终态任务（级联删 分片/文件/日志 + 磁盘临时文件）
```
