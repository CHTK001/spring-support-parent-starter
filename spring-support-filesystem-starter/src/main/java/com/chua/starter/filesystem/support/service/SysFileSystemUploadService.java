package com.chua.starter.filesystem.support.service;

import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件系统分片上传任务服务接口。
 *
 * @author CH
 * @since 1.0
 */
public interface SysFileSystemUploadService {

    SysFileSystemUpload createTask(String uploadId, String fileName, String originalName,
                                   Long fileSize, Long chunkSize, Integer totalChunks,
                                   String storageType, String bucket, String key, String contentType);

    boolean updateUploadedChunks(String uploadId, Integer uploadedChunks);

    boolean updateStatus(String uploadId, Integer status);

    boolean markCompleted(String uploadId, String fileUrl);

    boolean markFailed(String uploadId, String errorMessage);

    SysFileSystemUpload getByUploadId(String uploadId);

    int cleanTimeoutTasks(LocalDateTime beforeTime);

    List<SysFileSystemUpload> listPendingMerge();

    List<SysFileSystemUpload> listFailedRetryable();
}
