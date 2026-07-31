package com.chua.starter.filesystem.support.service.impl;

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
import java.nio.file.Files;
import java.util.List;

/**
 * 异步合并调度服务实现。
 *
 * <p>负责每 10 秒扫描待合并任务，每 60 秒重试失败任务（最多 {@value #MAX_RETRY} 次），
 * 合并完成后调用存储后端的 putObject 上传最终文件并写入文件主体记录。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncMergeServiceImpl implements AsyncMergeService {

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY = 3;

    /**
     * 扫描待合并任务的周期（毫秒）
     */
    private static final long SCAN_PENDING_INTERVAL_MS = 10_000L;

    /**
     * 重试失败任务的周期（毫秒）
     */
    private static final long SCAN_RETRY_INTERVAL_MS = 60_000L;

    /**
     * 初始重试计数
     */
    private static final int INITIAL_RETRY_COUNT = 1;

    /**
     * 上传任务服务
     */
    private final SysFileSystemUploadService uploadService;

    /**
     * 分片详情服务
     */
    private final SysFileSystemUploadChunkService chunkService;

    /**
     * 文件主体服务
     */
    private final SysFileSystemUploadFileService fileService;

    /**
     * 文件存储模板
     */
    private final FileStorageTemplate storageTemplate;

    @Override
    @Scheduled(fixedDelay = SCAN_PENDING_INTERVAL_MS)
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
    @Scheduled(fixedDelay = SCAN_RETRY_INTERVAL_MS)
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

    /**
     * 合并单个上传任务。
     *
     * <p>流程：状态切换 → 分片校验 → 字节合并 → 写入对象存储 → 标记分片已合并 → 写入文件主体 → 标记任务完成。</p>
     *
     * @param task 待合并的上传任务
     */
    @Transactional
    protected void mergeTask(SysFileSystemUpload task) {
        String uploadId = task.getFilesystemUploadTaskUploadId();

        // 切到合并中状态，防止重复处理
        uploadService.updateStatus(uploadId, SysFileSystemUpload.Status.MERGING);

        // 校验分片数量
        List<SysFileSystemUploadChunk> chunks = chunkService.listByUploadId(uploadId);
        if (chunks.size() < task.getFilesystemUploadTaskTotalChunks()) {
            uploadService.markFailed(uploadId, "分片不完整: " + chunks.size() + "/" + task.getFilesystemUploadTaskTotalChunks());
            return;
        }

        // 合并字节
        byte[] merged = mergeChunks(chunks);
        if (merged == null) {
            uploadService.markFailed(uploadId, "合并分片数据失败");
            return;
        }

        // 选择存储后端
        String storageType = task.getFilesystemUploadTaskStorageType();
        var storage = storageTemplate.getStorage(storageType);
        if (storage == null) {
            uploadService.markFailed(uploadId, "存储不存在: " + storageType);
            return;
        }

        // 构造上传请求（仅本地分片合并场景：使用临时 key 作为远程路径）
        String key = task.getFilesystemUploadTaskKey();
        String fileName = extractFileName(key);
        String filePath = extractFilePath(key);
        var putRequest = com.chua.common.support.storage.request.PutObjectRequest.builder()
                .content(merged)
                .fileName(fileName)
                .filePath(filePath)
                .build();

        // 上传到对象存储
        var result = storage.putObject(putRequest);
        if (result.getResultCode() != com.chua.common.support.storage.result.ObjectResult.ResultCode.SUCCESS) {
            int retry = task.getFilesystemUploadTaskRetryCount() != null
                    ? task.getFilesystemUploadTaskRetryCount() + INITIAL_RETRY_COUNT
                    : INITIAL_RETRY_COUNT;
            task.setFilesystemUploadTaskRetryCount(retry);
            uploadService.updateById(task);
            uploadService.markFailed(uploadId, "上传到bucket失败: " + result.getMessage());
            return;
        }

        // 持久化合并结果
        chunkService.markMerged(uploadId);
        fileService.createFromTask(uploadId, result.getUrl(), null);
        uploadService.markCompleted(uploadId, result.getUrl());
        log.info("异步合并完成: {} -> {}", uploadId, result.getUrl());
    }

    /**
     * 将分片按序合并为完整字节数组。
     *
     * @param chunks 已上传的分片列表（已按 partNumber 升序）
     * @return 合并后的字节数组；任一分片缺失或读取失败返回 null
     */
    private byte[] mergeChunks(List<SysFileSystemUploadChunk> chunks) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (SysFileSystemUploadChunk chunk : chunks) {
            String path = chunk.getFilesystemUploadChunkStoragePath();
            if (path == null || path.isEmpty()) {
                log.error("分片存储路径为空: {}", chunk.getFilesystemUploadChunkId());
                return null;
            }
            try {
                baos.write(Files.readAllBytes(new File(path).toPath()));
            } catch (IOException e) {
                log.error("读取分片失败: {}", chunk.getFilesystemUploadChunkId(), e);
                return null;
            }
        }
        return baos.toByteArray();
    }

    /**
     * 从 Key 中提取文件名（最后一段）。
     *
     * @param key 对象 Key
     * @return 文件名；若 key 为 null/空 返回 null
     */
    private String extractFileName(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        int idx = key.lastIndexOf('/');
        return idx >= 0 ? key.substring(idx + 1) : key;
    }

    /**
     * 从 Key 中提取目录前缀（不含文件名）。
     *
     * @param key 对象 Key
     * @return 目录前缀；若不含 "/" 返回 null
     */
    private String extractFilePath(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        int idx = key.lastIndexOf('/');
        return idx >= 0 ? key.substring(0, idx) : null;
    }
}
