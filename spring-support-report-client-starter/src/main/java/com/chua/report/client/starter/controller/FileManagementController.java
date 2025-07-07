package com.chua.report.client.starter.controller;

import com.chua.report.client.starter.pojo.FileOperationRequest;
import com.chua.report.client.starter.pojo.FileOperationResponse;
import com.chua.report.client.starter.service.ClientHealthService;
import com.chua.report.client.starter.service.FileManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件管理控制器
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.report.client.file-management", name = "enable", havingValue = "true")
public class FileManagementController {

    private final FileManagementService fileManagementService;
    private final ClientHealthService clientHealthService;

    /**
     * 列出目录文件
     */
    @GetMapping("/list")
    public ResponseEntity<FileOperationResponse> listFiles(
            @RequestParam String path,
            @RequestParam(required = false) Boolean includeHidden,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FileOperationResponse.error("LIST", "客户端健康状态无效", "HEALTH_CHECK_FAILED"));
        }

        try {
            FileOperationResponse response = fileManagementService.listFiles(path, includeHidden, sortBy, sortOrder);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("列出文件失败: path={}", path, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileOperationResponse.error("LIST", "服务器内部错误", "INTERNAL_ERROR"));
        }
    }

    /**
     * 获取文件树结构
     */
    @GetMapping("/tree")
    public ResponseEntity<FileOperationResponse> getFileTree(
            @RequestParam String path,
            @RequestParam(required = false) Integer maxDepth,
            @RequestParam(required = false) Boolean includeHidden) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FileOperationResponse.error("TREE", "客户端健康状态无效", "HEALTH_CHECK_FAILED"));
        }

        try {
            FileOperationResponse response = fileManagementService.getFileTree(path, maxDepth, includeHidden);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取文件树失败: path={}", path, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileOperationResponse.error("TREE", "服务器内部错误", "INTERNAL_ERROR"));
        }
    }

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public ResponseEntity<FileOperationResponse> uploadFile(
            @RequestParam String targetPath,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Boolean overwrite) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FileOperationResponse.error("UPLOAD", "客户端健康状态无效", "HEALTH_CHECK_FAILED"));
        }

        try {
            FileOperationResponse response = fileManagementService.uploadFile(targetPath, file, overwrite);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("上传文件失败: targetPath={}, filename={}", targetPath, 
                file != null ? file.getOriginalFilename() : "null", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileOperationResponse.error("UPLOAD", "服务器内部错误", "INTERNAL_ERROR"));
        }
    }

    /**
     * 下载文件
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam String filePath) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            FileOperationResponse response = fileManagementService.downloadFile(filePath);
            
            if (!response.getSuccess()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            if (response.getData() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            String filename = response.getFileInfo() != null ? response.getFileInfo().getName() : "download";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
            
            ByteArrayResource resource = new ByteArrayResource(response.getData());
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(response.getData().length))
                .body(resource);
                
        } catch (Exception e) {
            log.error("下载文件失败: filePath={}", filePath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/delete")
    public ResponseEntity<FileOperationResponse> deleteFile(
            @RequestParam String path,
            @RequestParam(required = false) Boolean recursive) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FileOperationResponse.error("DELETE", "客户端健康状态无效", "HEALTH_CHECK_FAILED"));
        }

        try {
            FileOperationResponse response = fileManagementService.deleteFile(path, recursive);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除文件失败: path={}", path, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileOperationResponse.error("DELETE", "服务器内部错误", "INTERNAL_ERROR"));
        }
    }

    /**
     * 重命名文件
     */
    @PostMapping("/rename")
    public ResponseEntity<FileOperationResponse> renameFile(
            @RequestParam String path,
            @RequestParam String newName) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FileOperationResponse.error("RENAME", "客户端健康状态无效", "HEALTH_CHECK_FAILED"));
        }

        try {
            FileOperationResponse response = fileManagementService.renameFile(path, newName);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("重命名文件失败: path={}, newName={}", path, newName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileOperationResponse.error("RENAME", "服务器内部错误", "INTERNAL_ERROR"));
        }
    }

    /**
     * 创建目录
     */
    @PostMapping("/mkdir")
    public ResponseEntity<FileOperationResponse> createDirectory(@RequestParam String path) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FileOperationResponse.error("MKDIR", "客户端健康状态无效", "HEALTH_CHECK_FAILED"));
        }

        try {
            FileOperationResponse response = fileManagementService.createDirectory(path);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建目录失败: path={}", path, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileOperationResponse.error("MKDIR", "服务器内部错误", "INTERNAL_ERROR"));
        }
    }

    /**
     * 移动文件
     */
    @PostMapping("/move")
    public ResponseEntity<FileOperationResponse> moveFile(
            @RequestParam String sourcePath,
            @RequestParam String targetPath,
            @RequestParam(required = false) Boolean overwrite) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FileOperationResponse.error("MOVE", "客户端健康状态无效", "HEALTH_CHECK_FAILED"));
        }

        try {
            FileOperationResponse response = fileManagementService.moveFile(sourcePath, targetPath, overwrite);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("移动文件失败: sourcePath={}, targetPath={}", sourcePath, targetPath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileOperationResponse.error("MOVE", "服务器内部错误", "INTERNAL_ERROR"));
        }
    }

    /**
     * 复制文件
     */
    @PostMapping("/copy")
    public ResponseEntity<FileOperationResponse> copyFile(
            @RequestParam String sourcePath,
            @RequestParam String targetPath,
            @RequestParam(required = false) Boolean overwrite) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FileOperationResponse.error("COPY", "客户端健康状态无效", "HEALTH_CHECK_FAILED"));
        }

        try {
            FileOperationResponse response = fileManagementService.copyFile(sourcePath, targetPath, overwrite);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("复制文件失败: sourcePath={}, targetPath={}", sourcePath, targetPath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileOperationResponse.error("COPY", "服务器内部错误", "INTERNAL_ERROR"));
        }
    }

    /**
     * 修改文件权限
     */
    @PostMapping("/chmod")
    public ResponseEntity<FileOperationResponse> changePermissions(
            @RequestParam String path,
            @RequestParam String permissions) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FileOperationResponse.error("CHMOD", "客户端健康状态无效", "HEALTH_CHECK_FAILED"));
        }

        try {
            FileOperationResponse response = fileManagementService.changePermissions(path, permissions);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("修改文件权限失败: path={}, permissions={}", path, permissions, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileOperationResponse.error("CHMOD", "服务器内部错误", "INTERNAL_ERROR"));
        }
    }

    /**
     * 预览文件内容
     */
    @GetMapping("/preview")
    public ResponseEntity<FileOperationResponse> previewFile(
            @RequestParam String filePath,
            @RequestParam(required = false) String encoding,
            @RequestParam(required = false) Long maxSize) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FileOperationResponse.error("PREVIEW", "客户端健康状态无效", "HEALTH_CHECK_FAILED"));
        }

        try {
            FileOperationResponse response = fileManagementService.previewFile(filePath, encoding, maxSize);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("预览文件失败: filePath={}", filePath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileOperationResponse.error("PREVIEW", "服务器内部错误", "INTERNAL_ERROR"));
        }
    }

    /**
     * 获取文件信息
     */
    @GetMapping("/info")
    public ResponseEntity<FileOperationResponse> getFileInfo(@RequestParam String path) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FileOperationResponse.error("INFO", "客户端健康状态无效", "HEALTH_CHECK_FAILED"));
        }

        try {
            FileOperationResponse response = fileManagementService.getFileInfo(path);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取文件信息失败: path={}", path, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileOperationResponse.error("INFO", "服务器内部错误", "INTERNAL_ERROR"));
        }
    }

    /**
     * 搜索文件
     */
    @GetMapping("/search")
    public ResponseEntity<FileOperationResponse> searchFiles(
            @RequestParam String path,
            @RequestParam String pattern,
            @RequestParam(required = false) Boolean includeContent,
            @RequestParam(required = false) Integer maxResults) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FileOperationResponse.error("SEARCH", "客户端健康状态无效", "HEALTH_CHECK_FAILED"));
        }

        try {
            FileOperationResponse response = fileManagementService.searchFiles(path, pattern, includeContent, maxResults);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("搜索文件失败: path={}, pattern={}", path, pattern, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileOperationResponse.error("SEARCH", "服务器内部错误", "INTERNAL_ERROR"));
        }
    }

    /**
     * 执行文件操作
     */
    @PostMapping("/operation")
    public ResponseEntity<FileOperationResponse> executeOperation(@RequestBody FileOperationRequest request) {
        
        if (!checkHealth()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(FileOperationResponse.error("OPERATION", "客户端健康状态无效", "HEALTH_CHECK_FAILED"));
        }

        try {
            FileOperationResponse response = fileManagementService.executeOperation(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("执行文件操作失败: operation={}, path={}", 
                request != null ? request.getOperation() : "null",
                request != null ? request.getPath() : "null", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileOperationResponse.error("OPERATION", "服务器内部错误", "INTERNAL_ERROR"));
        }
    }

    /**
     * 检查客户端健康状态
     */
    private boolean checkHealth() {
        boolean healthy = clientHealthService.isHealthy();
        if (!healthy) {
            log.warn("文件管理接口访问被拒绝: 客户端健康状态无效");
        }
        return healthy;
    }
}
