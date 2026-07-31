package com.chua.starter.filesystem.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;
import com.chua.starter.filesystem.support.mapper.SysFileSystemUploadMapper;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件系统分片上传任务服务实现。
 *
 * <p>继承 MyBatis-Plus {@link ServiceImpl} 获取 Base CRUD，
 * 实现 {@link SysFileSystemUploadService} 中定义的分片上传特有业务逻辑。</p>
 *
 * @author CH
 * @since 1.0
 */
@Service
public class SysFileSystemUploadServiceImpl
        extends ServiceImpl<SysFileSystemUploadMapper, SysFileSystemUpload>
        implements SysFileSystemUploadService {

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY = 3;

    /**
     * 系统默认操作人 ID
     */
    private static final int SYSTEM_USER_ID = 0;

    /**
     * 系统默认操作人名称
     */
    private static final String SYSTEM_USER_NAME = "system";

    /**
     * 初始已上传分片数
     */
    private static final int INITIAL_UPLOADED_CHUNKS = 0;

    @Override
    public SysFileSystemUpload createTask(String uploadId, String fileName, String originalName,
                                          Long fileSize, Long chunkSize, Integer totalChunks,
                                          String storageType, String bucket, String key, String contentType) {
        SysFileSystemUpload task = new SysFileSystemUpload();
        LocalDateTime now = LocalDateTime.now();
        task.setFilesystemUploadTaskUploadId(uploadId);
        task.setFilesystemUploadTaskFileName(fileName);
        task.setFilesystemUploadTaskOriginalName(originalName);
        task.setFilesystemUploadTaskFileSize(fileSize);
        task.setFilesystemUploadTaskChunkSize(chunkSize);
        task.setFilesystemUploadTaskTotalChunks(totalChunks);
        task.setFilesystemUploadTaskUploadedChunks(INITIAL_UPLOADED_CHUNKS);
        task.setFilesystemUploadTaskStorageType(storageType);
        task.setFilesystemUploadTaskBucket(bucket);
        task.setFilesystemUploadTaskKey(key);
        task.setFilesystemUploadTaskContentType(contentType);
        task.setFilesystemUploadTaskStatus(SysFileSystemUpload.Status.PENDING);
        task.setFilesystemUploadTaskStartTime(now);
        task.setFilesystemUploadTaskRetryCount(INITIAL_UPLOADED_CHUNKS);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setCreateBy(SYSTEM_USER_ID);
        task.setCreateName(SYSTEM_USER_NAME);
        task.setUpdateBy(SYSTEM_USER_ID);
        task.setUpdateName(SYSTEM_USER_NAME);
        save(task);
        return task;
    }

    @Override
    public boolean updateUploadedChunks(String uploadId, Integer uploadedChunks) {
        SysFileSystemUpload task = getByUploadId(uploadId);
        if (task == null) {
            return false;
        }
        task.setFilesystemUploadTaskUploadedChunks(uploadedChunks);
        if (uploadedChunks >= task.getFilesystemUploadTaskTotalChunks()) {
            task.setFilesystemUploadTaskStatus(SysFileSystemUpload.Status.PENDING_MERGE);
        } else {
            task.setFilesystemUploadTaskStatus(SysFileSystemUpload.Status.UPLOADING);
        }
        return updateById(task);
    }

    @Override
    public boolean updateStatus(String uploadId, Integer status) {
        SysFileSystemUpload task = getByUploadId(uploadId);
        if (task == null) {
            return false;
        }
        task.setFilesystemUploadTaskStatus(status);
        return updateById(task);
    }

    @Override
    public boolean markCompleted(String uploadId, String fileUrl) {
        SysFileSystemUpload task = getByUploadId(uploadId);
        if (task == null) {
            return false;
        }
        task.setFilesystemUploadTaskStatus(SysFileSystemUpload.Status.COMPLETED);
        task.setFilesystemUploadTaskFileUrl(fileUrl);
        task.setFilesystemUploadTaskCompleteTime(LocalDateTime.now());
        return updateById(task);
    }

    @Override
    public boolean markFailed(String uploadId, String errorMessage) {
        SysFileSystemUpload task = getByUploadId(uploadId);
        if (task == null) {
            return false;
        }
        task.setFilesystemUploadTaskStatus(SysFileSystemUpload.Status.FAILED);
        task.setFilesystemUploadTaskErrorMessage(errorMessage);
        task.setFilesystemUploadTaskCompleteTime(LocalDateTime.now());
        return updateById(task);
    }

    @Override
    public SysFileSystemUpload getByUploadId(String uploadId) {
        return getOne(new LambdaQueryWrapper<SysFileSystemUpload>()
                .eq(SysFileSystemUpload::getFilesystemUploadTaskUploadId, uploadId));
    }

    @Override
    public int cleanTimeoutTasks(LocalDateTime beforeTime) {
        LambdaQueryWrapper<SysFileSystemUpload> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(SysFileSystemUpload::getCreateTime, beforeTime);
        wrapper.in(SysFileSystemUpload::getFilesystemUploadTaskStatus,
                SysFileSystemUpload.Status.PENDING,
                SysFileSystemUpload.Status.UPLOADING,
                SysFileSystemUpload.Status.PENDING_MERGE);
        return remove(wrapper) ? 1 : 0;
    }

    @Override
    public List<SysFileSystemUpload> listPendingMerge() {
        return list(new LambdaQueryWrapper<SysFileSystemUpload>()
                .eq(SysFileSystemUpload::getFilesystemUploadTaskStatus, SysFileSystemUpload.Status.PENDING_MERGE));
    }

    @Override
    public List<SysFileSystemUpload> listFailedRetryable() {
        return list(new LambdaQueryWrapper<SysFileSystemUpload>()
                .eq(SysFileSystemUpload::getFilesystemUploadTaskStatus, SysFileSystemUpload.Status.FAILED)
                .lt(SysFileSystemUpload::getFilesystemUploadTaskRetryCount, MAX_RETRY));
    }
}