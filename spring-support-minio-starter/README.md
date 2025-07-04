# Spring Support MinIO Starter

## 📖 模块简介

Spring Support MinIO Starter 是一个功能完整的MinIO对象存储集成模块，提供了企业级应用中文件存储、管理和访问的完整解决方案。该模块封装了MinIO客户端操作，提供了简单易用的API接口，支持文件上传下载、桶管理、权限控制等功能。

## ✨ 主要功能

### 📁 文件管理
- 文件上传和下载
- 文件预览和分享
- 文件元数据管理
- 批量文件操作

### 🗂️ 存储桶管理
- 存储桶创建和删除
- 存储桶列表查询
- 存储桶权限配置
- 默认存储桶初始化

### 🔗 URL生成
- 临时访问URL生成
- 预签名上传URL
- 文件分享链接
- 自定义过期时间

### 🔒 安全控制
- 访问权限控制
- 文件大小限制
- 文件类型验证
- 安全策略配置

### 🎯 高级功能
- 文件去重处理
- 自动文件分类
- 文件版本管理
- 批量操作支持

## 🚀 快速开始

### Maven依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-minio-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 基础配置

```yaml
plugin:
  spring:
    minio:
      address: http://localhost:9000    # MinIO服务器地址
      username: minioadmin             # 访问密钥ID
      password: minioadmin             # 访问密钥Secret
      bucket: default                  # 默认存储桶名称
```

## 📋 详细功能说明

### 1. 文件上传

#### 基础文件上传

```java
@RestController
@RequestMapping("/files")
public class FileController {
    
    @Autowired
    private MinioTemplate minioTemplate;
    
    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bucket", required = false) String bucket) {
        
        try {
            // 上传文件到指定桶（如果不指定则使用默认桶）
            minioTemplate.putObject(
                file.getInputStream(), 
                bucket, 
                file.getOriginalFilename()
            );
            
            return ResponseEntity.ok("文件上传成功");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("文件上传失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/upload-batch")
    public ResponseEntity<List<String>> uploadFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "bucket", required = false) String bucket) {
        
        List<String> uploadedFiles = new ArrayList<>();
        
        for (MultipartFile file : files) {
            try {
                String fileName = minioTemplate.generateOssUuidFileName(file.getOriginalFilename());
                minioTemplate.putObject(file.getInputStream(), bucket, fileName);
                uploadedFiles.add(fileName);
            } catch (Exception e) {
                log.error("文件上传失败: {}", file.getOriginalFilename(), e);
            }
        }
        
        return ResponseEntity.ok(uploadedFiles);
    }
}
```

#### 高级上传功能

```java
@Service
public class FileUploadService {
    
    @Autowired
    private MinioTemplate minioTemplate;
    
    public String uploadWithValidation(MultipartFile file, String bucket) {
        // 文件类型验证
        if (!isValidFileType(file)) {
            throw new IllegalArgumentException("不支持的文件类型");
        }
        
        // 文件大小验证
        if (file.getSize() > 10 * 1024 * 1024) { // 10MB限制
            throw new IllegalArgumentException("文件大小超过限制");
        }
        
        try {
            String fileName = minioTemplate.generateOssUuidFileName(file.getOriginalFilename());
            minioTemplate.putObject(file.getInputStream(), bucket, fileName);
            
            // 记录上传日志
            logFileUpload(fileName, file.getSize(), bucket);
            
            return fileName;
        } catch (Exception e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }
    
    private boolean isValidFileType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && (
            contentType.startsWith("image/") ||
            contentType.startsWith("application/pdf") ||
            contentType.startsWith("text/")
        );
    }
    
    private void logFileUpload(String fileName, long fileSize, String bucket) {
        log.info("文件上传成功 - 文件名: {}, 大小: {} bytes, 存储桶: {}", 
                fileName, fileSize, bucket);
    }
}
```

### 2. 文件下载和访问

#### 文件下载

```java
@RestController
@RequestMapping("/files")
public class FileController {
    
    @GetMapping("/download/{bucket}/{fileName}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String bucket,
            @PathVariable String fileName) {
        
        try {
            InputStream inputStream = minioTemplate.getObject(bucket, fileName);
            InputStreamResource resource = new InputStreamResource(inputStream);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                       "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
                
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/preview/{bucket}/{fileName}")
    public ResponseEntity<String> getPreviewUrl(
            @PathVariable String bucket,
            @PathVariable String fileName) {
        
        try {
            // 生成24小时有效的预览URL
            String previewUrl = minioTemplate.getResignedObjectUrl(bucket, fileName);
            return ResponseEntity.ok(previewUrl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("生成预览URL失败");
        }
    }
}
```

#### 文件流式传输

