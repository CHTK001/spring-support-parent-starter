# 节点管理功能说明

## 概述

节点管理功能通过自定义协议服务器提供文件管理、脚本执行、系统信息获取等功能，支持远程节点管理和监控。

## 架构设计

### 核心组件

1. **NodeManagementConfiguration** - 节点管理配置类
   - 处理文件管理相关请求
   - 处理脚本执行相关请求
   - 处理系统信息相关请求

2. **FileManagementService** - 文件管理服务
   - 文件列表、文件树、文件操作
   - 文件上传、下载
   - 文件搜索、预览

3. **ScriptExecuteService** - 脚本执行服务
   - 支持多种脚本类型（Shell、PowerShell、Python等）
   - 同步/异步执行
   - 进程管理

4. **SystemInfoService** - 系统信息服务
   - 系统硬件信息
   - 进程和服务管理
   - 网络和存储信息

## 接口说明

### 文件管理接口

#### 1. 文件列表
- **接口**: `node-file-list`
- **功能**: 获取指定目录的文件列表
- **请求参数**:
```json
{
  "profile": "dev",
  "content": {
    "path": "/path/to/directory",
    "includeHidden": false,
    "sortBy": "name",
    "sortOrder": "asc"
  }
}
```

#### 2. 文件树
- **接口**: `node-file-tree`
- **功能**: 获取文件树结构
- **请求参数**:
```json
{
  "profile": "dev",
  "content": {
    "path": "/path/to/root",
    "maxDepth": 3,
    "includeHidden": false
  }
}
```

#### 3. 文件上传
- **接口**: `node-file-upload`
- **功能**: 上传文件到指定目录
- **请求参数**:
```json
{
  "profile": "dev",
  "content": {
    "targetPath": "/path/to/target",
    "fileName": "example.txt",
    "fileData": "base64_encoded_data",
    "overwrite": true
  }
}
```

#### 4. 文件下载
- **接口**: `node-file-download`
- **功能**: 下载指定文件
- **请求参数**:
```json
{
  "profile": "dev",
  "content": {
    "filePath": "/path/to/file.txt"
  }
}
```

#### 5. 文件操作
- **接口**: `node-file-operation`
- **功能**: 执行文件操作（删除、重命名、复制、移动等）
- **请求参数**:
```json
{
  "profile": "dev",
  "content": {
    "operation": "DELETE",
    "path": "/path/to/file",
    "recursive": true
  }
}
```

### 脚本执行接口

#### 1. 脚本执行
- **接口**: `node-script-execute`
- **功能**: 执行脚本
- **请求参数**:
```json
{
  "profile": "dev",
  "content": {
    "scriptType": "shell",
    "scriptContent": "echo 'Hello World'",
    "scriptParams": ["param1", "param2"],
    "workingDirectory": "/tmp",
    "timeout": 300,
    "async": false
  }
}
```

#### 2. 停止脚本
- **接口**: `node-script-stop`
- **功能**: 停止正在执行的脚本
- **请求参数**:
```json
{
  "profile": "dev",
  "content": {
    "processId": 12345
  }
}
```

#### 3. 脚本状态
- **接口**: `node-script-status`
- **功能**: 获取脚本执行状态
- **请求参数**:
```json
{
  "profile": "dev",
  "content": {
    "processId": 12345
  }
}
```

#### 4. 支持的脚本类型
- **接口**: `node-script-types`
- **功能**: 获取支持的脚本类型列表

### 系统信息接口

#### 1. 系统信息
- **接口**: `node-system-info`
- **功能**: 获取完整的系统信息

#### 2. 进程列表
- **接口**: `node-process-list`
- **功能**: 获取系统进程列表

#### 3. 服务列表
- **接口**: `node-service-list`
- **功能**: 获取系统服务列表

## 支持的脚本类型

- **shell** - Shell脚本 (.sh)
- **batch** - 批处理脚本 (.bat)
- **powershell** - PowerShell脚本 (.ps1)
- **python** - Python脚本 (.py)
- **javascript** - JavaScript脚本 (.js)
- **groovy** - Groovy脚本 (.groovy)
- **lua** - Lua脚本 (.lua)
- **perl** - Perl脚本 (.pl)
- **ruby** - Ruby脚本 (.rb)
- **php** - PHP脚本 (.php)

## 支持的文件操作

- **LIST** - 列出文件
- **TREE** - 获取文件树
- **UPLOAD** - 上传文件
- **DOWNLOAD** - 下载文件
- **DELETE** - 删除文件
- **RENAME** - 重命名文件
- **MKDIR** - 创建目录
- **COPY** - 复制文件
- **MOVE** - 移动文件
- **SEARCH** - 搜索文件
- **PREVIEW** - 预览文件
- **INFO** - 获取文件信息

## 环境过滤

所有接口都支持环境过滤，通过 `profile` 参数指定环境：
- 请求中的 `profile` 必须匹配节点的 `applicationActive` 或包含在 `applicationActiveInclude` 中
- 如果环境不匹配，接口将返回 "环境不支持" 错误

## 安全考虑

1. **环境隔离**: 通过环境过滤确保只有匹配的环境才能执行操作
2. **文件路径验证**: 防止路径遍历攻击
3. **脚本执行限制**: 支持超时控制和进程管理
4. **权限控制**: 基于当前用户权限执行操作

## 使用示例

### Java客户端调用示例

```java
// 创建请求
JsonObject request = new JsonObject();
request.put("profile", "dev");
request.put("content", Json.toJson(FileOperationRequest.createListRequest("/home")));

// 发送请求到节点
Response response = protocolClient.send("node-file-list", request.toString().getBytes());

// 处理响应
if (response instanceof OkResponse) {
    FileOperationResponse result = Json.fromJson(new String(response.getBody()), FileOperationResponse.class);
    // 处理文件列表
}
```

## 配置说明

节点管理功能会自动注册到协议服务器中，无需额外配置。确保以下配置正确：

```yaml
plugin:
  report:
    client:
      enable: true
      server: false
      protocol:
        type: "tcp"
        host: "0.0.0.0"
        port: 9999
```

## 注意事项

1. 文件上传/下载使用Base64编码传输，适合小文件操作
2. 脚本执行支持同步和异步模式，长时间运行的脚本建议使用异步模式
3. 系统信息获取可能需要特定权限，某些信息在不同操作系统上可能不可用
4. 所有操作都会记录日志，便于问题排查和审计
