package com.chua.starter.filesystem.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.MultipartStorage;
import com.chua.common.support.storage.PartETag;
import com.chua.common.support.storage.metadata.Metadata;
import com.chua.common.support.storage.request.MultipartUploadPartRequest;
import com.chua.common.support.storage.request.PutObjectRequest;
import com.chua.common.support.storage.result.MultipartPartResult;
import com.chua.common.support.storage.result.PutObjectResult;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件分片上传接口。
 *
 * <p>提供分片上传任务的创建、分片上传、合并完成、取消、进度查询 5 个接口，
 * 合并完成后由 {@link com.chua.starter.filesystem.support.service.AsyncMergeService}
 * 异步调度写入对象存储。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "文件分片上传接口")
public class FileChunkUploadController {

    /**
     * 默认分片大小（字节）
     */
    private static final long DEFAULT_CHUNK_SIZE = 5L * 1024L * 1024L;

    /**
     * 进度百分比基数
     */
    private static final double PERCENT_BASE = 100.0;

    /**
     * 远端 Key 路径分隔符
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
    @SuppressWarnings("unused")
    private final FileStorageProperties fileStorageProperties;

    /**
     * 上传任务 ID → MultipartStorage 实例 的映射
     */
    private final Map<String, MultipartStorage> multipartStorageMap = new ConcurrentHashMap<>();

    /**
     * 上传任务 ID → 分片 → 本地路径占位 的映射
     */
    private final Map<String, Map<Integer, String>> chunkTempPathMap = new ConcurrentHashMap<>();

    /**
     * 上传任务 ID → 实时进度 的映射
     */
    private final Map<String, Map<String, Object>> progressMap = new ConcurrentHashMap<>();

    /**
     * 初始化分片上传任务。
     *
     * @param fileName     文件名称
     * @param originalName 原始文件名
     * @param fileSize     文件大小（字节）
     * @param chunkSize    分片大小（字节），默认 5MB
     * @param storageType  存储类型
     * @param bucket       存储桶
     * @param key          文件 Key
     * @param contentType  内容类型
     * @return 包含 uploadId/totalChunks 等信息的结果
     */
    @Operation(summary = "初始化分片上传任务")
    @PostMapping("/chunk/create")
    public ReturnResult<Map<String, Object>> create(
            @Parameter(description = "文件名称") @RequestParam String fileName,
            @Parameter(description = "原始文件名") @RequestParam(required = false) String originalName,
            @Parameter(description = "文件大小(字节)") @RequestParam Long fileSize,
            @Parameter(description = "分片大小(字节)") @RequestParam(defaultValue = "5242880") Long chunkSize,
            @Parameter(description = "存储类型") @RequestParam(defaultValue = "local") String storageType,
            @Parameter(description = "存储桶") @RequestParam(defaultValue = "default") String bucket,
            @Parameter(description = "文件Key") @RequestParam(required = false) String key,
            @Parameter(description = "内容类型") @RequestParam(required = false) String contentType
    ) {
        // 入参校验
        if (fileName == null || fileName.isEmpty()) {
            return ReturnResult.illegal("文件名称不能为空");
        }
        if (fileSize == null || fileSize <= 0) {
            return ReturnResult.illegal("文件大小必须大于0");
        }
        if (chunkSize == null || chunkSize <= 0) {
            chunkSize = DEFAULT_CHUNK_SIZE;
        }

        // 计算总分片数
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
        if (totalChunks <= 0) {
            totalChunks = 1;
        }

        // 生成上传任务 ID 与默认 Key
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String finalKey = key;
        if (finalKey == null || finalKey.isEmpty()) {
            finalKey = "uploads/" + uploadId + "/" + fileName;
        }
        if (originalName == null || originalName.isEmpty()) {
            originalName = fileName;
        }

        // 构造分片上传会话
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .fileName(fileName)
                .filePath(null)
                .metadata(Metadata.builder().contentType(contentType).build())
                .build();

        FileStorage fileStorage = resolveStorage(storageType);
        if (fileStorage == null) {
            return ReturnResult.illegal("存储不存在: " + storageType + "/" + bucket);
        }
        MultipartStorage multipartStorage = fileStorage.createMultipartStorage();
        multipartStorageMap.put(uploadId, multipartStorage);

        // 初始化远端分片上传任务
        MultipartPartResult result = multipartStorage.initiate(putRequest);
        if (result.getResultCode() != com.chua.common.support.storage.result.ObjectResult.ResultCode.SUCCESS) {
            multipartStorageMap.remove(uploadId);
            return ReturnResult.illegal(result.getMessage());
        }

        // 若远端返回了自己的 uploadId，替换本地标识
        String remoteUploadId = result.getUploadId();
        if (remoteUploadId != null && !remoteUploadId.isEmpty()) {
            multipartStorageMap.remove(uploadId);
            multipartStorageMap.put(remoteUploadId, multipartStorage);
            uploadId = remoteUploadId;
        }

        // 持久化上传任务
        uploadService.createTask(uploadId, fileName, originalName, fileSize, chunkSize, totalChunks,
                storageType, bucket, finalKey, contentType);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uploadId", uploadId);
        data.put("chunkSize", chunkSize);
        data.put("totalChunks", totalChunks);
        data.put("fileSize", fileSize);
        data.put("key", finalKey);
        MODULE_LOG.info("初始化分片上传: {} {}", fileName, uploadId);
        return ReturnResult.ok(data);
    }

