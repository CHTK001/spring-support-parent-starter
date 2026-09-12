package com.chua.starter.filesystem.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.properties.FileStorageUploadProperties;
import com.chua.starter.filesystem.support.server.FileServerManager;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadChunkService;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadService;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
* 文件分片上传接口。
*
* <p>提供分片上传任务的创建、分片上传、合并完成、取消、进度查询 5 个接口，
* 合并完成后由 {@link com.chua.starter.filesystem.support.service.AsyncMergeService}
* 异步调度写入对象存储。</p>
*
* @since 4.0.0.42
* @author CH
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "文件分片上传接口")
public class FileChunkUploadController {

    /**
    * 进度百分比基数
     */
    private static final double PERCENT_BASE = 100.0;

    /**
    * 远端 钥匙 路径分隔符
     */
    private static final String KEY_PATH_SEPARATOR = "/";

    /**
    * 日志记录器
     */
    private static final ModuleLog MODULE_LOG = ModuleLog.of("FileSystem", FileChunkUploadController.class);

    /**
    * 上传任务服务
     */
    private final SysFileSystemUploadService uploadService;

    /**
    * 分片详情服务
     */
    private final SysFileSystemUploadChunkService chunkService;

    /**
    * 文件存储模板
     */
    private final FileStorageTemplate fileStorageTemplate;

    /**
    * 文件存储配置
     */
    private final FileStorageProperties fileStorageProperties;

    /**
    * 分片上传配置 (chunk大小 等)
     */
    private final FileStorageUploadProperties uploadProperties;

    /**
    * 文件服务器管理器 (用于校验 bucket)
     */
    private final FileServerManager fileServerManager;

    /**
    * 上传任务 标识 → 分片 → 本地路径占位 的映射
     */
    private final Map<String, Map<Integer, String>> chunkTempPathMap = new ConcurrentHashMap<>();

    /**
    * 上传任务 标识 → 实时进度 的映射
     */
    private final Map<String, Map<String, Object>> progressMap = new ConcurrentHashMap<>();

    /**
    * 初始化分片上传任务。
    *
    * <p>校验 bucket 是否被 FileServerManager 管理，按 properties 配置的 chunkSize 切分文件，
    * 返回每个分片的 part数字 与 大小。</p>
    *
    * @param fileName    文件名称
    * @param fileSize    文件大小（字节）
    * @param md5         文件 MD5（可选，用于秒传校验）
    * @param bucket      目标 bucket（必须已被 文件服务端管理器 管理）
    * @param contentType 内容类型（可选）
    * @return 包含 uploadid、chunk大小、totalchunks、parts 等信息
     */
    @Operation(summary = "初始化分片上传任务")
    @PostMapping("/chunk/create")
    public ReturnResult<Map<String, Object>> create(
            @Parameter(description = "文件名称") @RequestParam String fileName,
            @Parameter(description = "文件大小(字节)") @RequestParam Long fileSize,
            @Parameter(description = "文件MD5") @RequestParam(required = false) String md5,
            @Parameter(description = "目标bucket") @RequestParam(defaultValue = "default") String bucket,
            @Parameter(description = "内容类型") @RequestParam(required = false) String contentType
    ) {
        // 入参校验
        if (fileName == null || fileName.isEmpty()) {
            return ReturnResult.illegal("文件名称不能为空");
        }
        if (fileSize == null || fileSize <= 0) {
            return ReturnResult.illegal("文件大小必须大于0");
        }

 // 从 属性 读取分片大小（不允许客户端覆盖）
        long chunkSize = uploadProperties.getChunkSize();
        if (chunkSize <= 0) {
            return ReturnResult.illegal("分片大小配置错误: " + chunkSize);
        }

 // 校验 bucket 是否被 文件服务端管理器 管理（只有启动成功的 服务端 才计入 服务端映射）
        if (!fileServerManager.isRunning(bucket)) {
            return ReturnResult.illegal("bucket 不存在或未启动: " + bucket);
        }

        // 计算总分片数与每个分片大小
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
        if (totalChunks <= 0) {
            totalChunks = 1;
        }

 // 生成上传任务 标识 与默认 钥匙
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String finalKey = "uploads/" + uploadId + "/" + fileName;

        // 构造每个分片的信息
        List<Map<String, Object>> parts = new java.util.ArrayList<>();
        long remaining = fileSize;
        for (int i = 1; i <= totalChunks; i++) {
            long size = Math.min(chunkSize, remaining);
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("partNumber", i);
            part.put("size", size);
            part.put("uploadId", uploadId);
            parts.add(part);
            remaining -= size;
        }

        // 持久化上传任务
        uploadService.createTask(uploadId, fileName, fileName, fileSize, chunkSize, totalChunks,
                bucket, bucket, finalKey, contentType);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uploadId", uploadId);
        data.put("chunkSize", chunkSize);
        data.put("totalChunks", totalChunks);
        data.put("fileSize", fileSize);
        data.put("key", finalKey);
        data.put("bucket", bucket);
        data.put("md5", md5);
        data.put("parts", parts);
        MODULE_LOG.info("初始化分片上传: {} {} ({} parts, bucket={})", fileName, uploadId, totalChunks, bucket);
        return ReturnResult.ok(data);
    }

