package com.chua.starter.filesystem.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.storage.MultipartStorage;
import com.chua.common.support.storage.PartETag;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.result.MultipartPartResult;
import com.chua.common.support.storage.result.PutObjectResult;
import com.chua.common.support.storage.request.PutObjectRequest;
import com.chua.common.support.storage.request.MultipartUploadPartRequest;
import com.chua.common.support.storage.metadata.Metadata;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadService;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadChunkService;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequiredArgsConstructor
@Tag(name = "文件分片上传接口")
public class FileChunkUploadController {

    private static final Logger LOG = LoggerFactory.getLogger(FileChunkUploadController.class);
    private static final ModuleLog MODULE_LOG = ModuleLog.of("FileSystem", FileChunkUploadController.class);

    private final SysFileSystemUploadService uploadService;
    private final SysFileSystemUploadChunkService chunkService;
    private final FileStorageTemplate fileStorageTemplate;
    private final FileStorageProperties fileStorageProperties;

    private final Map<String, MultipartStorage> multipartStorageMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> progressMap = new ConcurrentHashMap<>();

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
        if (fileName == null || fileName.isEmpty()) {
            return ReturnResult.illegal("文件名称不能为空");
        }
        if (fileSize == null || fileSize <= 0) {
            return ReturnResult.illegal("文件大小必须大于0");
        }
        if (chunkSize == null || chunkSize <= 0) {
            chunkSize = 5 * 1024 * 1024L;
        }

        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
        if (totalChunks <= 0) {
            totalChunks = 1;
        }

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String finalKey = key;
        if (finalKey == null || finalKey.isEmpty()) {
            finalKey = "uploads/" + uploadId + "/" + fileName;
        }
        if (originalName == null || originalName.isEmpty()) {
            originalName = fileName;
        }

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .fileName(fileName)
                .filePath(null)
                .metadata(Metadata.builder()
                        .contentType(contentType)
                        .build())
                .build();

        FileStorage fileStorage = getDefaultFileStorage();
        if (fileStorage == null) {
            return ReturnResult.illegal("存储不存在: " + storageType + "/" + bucket);
        }
        MultipartStorage multipartStorage = fileStorage.createMultipartStorage();
        multipartStorageMap.put(uploadId, multipartStorage);
        MultipartPartResult result = multipartStorage.initiate(putRequest);
        if (result.getResultCode() != com.chua.common.support.storage.result.ObjectResult.ResultCode.SUCCESS) {
            multipartStorageMap.remove(uploadId);
            return ReturnResult.illegal(result.getMessage());
        }

        String realUploadId = result.getUploadId();
        if (realUploadId != null && !realUploadId.isEmpty()) {
            multipartStorageMap.remove(uploadId);
            multipartStorageMap.put(realUploadId, multipartStorage);
            uploadId = realUploadId;
        }

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

            String storagePath = partResult.getStoragePath();
            chunkService.saveChunk(uploadId, partNumber, partResult.getPartSize(), partResult.getEtag(), storagePath);

            SysFileSystemUpload task = uploadService.getByUploadId(uploadId);
            if (task != null) {
                int uploaded = task.getFilesystemUploadTaskUploadedChunks() + 1;
                int total = task.getFilesystemUploadTaskTotalChunks();
                if (uploaded >= total) {
                    uploadService.updateStatus(uploadId, SysFileSystemUpload.Status.PENDING_MERGE);
                } else {
                    uploadService.updateStatus(uploadId, SysFileSystemUpload.Status.UPLOADING);
                }
                uploadService.updateUploadedChunks(uploadId, uploaded);
            }

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
            LOG.error("上传分片失败: {} part={}", uploadId, partNumber, e);
            return ReturnResult.illegal("上传分片失败: " + e.getMessage());
        }
    }

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

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uploadId", uploadId);
            data.put("url", fileUrl);
            data.put("key", task.getFilesystemUploadTaskKey());
            data.put("fileName", task.getFilesystemUploadTaskFileName());
            MODULE_LOG.info("分片上传完成: {} -> {}", task.getFilesystemUploadTaskFileName(), fileUrl);
            return ReturnResult.ok(data);
        } catch (Exception e) {
            LOG.error("完成分片上传失败: " + uploadId, e);
            uploadService.markFailed(uploadId, "完成上传失败: " + e.getMessage());
            return ReturnResult.illegal("完成上传失败: " + e.getMessage());
        }
    }

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

            SysFileSystemUpload task = uploadService.getByUploadId(uploadId);
            if (task != null && task.getFilesystemUploadTaskStatus() == SysFileSystemUpload.Status.PENDING) {
                uploadService.updateStatus(uploadId, SysFileSystemUpload.Status.CANCELLED);
            }
            MODULE_LOG.info("取消分片上传: {}", uploadId);
            return ReturnResult.ok();
        } catch (Exception e) {
            LOG.error("取消上传失败: " + uploadId, e);
            return ReturnResult.illegal("取消失败: " + e.getMessage());
        }
    }

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

        double percent = 0;
        if (totalChunks > 0) {
            percent = Math.round((uploadedChunks * 100.0) / totalChunks);
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

    private String getStatusText(int status) {
        return switch (status) {
            case 0 -> "待上传";
            case 1 -> "上传中";
            case 2 -> "待合并";
            case 3 -> "合并中";
            case 4 -> "已完成";
            case -1 -> "失败";
            case -2 -> "已取消";
            default -> "未知";
        };
    }

    private FileStorage getDefaultFileStorage() {
        FileStorage storage = fileStorageTemplate.getDefaultStorage();
        if (storage == null) {
            return new com.chua.common.support.storage.AbstractFileStorage(
                    com.chua.common.support.storage.setting.BucketSetting.builder().bucket("default").build()) {
                @Override
                public com.chua.common.support.storage.result.GetObjectResult getObject(String key) { return null; }
                @Override
                public com.chua.common.support.storage.result.GetObjectResult getObject(com.chua.common.support.storage.request.GetObjectRequest r) { return null; }
                @Override
                public com.chua.common.support.storage.result.PutObjectResult putObject(com.chua.common.support.storage.request.PutObjectRequest r) { return null; }
                @Override
                public com.chua.common.support.storage.result.DeleteObjectResult deleteObject(String key) { return null; }
                @Override
                public com.chua.common.support.storage.result.ExistObjectResult existObject(com.chua.common.support.storage.request.ExistObjectRequest r) { return null; }
                @Override
                public com.chua.common.support.storage.result.ListObjectResult listObject(com.chua.common.support.storage.request.ListObjectRequest r) { return null; }
            };
        }
        return storage;
    }
}
