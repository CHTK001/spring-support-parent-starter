package com.chua.starter.filesystem.support.service.impl;

import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;
import com.chua.starter.filesystem.support.entity.SysFileSystemUploadChunk;
import com.chua.starter.filesystem.support.service.AsyncMergeService;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadChunkService;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadFileService;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadMergeLogService;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadService;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadUploadLogService;
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
 * 文件系统异步调度服务实现。
 *
 * <p>两阶段异步：</p>
 * <ol>
 *   <li>合并阶段：扫描待合并任务，合并分片字节，无论成败写入合并记录表；成功后任务切到待上传 OSS。</li>
 *   <li>上传阶段：扫描待上传任务，写入对象存储，无论成败写入上传记录表；成功后写入文件主体表并标记完成。</li>
 * </ol>
 *
 * <p>失败任务由重试调度（最多 {@value #MAX_RETRY} 次）继续执行。</p>
 *
 * @since 4.0.0.42
 * @author CH
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
     * 初始重试计数
     */
    private static final int INITIAL_RETRY_COUNT = 1;

    /**
     * 合并阶段记录文案
     */
    private static final String MERGE_STAGE_NAME = "合并";

    /**
     * 上传阶段记录文案
     */
    private static final String UPLOAD_STAGE_NAME = "上传OSS";

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
     * 合并记录服务
     */
    private final SysFileSystemUploadMergeLogService mergeLogService;

    /**
     * 上传记录服务
     */
    private final SysFileSystemUploadUploadLogService uploadLogService;

    /**
     * 文件存储模板
     */
    private final FileStorageTemplate storageTemplate;

    @Override
    @Scheduled(fixedDelayString = "${plugin.filesystem.upload.merge-interval-ms:10000}")
    /**
     * 合并pending任务
    */
    public void mergePendingTasks() {
        List<SysFileSystemUpload> tasks = uploadService.listPendingMerge();
        for (SysFileSystemUpload task : tasks) {
            try {
                /**
                 * 合并任务。
                 * @param task 任务
                 */
                mergeTask(task);
            } catch (Exception e) {
                log.error("异步合并失败: {}", task.getFilesystemUploadTaskUploadId(), e);
                /**
                 * mark合并失败。
                 * @param task 任务
                 * @param e.getMessage( e.获取消息(
                 */
                markMergeFailed(task, "合并异常: " + e.getMessage());
            }
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${plugin.filesystem.upload.upload-interval-ms:10000}")
    /**
     * uploadpending任务
    */
    public void uploadPendingTasks() {
        List<SysFileSystemUpload> tasks = uploadService.listPendingUpload();
        for (SysFileSystemUpload task : tasks) {
            try {
                /**
                 * upload任务。
                 * @param task 任务
                 */
                uploadTask(task);
            } catch (Exception e) {
                log.error("异步上传OSS失败: {}", task.getFilesystemUploadTaskUploadId(), e);
                /**
                 * markupload失败。
                 * @param task 任务
                 * @param e.getMessage( e.获取消息(
                 */
                markUploadFailed(task, "上传OSS异常: " + e.getMessage());
            }
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${plugin.filesystem.upload.retry-interval-ms:60000}")
    /**
     * 重试失败任务
    */
    public void retryFailedTasks() {
        List<SysFileSystemUpload> tasks = uploadService.listFailedRetryable();
        for (SysFileSystemUpload task : tasks) {
            try {
                Integer stage = task.getFilesystemUploadTaskStage();
                if (stage != null && stage == SysFileSystemUpload.Stage.MERGE) {
                    /**
                     * 合并任务。
                     * @param task 任务
                     */
                    mergeTask(task);
                } else if (stage != null && stage == SysFileSystemUpload.Stage.UPLOAD_TO_OSS) {
                    /**
                     * upload任务。
                     * @param task 任务
                     */
                    uploadTask(task);
                } else {
                    log.warn("未知阶段的任务不可重试: {} stage={}",
                            task.getFilesystemUploadTaskUploadId(), task.getFilesystemUploadTaskStage());
                }
            } catch (Exception e) {
                log.error("重试失败: {} stage={}", task.getFilesystemUploadTaskUploadId(),
                        task.getFilesystemUploadTaskStage(), e);
            }
        }
    }

    /**
     * 合并阶段：合并单个任务的分片。
     *
     * <p>流程：切合并中 → 校验分片 → 字节合并 → 写合并记录表 → 成功标记分片已合并并切到待上传 OSS。</p>
     *
     * @param task 待合并的上传任务
     */
    @Transactional
    protected void mergeTask(SysFileSystemUpload task) {
        String uploadId = task.getFilesystemUploadTaskUploadId();
        int retry = task.getFilesystemUploadTaskRetryCount() != null
                ? task.getFilesystemUploadTaskRetryCount() + INITIAL_RETRY_COUNT
                : INITIAL_RETRY_COUNT;

        // 切到合并中状态，防止重复处理
        uploadService.updateStatus(uploadId, SysFileSystemUpload.Status.MERGING);
        uploadService.updateStage(uploadId, SysFileSystemUpload.Stage.MERGE);

        // 校验分片数量
        List<SysFileSystemUploadChunk> chunks = chunkService.listByUploadId(uploadId);
        if (chunks.size() < task.getFilesystemUploadTaskTotalChunks()) {
            String msg = "分片不完整: " + chunks.size() + "/" + task.getFilesystemUploadTaskTotalChunks();
            mergeLogService.record(uploadId, false, msg, retry);
            /**
             * mark合并失败。
             * @param task 任务
             * @param msg msg
             */
            markMergeFailed(task, msg);
            return;
        }

        // 合并字节
        byte[] merged = mergeChunks(chunks);
        if (merged == null) {
            String msg = "合并分片数据失败";
            mergeLogService.record(uploadId, false, msg, retry);
            /**
             * mark合并失败。
             * @param task 任务
             * @param msg msg
             */
            markMergeFailed(task, msg);
            return;
        }

        // 持久化合并文件到临时目录，供上传阶段读取
        if (!writeMergedFile(uploadId, merged)) {
            String msg = "写入合并文件失败";
            mergeLogService.record(uploadId, false, msg, retry);
            /**
             * mark合并失败。
             * @param task 任务
             * @param msg msg
             */
            markMergeFailed(task, msg);
            return;
        }

 // 合并成功：标记分片已合并、任务进入待上传 对象存储 阶段
        chunkService.markMerged(uploadId);
        mergeLogService.record(uploadId, true, "合并成功", retry);
        uploadService.updateStage(uploadId, SysFileSystemUpload.Stage.UPLOAD_TO_OSS);
        uploadService.updateStatus(uploadId, SysFileSystemUpload.Status.PENDING_UPLOAD);
 // 重置失败次数，保证上传阶段独立重试（用独立字段更新，避免覆盖 状态/Stage）
        uploadService.resetRetryCount(uploadId);
        log.info("异步合并成功: {} (第{}次)", uploadId, retry);
    }

    /**
     * 上传阶段：将合并后的文件上传到对象存储。
     *
     * <p>流程：切上传中 → 选择存储 → putObject → 写上传记录表 → 成功写入文件主体表并标记完成。</p>
     *
     * @param task 待上传的上传任务
     */
    @Transactional
    protected void uploadTask(SysFileSystemUpload task) {
        String uploadId = task.getFilesystemUploadTaskUploadId();
        int retry = task.getFilesystemUploadTaskRetryCount() != null
                ? task.getFilesystemUploadTaskRetryCount() + INITIAL_RETRY_COUNT
                : INITIAL_RETRY_COUNT;

        uploadService.updateStatus(uploadId, SysFileSystemUpload.Status.UPLOADING_TO_OSS);
        uploadService.updateStage(uploadId, SysFileSystemUpload.Stage.UPLOAD_TO_OSS);

        // 选择存储后端
        String storageType = task.getFilesystemUploadTaskStorageType();
        var storage = storageTemplate.getStorage(storageType);
        if (storage == null) {
            storage = storageTemplate.getDefaultStorage();
        }
        if (storage == null) {
            String msg = "存储不存在: " + storageType;
            uploadLogService.record(uploadId, false, msg, retry);
            /**
             * markupload失败。
             * @param task 任务
             * @param msg msg
             */
            markUploadFailed(task, msg);
            return;
        }

        // 构造上传请求
        String key = task.getFilesystemUploadTaskKey();
        String fileName = extractFileName(key);
        String filePath = extractFilePath(key);
        byte[] content = readMergedFile(uploadId);
        if (content == null) {
            String msg = "读取合并文件失败";
            uploadLogService.record(uploadId, false, msg, retry);
            /**
             * markupload失败。
             * @param task 任务
             * @param msg msg
             */
            markUploadFailed(task, msg);
            return;
        }
        var putRequest = com.chua.common.support.storage.request.PutObjectRequest.builder()
                .content(content)
                .fileName(fileName)
                .filePath(filePath)
                .build();

        // 上传到对象存储
        var result = storage.putObject(putRequest);
        if (result.getResultCode() != com.chua.common.support.storage.result.ObjectResult.ResultCode.SUCCESS) {
            String msg = "上传到bucket失败: " + result.getMessage();
            uploadLogService.record(uploadId, false, msg, retry);
            /**
             * markupload失败。
             * @param task 任务
             * @param msg msg
             */
            markUploadFailed(task, msg);
            return;
        }

        // 上传成功：删除合并临时文件、写入文件主体表并标记完成
        /**
         * 删除合并文件。
         * @param uploadId uploadid
         */
        deleteMergedFile(uploadId);
        fileService.createFromTask(uploadId, result.getUrl(), null);
        uploadLogService.record(uploadId, true, "上传OSS成功", retry);
        uploadService.markCompleted(uploadId, result.getUrl());
        log.info("异步上传OSS完成: {} -> {} (第{}次)", uploadId, result.getUrl(), retry);
    }

    /**
     * 标记合并失败（未超重试则回退到待合并继续重试）。
     *
     * @param task        上传任务
     * @param errorMessage 错误信息
     */
    private void markMergeFailed(SysFileSystemUpload task, String errorMessage) {
        String uploadId = task.getFilesystemUploadTaskUploadId();
        int retry = task.getFilesystemUploadTaskRetryCount() != null
                ? task.getFilesystemUploadTaskRetryCount() + INITIAL_RETRY_COUNT
                : INITIAL_RETRY_COUNT;
        uploadService.updateRetryCount(uploadId, retry);
        if (retry >= MAX_RETRY) {
            uploadService.markFailed(uploadId, errorMessage);
            log.warn("合并失败且超过最大重试次数: {} -> {}", uploadId, errorMessage);
        } else {
            // 回退到待合并状态，等待下次调度重试
            uploadService.updateStatus(uploadId, SysFileSystemUpload.Status.PENDING_MERGE);
            log.warn("合并失败，等待重试: {} (第{}/{}次) -> {}", uploadId, retry, MAX_RETRY, errorMessage);
        }
    }

    /**
     * 标记上传失败（未超重试则回退到待上传 对象存储 继续重试）。
     *
     * @param task        上传任务
     * @param errorMessage 错误信息
     */
    private void markUploadFailed(SysFileSystemUpload task, String errorMessage) {
        String uploadId = task.getFilesystemUploadTaskUploadId();
        int retry = task.getFilesystemUploadTaskRetryCount() != null
                ? task.getFilesystemUploadTaskRetryCount() + INITIAL_RETRY_COUNT
                : INITIAL_RETRY_COUNT;
        uploadService.updateRetryCount(uploadId, retry);
        if (retry >= MAX_RETRY) {
            uploadService.markFailed(uploadId, errorMessage);
            log.warn("上传OSS失败且超过最大重试次数: {} -> {}", uploadId, errorMessage);
        } else {
            // 回退到待上传状态，等待下次调度重试
            uploadService.updateStatus(uploadId, SysFileSystemUpload.Status.PENDING_UPLOAD);
            log.warn("上传OSS失败，等待重试: {} (第{}/{}次) -> {}", uploadId, retry, MAX_RETRY, errorMessage);
        }
    }

    /**
     * 将分片按序合并为完整字节数组。
     *
     * @param chunks 已上传的分片列表（已按 part数字 升序）
     * @return 合并后的字节数组；任一分片缺失或读取失败返回 空
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
     * 从临时目录读取合并文件字节。
     *
     * @param uploadId 上传任务 标识
     * @return 合并文件字节；不存在返回 空
     */
    private byte[] readMergedFile(String uploadId) {
        try {
            java.nio.file.Path path = mergedFilePath(uploadId);
            if (!java.nio.file.Files.exists(path)) {
                return null;
            }
            return java.nio.file.Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("读取合并文件失败: {}", uploadId, e);
            return null;
        }
    }

    /**
     * 写入合并文件到临时目录。
     *
     * @param uploadId 上传任务 标识
     * @param content  合并字节
     * @return 是否写入成功
     */
    private boolean writeMergedFile(String uploadId, byte[] content) {
        try {
            java.nio.file.Path path = mergedFilePath(uploadId);
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path, content);
            return true;
        } catch (IOException e) {
            log.error("写入合并文件失败: {}", uploadId, e);
            return false;
        }
    }

    /**
     * 删除合并临时文件。
     *
     * @param uploadId 上传任务 标识
     */
    private void deleteMergedFile(String uploadId) {
        try {
            java.nio.file.Files.deleteIfExists(mergedFilePath(uploadId));
        } catch (IOException e) {
            log.warn("删除合并临时文件失败: {}", uploadId, e);
        }
    }

    /**
     * 合并临时文件路径。
     *
     * @param uploadId 上传任务 标识
     * @return 合并临时文件 路径
     */
    private java.nio.file.Path mergedFilePath(String uploadId) {
        String baseDir = System.getProperty("filesystem.temp.path");
        if (baseDir == null || baseDir.isEmpty()) {
            baseDir = System.getProperty("java.io.tmpdir");
        }
        return java.nio.file.Paths.get(baseDir).resolve(uploadId + "_merged");
    }

    /**
     * 从 Key 中提取文件名（最后一段）。
     *
     * @param key 对象 Key
     * @return 文件名；若 Key 为 空/空 返回 空
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
     * @return 目录前缀；若不含 "/" 返回 空
     */
    private String extractFilePath(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        int idx = key.lastIndexOf('/');
        return idx >= 0 ? key.substring(0, idx) : null;
    }
}