    /**
    * 上传单个分片。
    *
    * @param uploadId   上传任务 标识
    * @param partNumber 分片序号（1-based）
    * @param file       分片文件
    * @return 上传结果
     */
    @Operation(summary = "上传分片")
    @PostMapping("/chunk/{uploadId}/part")
    public ReturnResult<Map<String, Object>> uploadPart(
            @Parameter(description = "上传任务ID") @PathVariable String uploadId,
            @Parameter(description = "分片序号(1-based)") @RequestParam Integer partNumber,
            @Parameter(description = "分片文件") @RequestParam("file") MultipartFile file
    ) {
        if (uploadId == null || uploadId.isEmpty()) {
            return ReturnResult.illegal("uploadId不能为空");
        }
        if (partNumber == null || partNumber < 1) {
            return ReturnResult.illegal("分片序号必须大于等于1");
        }
        try {
            // 校验上传任务存在
            SysFileSystemUpload task = uploadService.getByUploadId(uploadId);
            if (task == null) {
                return ReturnResult.illegal("上传任务不存在或已过期，请重新创建");
            }

            // 将分片直接写入临时目录
            byte[] content = file.getBytes();
            String baseDir = Optional.ofNullable(System.getProperty("filesystem.temp.path"))
                    .orElse(uploadProperties.getTempDir());
            java.nio.file.Path basePath = java.nio.file.Paths.get(baseDir);
            java.nio.file.Path partPath;
            try {
                java.nio.file.Files.createDirectories(basePath);
                partPath = basePath.resolve(uploadId + "_" + partNumber);
                java.nio.file.Files.write(partPath, content);
            } catch (java.io.IOException e) {
                MODULE_LOG.error("写入分片失败: {}", uploadId, e);
                return ReturnResult.illegal("写入分片失败: " + e.getMessage());
            }

            String etag = "etag-" + partNumber;
            // 存储分片完整磁盘路径，供合并调度直接读取
            chunkService.saveChunk(uploadId, partNumber, (long) content.length, etag, partPath.toString());

            // 更新任务状态与已上传分片数
            int uploaded = task.getFilesystemUploadTaskUploadedChunks() + 1;
            int total = task.getFilesystemUploadTaskTotalChunks();
            int nextStatus = uploaded >= total
                    ? SysFileSystemUpload.Status.PENDING_MERGE
                    : SysFileSystemUpload.Status.UPLOADING;
            uploadService.updateStatus(uploadId, nextStatus);
            uploadService.updateUploadedChunks(uploadId, uploaded);

            // 刷新内存进度
            Map<String, Object> progress = progressMap.computeIfAbsent(uploadId, k -> new HashMap<>());
            progress.put("uploadedChunks", uploaded);
            progress.put("totalChunks", total);
            progress.put("status", nextStatus);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uploadId", uploadId);
            data.put("partNumber", partNumber);
            data.put("partSize", content.length);
            data.put("etag", etag);
            return ReturnResult.ok(data);
        } catch (Exception e) {
            MODULE_LOG.error("上传分片失败: {} part={}", uploadId, partNumber, e);
            return ReturnResult.illegal("上传分片失败: " + e.getMessage());
        }
    }

