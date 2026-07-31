package com.chua.starter.filesystem.support.service.impl;

import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.PartETag;
import com.chua.common.support.storage.request.PutObjectRequest;
import com.chua.common.support.storage.result.ObjectResult;
import com.chua.common.support.storage.result.PutObjectResult;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;
import com.chua.starter.filesystem.support.entity.SysFileSystemUploadChunk;
import com.chua.starter.filesystem.support.service.AsyncMergeService;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadChunkService;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadFileService;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadService;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncMergeServiceImpl implements AsyncMergeService {

    private final SysFileSystemUploadService uploadService;
    private final SysFileSystemUploadChunkService chunkService;
    private final SysFileSystemUploadFileService fileService;
    private final FileStorageTemplate storageTemplate;

    private static final int MAX_RETRY = 3;

    @Override
    @Scheduled(fixedDelay = 10000)
    public void mergePendingTasks() {
        List<SysFileSystemUpload> tasks = uploadService.listPendingMerge();
        for (SysFileSystemUpload task : tasks) {
            try {
                mergeTask(task);
            } catch (Exception e) {
                log.error("异步合并失败: {}", task.getFilesystemUploadTaskUploadId(), e);
                uploadService.markFailed(task.getFilesystemUploadTaskUploadId(), "合并失败: " + e.getMessage());
            }
        }
    }

    @Override
    @Scheduled(fixedDelay = 60000)
    public void retryFailedTasks() {
        List<SysFileSystemUpload> tasks = uploadService.listFailedRetryable();
        for (SysFileSystemUpload task : tasks) {
            try {
                mergeTask(task);
            } catch (Exception e) {
                log.error("重试合并失败: {}", task.getFilesystemUploadTaskUploadId(), e);
            }
        }
    }

    @Transactional
    protected void mergeTask(SysFileSystemUpload task) {
        String uploadId = task.getFilesystemUploadTaskUploadId();
        uploadService.updateStatus(uploadId, SysFileSystemUpload.Status.MERGING);

        List<SysFileSystemUploadChunk> chunks = chunkService.listByUploadId(uploadId);
        if (chunks.size() < task.getFilesystemUploadTaskTotalChunks()) {
            uploadService.markFailed(uploadId, "分片不完整: " + chunks.size() + "/" + task.getFilesystemUploadTaskTotalChunks());
            return;
        }
        byte[] merged = mergeChunks(chunks);
        if (merged == null) {
            uploadService.markFailed(uploadId, "合并分片数据失败");
            return;
        }
        String bucket = task.getFilesystemUploadTaskBucket();
        String key = task.getFilesystemUploadTaskKey();
        String storageType = task.getFilesystemUploadTaskStorageType();
        FileStorage storage = storageTemplate.getStorage(storageType);
        if (storage == null) {
            uploadService.markFailed(uploadId, "存储不存在: " + storageType);
            return;
        }
        PutObjectResult result = storage.putObject(PutObjectRequest.builder()
                .content(merged)
                .fileName(key != null && key.contains("/") ? key.substring(key.lastIndexOf("/") + 1) : key)
                .filePath(key != null && key.contains("/") ? key.substring(0, key.lastIndexOf("/")) : null)
                .build());
        if (result.getResultCode() != ObjectResult.ResultCode.SUCCESS) {
            int retry = task.getFilesystemUploadTaskRetryCount() != null ? task.getFilesystemUploadTaskRetryCount() + 1 : 1;
            task.setFilesystemUploadTaskRetryCount(retry);
            uploadService.updateById(task);
            uploadService.markFailed(uploadId, "上传到bucket失败: " + result.getMessage());
            return;
        }
        chunkService.markMerged(uploadId);
        fileService.createFromTask(uploadId, result.getUrl(), result.getEtag());
        uploadService.markCompleted(uploadId, result.getUrl());
        log.info("异步合并完成: {} -> {}", uploadId, result.getUrl());
    }

    private byte[] mergeChunks(List<SysFileSystemUploadChunk> chunks) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (SysFileSystemUploadChunk chunk : chunks) {
            String path = chunk.getFilesystemUploadChunkStoragePath();
            if (path == null || path.isEmpty()) {
                log.error("分片存储路径为空: {}", chunk.getFilesystemUploadChunkId());
                return null;
            }
            try {
                baos.write(IoUtils.toByteArray(new File(path)));
            } catch (IOException e) {
                log.error("读取分片失败: {}", chunk.getFilesystemUploadChunkId(), e);
                return null;
            }
        }
        return baos.toByteArray();
    }
}
