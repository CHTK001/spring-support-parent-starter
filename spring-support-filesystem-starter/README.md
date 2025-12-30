# Spring Support Filesystem Starter

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 📖 模块简介

Spring Support Filesystem Starter 是一个统一的文件系统抽象模块，提供统一的文件操作接口，支持本地文件系统、MinIO、阿里云OSS、腾讯云COS等多种存储方式的无缝切换。

### ✨ 主要特性

- 🔌 **统一接口** - 提供统一的文件操作API
- 💾 **多存储支持** - 支持本地、MinIO、OSS、COS等
- 📤 **上传下载** - 支持文件上传下载
- 🗂️ **目录管理** - 支持目录创建、删除、遍历
- 🔗 **URL生成** - 支持访问URL和临时URL生成
- 📊 **元数据管理** - 支持文件元数据读写

## 🚀 快速开始

### Maven 依赖

```xml
<!-- 文件系统抽象模块 -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-filesystem-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>

<!-- 根据需要选择具体实现 -->
<!-- MinIO实现 -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-filesystem-minio-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>
```

## ⚙️ 配置说明

### 本地文件系统配置

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `plugin.filesystem.type` | String | local | 文件系统类型 |
| `plugin.filesystem.local.base-path` | String | /tmp | 本地存储路径 |

### 配置示例

```yaml
plugin:
  filesystem:
    type: local
    local:
      base-path: /data/uploads
```

## 📝 使用示例

### 文件上传

```java
@Service
public class FileService {

    @Autowired
    private FileSystem fileSystem;
    
    public String uploadFile(MultipartFile file) throws IOException {
        // 生成文件路径
        String fileName = UUID.randomUUID().toString() + 
                         getFileExtension(file.getOriginalFilename());
        String path = "uploads/" + LocalDate.now() + "/" + fileName;
        
        // 上传文件
        fileSystem.write(path, file.getInputStream());
        
        return path;
    }
    
    public void uploadWithMetadata(MultipartFile file, Map<String, String> metadata) 
            throws IOException {
        String path = "uploads/" + file.getOriginalFilename();
        
        // 上传文件并设置元数据
        fileSystem.write(path, file.getInputStream(), metadata);
    }
}
```

### 文件下载

```java
@RestController
public class FileController {

    @Autowired
    private FileSystem fileSystem;
    
    @GetMapping("/files/{path}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String path) 
            throws IOException {
        // 读取文件
        InputStream inputStream = fileSystem.read(path);
        InputStreamResource resource = new InputStreamResource(inputStream);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                       "attachment; filename=\"" + getFileName(path) + "\"")
                .body(resource);
    }
    
    @GetMapping("/files/{path}/url")
    public String getFileUrl(@PathVariable String path) {
        // 获取文件访问URL（临时URL，24小时有效）
        return fileSystem.getPresignedUrl(path, Duration.ofHours(24));
    }
}
```

### 文件管理

```java
@Service
public class FileManagementService {

    @Autowired
    private FileSystem fileSystem;
    
    public boolean fileExists(String path) {
        return fileSystem.exists(path);
    }
    
    public void deleteFile(String path) throws IOException {
        fileSystem.delete(path);
    }
    
    public List<String> listFiles(String directory) throws IOException {
        return fileSystem.list(directory);
    }
    
    public void createDirectory(String path) throws IOException {
        fileSystem.createDirectory(path);
    }
    
    public FileInfo getFileInfo(String path) throws IOException {
        return fileSystem.getFileInfo(path);
    }
}
```

## 🔗 具体实现模块

### MinIO 实现
- [spring-support-filesystem-minio-starter](../spring-support-filesystem-minio-starter/README.md)
- 支持MinIO对象存储
- 支持桶管理和权限控制
- 适用于私有云部署

## 🔗 相关链接

- [返回主文档](../README.md)
- [配置示例文件](../application-example.yml)

## 📄 许可证

本项目采用 [Apache License 2.0](../LICENSE) 许可证。