    /**
    * 完成分片上传。
    *
    * <p>校验分片齐全后，将任务入队为"待合并"阶段，由异步合并调度与上传调度
    * 依次执行合并与对象存储上传。</p>
    *
    * @param uploadId 上传任务 标识
    * @return 入队结果
     */
    @Operation(summary = "完成分片上传")
    @PostMapping("/chunk/{uploadId}/complete")
    public ReturnResult<Map<String, Object>> complete(
            @Parameter(description = "上传任务ID") @PathVariable String uploadId
    ) {
        if (uploadId == null || uploadId.isEmpty()) {
            return ReturnResult.illegal("uploadId不能为空");
        }
        try {
            SysFileSystemUpload task = uploadService.getByUploadId(uploadId);
            if (task == null) {
                return ReturnResult.illegal("上传任务不存在");
            }

            int uploadedChunks = task.getFilesystemUploadTaskUploadedChunks();
            int totalChunks = task.getFilesystemUploadTaskTotalChunks();
            if (uploadedChunks < totalChunks) {
                uploadService.markFailed(uploadId, "分片数量不足，期望: " + totalChunks + " 实际: " + uploadedChunks);
                return ReturnResult.illegal("分片数量不足，请上传全部分片后再完成");
            }

            // 入队合并任务：切到待合并阶段，由异步合并调度执行
            uploadService.updateStage(uploadId, SysFileSystemUpload.Stage.MERGE);
            uploadService.updateStatus(uploadId, SysFileSystemUpload.Status.PENDING_MERGE);
            progressMap.remove(uploadId);
            chunkTempPathMap.remove(uploadId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uploadId", uploadId);
            data.put("status", SysFileSystemUpload.Status.PENDING_MERGE);
            data.put("key", task.getFilesystemUploadTaskKey());
            data.put("fileName", task.getFilesystemUploadTaskFileName());
            data.put("message", "已入队，等待异步合并与上传");
            MODULE_LOG.info("分片上传完成，已入队合并: {}", uploadId);
            return ReturnResult.ok(data);
        } catch (Exception e) {
            MODULE_LOG.error("完成分片上传失败: {}", uploadId, e);
            uploadService.markFailed(uploadId, "完成上传失败: " + e.getMessage());
            return ReturnResult.illegal("完成上传失败: " + e.getMessage());
        }
    }

    /**
    * 取消分片上传。
    *
    * @param uploadId 上传任务 标识
    * @return 空结果
     */
    @Operation(summary = "取消分片上传")
    @DeleteMapping("/chunk/{uploadId}")
    public ReturnResult<Void> cancel(
            @Parameter(description = "上传任务ID") @PathVariable String uploadId
    ) {
        if (uploadId == null || uploadId.isEmpty()) {
            return ReturnResult.illegal("uploadId不能为空");
        }
        try {
            SysFileSystemUpload task = uploadService.getByUploadId(uploadId);
            // 清理临时分片（如果有）
            if (task != null) {
                /**
                * cleanuptempchunks。
                * @param uploadId uploadid
                * @param task.getFilesystemUploadTaskTotalChunks( 任务.getfilesystemupload任务totalchunks(
                 */
                cleanupTempChunks(uploadId, task.getFilesystemUploadTaskTotalChunks());
            }
            uploadService.markFailed(uploadId, "用户取消上传");
            progressMap.remove(uploadId);
            chunkTempPathMap.remove(uploadId);

            if (task != null && task.getFilesystemUploadTaskStatus() == SysFileSystemUpload.Status.PENDING) {
                uploadService.updateStatus(uploadId, SysFileSystemUpload.Status.CANCELLED);
            }
            MODULE_LOG.info("取消分片上传: {}", uploadId);
            return ReturnResult.ok();
        } catch (Exception e) {
            MODULE_LOG.error("取消上传失败: {}", uploadId, e);
            return ReturnResult.illegal("取消失败: " + e.getMessage());
        }
    }

    /**
    * 查询上传进度。
    *
    * @param uploadId 上传任务 标识
    * @return 进度信息
     */
    @Operation(summary = "查询上传进度")
    @GetMapping("/chunk/{uploadId}/progress")
    public ReturnResult<Map<String, Object>> progress(
            @Parameter(description = "上传任务ID") @PathVariable String uploadId
    ) {
        if (uploadId == null || uploadId.isEmpty()) {
            return ReturnResult.illegal("uploadId不能为空");
        }
        SysFileSystemUpload task = uploadService.getByUploadId(uploadId);
        if (task == null) {
            return ReturnResult.illegal("上传任务不存在");
        }

        Map<String, Object> progress = progressMap.get(uploadId);
        int uploadedChunks = task.getFilesystemUploadTaskUploadedChunks();
        int totalChunks = task.getFilesystemUploadTaskTotalChunks();
        if (progress != null && progress.containsKey("uploadedChunks")) {
            uploadedChunks = (int) progress.get("uploadedChunks");
        }

        long percent = 0;
        if (totalChunks > 0) {
            percent = Math.round((uploadedChunks * PERCENT_BASE) / totalChunks);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uploadId", uploadId);
        data.put("status", task.getFilesystemUploadTaskStatus());
        data.put("statusText", getStatusText(task.getFilesystemUploadTaskStatus()));
        data.put("uploadedChunks", uploadedChunks);
        data.put("totalChunks", totalChunks);
        data.put("percent", percent);
        data.put("fileSize", task.getFilesystemUploadTaskFileSize());
        data.put("chunkSize", task.getFilesystemUploadTaskChunkSize());

        if (task.getFilesystemUploadTaskStatus() == SysFileSystemUpload.Status.COMPLETED) {
            data.put("url", task.getFilesystemUploadTaskFileUrl());
            data.put("key", task.getFilesystemUploadTaskKey());
        }
        return ReturnResult.ok(data);
    }

    /**
    * 状态码 → 文本。
    *
    * @param status 状态码
    * @return 中文文本
     */
    private String getStatusText(int status) {
        return switch (status) {
            case SysFileSystemUpload.Status.PENDING -> "待上传";
            case SysFileSystemUpload.Status.UPLOADING -> "上传中";
            case SysFileSystemUpload.Status.PENDING_MERGE -> "待合并";
            case SysFileSystemUpload.Status.MERGING -> "合并中";
            case SysFileSystemUpload.Status.COMPLETED -> "已完成";
            case SysFileSystemUpload.Status.FAILED -> "失败";
            case SysFileSystemUpload.Status.CANCELLED -> "已取消";
            default -> "未知";
        };
    }

    /**
    * 清理临时分片文件。
    *
    * @param uploadId    上传任务 标识
    * @param totalChunks 总分片数
     */
    private void cleanupTempChunks(String uploadId, int totalChunks) {
        String baseDir = Optional.ofNullable(System.getProperty("filesystem.temp.path"))
                .orElse(uploadProperties.getTempDir());
        java.nio.file.Path basePath = java.nio.file.Paths.get(baseDir);
        for (int i = 1; i <= totalChunks; i++) {
            try {
                java.nio.file.Files.deleteIfExists(basePath.resolve(uploadId + "_" + i));
            } catch (java.io.IOException e) {
                MODULE_LOG.warn("清理分片失败: {}_{}", uploadId, i, e);
            }
        }
    }
}