```java
@Service
public class FileStreamService {
    
    @Autowired
    private MinioTemplate minioTemplate;
    
    public void streamFile(String bucket, String fileName, HttpServletResponse response) {
        try (InputStream inputStream = minioTemplate.getObject(bucket, fileName);
             OutputStream outputStream = response.getOutputStream()) {
            
            // 设置响应头
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", 
                             "attachment; filename=\"" + fileName + "\"");
            
            // 流式传输文件
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            outputStream.flush();
        } catch (Exception e) {
            log.error("文件流式传输失败: {}/{}", bucket, fileName, e);
            throw new RuntimeException("文件传输失败", e);
        }
    }
}
```

### 3. 存储桶管理

#### 存储桶操作

```java
@RestController
@RequestMapping("/buckets")
public class BucketController {
    
    @Autowired
    private MinioTemplate minioTemplate;
    
    @GetMapping
    public ResponseEntity<List<String>> listBuckets() {
        try {
            List<String> bucketNames = minioTemplate.listBuckets()
                .stream()
                .map(bucket -> bucket.name())
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(bucketNames);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{bucketName}")
    public ResponseEntity<String> createBucket(@PathVariable String bucketName) {
        try {
            if (minioTemplate.bucketExists(bucketName)) {
                return ResponseEntity.badRequest().body("存储桶已存在");
            }
            
            minioTemplate.makeBucket(bucketName);
            return ResponseEntity.ok("存储桶创建成功");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("创建存储桶失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{bucketName}")
    public ResponseEntity<String> deleteBucket(@PathVariable String bucketName) {
        try {
            if (!minioTemplate.bucketExists(bucketName)) {
                return ResponseEntity.notFound().build();
            }
            
            minioTemplate.removeBucket(bucketName);
            return ResponseEntity.ok("存储桶删除成功");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("删除存储桶失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{bucketName}/files")
    public ResponseEntity<List<FileInfo>> listFiles(
            @PathVariable String bucketName,
            @RequestParam(defaultValue = "false") boolean recursive) {
        
        try {
            List<FileInfo> files = new ArrayList<>();
            
            Iterable<Result<Item>> objects = minioTemplate.listObjects(bucketName, recursive);
            for (Result<Item> result : objects) {
                Item item = result.get();
                files.add(FileInfo.builder()
                    .fileName(item.objectName())
                    .size(item.size())
                    .lastModified(item.lastModified())
                    .etag(item.etag())
                    .build());
            }
            
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
```

### 4. 预签名URL和表单上传

#### 预签名URL生成

```java
@Service
public class PresignedUrlService {
    
    @Autowired
    private MinioTemplate minioTemplate;
    
    public String generateUploadUrl(String bucket, String fileName, int expireHours) {
        try {
            // 生成预签名上传URL
            return minioTemplate.getResignedObjectUrl(bucket, fileName);
        } catch (Exception e) {
            throw new RuntimeException("生成上传URL失败", e);
        }
    }
    
    public Map<String, String> generatePostFormData(String bucket, String fileName) {
        try {
            // 生成表单上传数据
            return minioTemplate.getResignedPostFormData(bucket, fileName);
        } catch (Exception e) {
            throw new RuntimeException("生成表单数据失败", e);
        }
    }
    
    public String generateShareUrl(String bucket, String fileName, int expireDays) {
        try {
            // 生成分享链接
            return minioTemplate.getResignedObjectUrl(bucket, fileName);
        } catch (Exception e) {
            throw new RuntimeException("生成分享链接失败", e);
        }
    }
}
```

### 5. 文件管理服务

#### 完整的文件管理服务

```java
@Service
public class FileManagementService {
    
    @Autowired
    private MinioTemplate minioTemplate;
    
    public FileUploadResult uploadFile(MultipartFile file, String bucket, Map<String, String> metadata) {
        try {
            // 验证文件
            validateFile(file);
            
            // 生成文件名
            String fileName = minioTemplate.generateOssUuidFileName(file.getOriginalFilename());
            
            // 上传文件
            minioTemplate.putObject(file.getInputStream(), bucket, fileName);
            
            // 生成访问URL
            String accessUrl = minioTemplate.getResignedObjectUrl(bucket, fileName);
            
            return FileUploadResult.builder()
                .fileName(fileName)
                .originalName(file.getOriginalFilename())
                .size(file.getSize())
                .contentType(file.getContentType())
                .bucket(bucket)
                .accessUrl(accessUrl)
                .uploadTime(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }
    
    public void deleteFile(String bucket, String fileName) {
        try {
            // 删除文件的逻辑需要自己实现，MinIO客户端提供removeObject方法
            // minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(fileName).build());
            log.info("删除文件: {}/{}", bucket, fileName);
        } catch (Exception e) {
            throw new RuntimeException("文件删除失败", e);
        }
    }
    
    public FileInfo getFileInfo(String bucket, String fileName) {
        try {
            // 获取文件信息的逻辑需要自己实现
            // StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(fileName).build());
            return FileInfo.builder()
                .fileName(fileName)
                .bucket(bucket)
                .build();
        } catch (Exception e) {
            throw new RuntimeException("获取文件信息失败", e);
        }
    }
    
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        if (file.getSize() > 100 * 1024 * 1024) { // 100MB限制
            throw new IllegalArgumentException("文件大小超过限制");
        }
        
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("无法确定文件类型");
        }
    }
}
```