    /**
     * 上传单个分片。
     *
     * @param uploadId   上传任务 ID
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
            MultipartStorage multipartStorage = multipartStorageMap.get(uploadId);
            if (multipartStorage == null) {
                return ReturnResult.illegal("上传任务不存在或已过期，请重新创建");
            }

            MultipartUploadPartRequest partRequest = MultipartUploadPartRequest.builder()
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .content(file.getBytes())
                    .isLastPart(false)
                    .build();

            MultipartPartResult partResult = multipartStorage.uploadPart(partRequest);
            if (partResult.getResultCode() != com.chua.common.support.storage.result.ObjectResult.ResultCode.SUCCESS) {
                return ReturnResult.illegal(partResult.getMessage());
            }

            // 分片本地路径由 MultipartStorage 内部维护，此处仅记录 partNumber 用于回溯
            Map<Integer, String> chunkPathMap = chunkTempPathMap.computeIfAbsent(uploadId, k -> new HashMap<>());
            chunkPathMap.put(partNumber, uploadId + KEY_PATH_SEPARATOR + partNumber);
            String storagePath = partNumber.toString();
            chunkService.saveChunk(uploadId, partNumber, partResult.getPartSize(), partResult.getEtag(), storagePath);

            // 更新任务状态与已上传分片数
            SysFileSystemUpload task = uploadService.getByUploadId(uploadId);
            if (task != null) {
                int uploaded = task.getFilesystemUploadTaskUploadedChunks() + 1;
                int total = task.getFilesystemUploadTaskTotalChunks();
                int nextStatus = uploaded >= total
                        ? SysFileSystemUpload.Status.PENDING_MERGE
                        : SysFileSystemUpload.Status.UPLOADING;
                uploadService.updateStatus(uploadId, nextStatus);
                uploadService.updateUploadedChunks(uploadId, uploaded);
            }

            // 刷新内存进度
            Map<String, Object> progress = progressMap.computeIfAbsent(uploadId, k -> new HashMap<>());
            progress.put("uploadedChunks", task != null ? task.getFilesystemUploadTaskUploadedChunks() : partNumber);
            progress.put("totalChunks", task != null ? task.getFilesystemUploadTaskTotalChunks() : 1);
            progress.put("status", SysFileSystemUpload.Status.UPLOADING);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uploadId", uploadId);
            data.put("partNumber", partNumber);
            data.put("partSize", partResult.getPartSize());
            data.put("etag", partResult.getEtag());
            return ReturnResult.ok(data);
        } catch (Exception e) {
            MODULE_LOG.error("上传分片失败: {} part={}", uploadId, partNumber, e);
            return ReturnResult.illegal("上传分片失败: " + e.getMessage());
        }
    }

    /**
     * 完成分片上传。
     *
     * @param uploadId 上传任务 ID
     * @return 包含最终文件 URL 的结果
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

            MultipartStorage multipartStorage = multipartStorageMap.remove(uploadId);
            if (multipartStorage == null) {
                return ReturnResult.illegal("上传会话已过期，请重新上传");
            }

            int uploadedChunks = task.getFilesystemUploadTaskUploadedChunks();
            int totalChunks = task.getFilesystemUploadTaskTotalChunks();
            if (uploadedChunks < totalChunks) {
                uploadService.markFailed(uploadId, "分片数量不足，期望: " + totalChunks + " 实际: " + uploadedChunks);
                return ReturnResult.illegal("分片数量不足，请上传全部分片后再完成");
            }

            List<PartETag> parts = new ArrayList<>();
            for (int i = 1; i <= totalChunks; i++) {
                parts.add(new PartETag(i, null));
            }
            PutObjectResult putResult = multipartStorage.complete(uploadId, parts);
            if (putResult.getResultCode() != com.chua.common.support.storage.result.ObjectResult.ResultCode.SUCCESS) {
                uploadService.markFailed(uploadId, putResult.getMessage());
                return ReturnResult.illegal(putResult.getMessage());
            }

            String fileUrl = putResult.getUrl();
            uploadService.markCompleted(uploadId, fileUrl);
            progressMap.remove(uploadId);
            chunkTempPathMap.remove(uploadId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uploadId", uploadId);
            data.put("url", fileUrl);
            data.put("key", task.getFilesystemUploadTaskKey());
            data.put("fileName", task.getFilesystemUploadTaskFileName());
            MODULE_LOG.info("分片上传完成: {} -> {}", task.getFilesystemUploadTaskFileName(), fileUrl);
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
     * @param uploadId 上传任务 ID
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
            MultipartStorage multipartStorage = multipartStorageMap.remove(uploadId);
            if (multipartStorage != null) {
                multipartStorage.abort(uploadId);
            }
            uploadService.markFailed(uploadId, "用户取消上传");
            progressMap.remove(uploadId);
            chunkTempPathMap.remove(uploadId);

            SysFileSystemUpload task = uploadService.getByUploadId(uploadId);
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
     * @param uploadId 上传任务 ID
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
     * 解析存储后端名称，未匹配时回退到默认存储。
     *
     * @param storageType 存储类型
     * @return FileStorage 实例
     */
    private FileStorage resolveStorage(String storageType) {
        FileStorage storage = fileStorageTemplate.getStorage(storageType);
        if (storage != null) {
            return storage;
        }
        return fileStorageTemplate.getDefaultStorage();
    }
}