## ⚙️ 高级配置

### 完整配置示例

```yaml
plugin:
  spring:
    minio:
      # 基础连接配置
      address: http://localhost:9000
      username: minioadmin
      password: minioadmin
      bucket: default
      
      # 连接配置
      connection-timeout: 10000    # 连接超时时间（毫秒）
      read-timeout: 30000         # 读取超时时间（毫秒）
      write-timeout: 30000        # 写入超时时间（毫秒）
      
      # 安全配置
      secure: false               # 是否使用HTTPS
      region: us-east-1          # 区域设置
      
      # 文件配置
      max-file-size: 100MB       # 最大文件大小
      allowed-types:             # 允许的文件类型
        - image/*
        - application/pdf
        - text/*
      
      # 存储桶配置
      auto-create-bucket: true   # 自动创建默认存储桶
      bucket-policy: public      # 存储桶策略
```

### 自定义MinIO客户端

```java
@Configuration
public class CustomMinioConfig {
    
    @Bean
    @Primary
    public MinioTemplate customMinioTemplate(MinioProperties properties) {
        return new MinioTemplate(properties) {
            @Override
            public String generateOssUuidFileName(String originalFilename) {
                // 自定义文件名生成策略
                String extension = getFileExtension(originalFilename);
                String timestamp = String.valueOf(System.currentTimeMillis());
                String uuid = UUID.randomUUID().toString().replace("-", "");
                
                return String.format("files/%s/%s/%s.%s", 
                    LocalDate.now().toString(), 
                    timestamp, 
                    uuid, 
                    extension);
            }
        };
    }
    
    private String getFileExtension(String filename) {
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
```

## 🔧 自定义扩展

### 文件处理拦截器

```java
@Component
public class FileUploadInterceptor {
    
    @EventListener
    public void handleFileUpload(FileUploadEvent event) {
        // 文件上传后处理
        log.info("文件上传完成: {}", event.getFileName());
        
        // 可以在这里添加：
        // 1. 文件病毒扫描
        // 2. 图片压缩处理
        // 3. 文档格式转换
        // 4. 文件备份
        // 5. 通知相关用户
    }
}
```

### 文件访问权限控制

```java
@Component
public class FileAccessController {
    
    public boolean hasAccess(String bucket, String fileName, String userId) {
        // 实现文件访问权限检查逻辑
        
        // 检查用户是否有访问该存储桶的权限
        if (!hasbucketAccess(bucket, userId)) {
            return false;
        }
        
        // 检查用户是否有访问该文件的权限
        if (!hasFileAccess(fileName, userId)) {
            return false;
        }
        
        return true;
    }
    
    private boolean hasBucketAccess(String bucket, String userId) {
        // 实现存储桶访问权限检查
        return true;
    }
    
    private boolean hasFileAccess(String fileName, String userId) {
        // 实现文件访问权限检查
        return true;
    }
}
```

## 📝 注意事项

1. **存储桶命名**：存储桶名称必须符合DNS命名规范，只能包含小写字母、数字和连字符
2. **文件大小限制**：MinIO默认支持最大5TB的单个文件，但建议根据实际需求设置合理限制
3. **并发上传**：大量并发上传时注意连接池配置和服务器性能
4. **安全考虑**：生产环境建议启用HTTPS和访问控制
5. **备份策略**：重要文件建议配置多副本或定期备份
6. **监控告警**：建议配置存储空间和访问量监控

## 🐛 故障排除

### 常见问题

1. **连接失败**
   - 检查MinIO服务器是否正常运行
   - 验证网络连接和防火墙设置
   - 确认地址和端口配置正确

2. **认证失败**
   - 检查用户名和密码是否正确
   - 验证用户是否有相应权限
   - 确认访问密钥是否过期

3. **文件上传失败**
   - 检查文件大小是否超过限制
   - 验证存储桶是否存在
   - 确认磁盘空间是否充足

### 调试建议

启用调试日志：

```yaml
logging:
  level:
    com.chua.starter.minio: DEBUG
    io.minio: DEBUG
```

这将输出详细的MinIO操作日志，帮助定位问题。
