package com.chua.starter.filesystem.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
 * @since 1.0
 * @author CH
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
    /**
     * 创建Task
     * @param uploadId uploadId
     * @param fileName fileName
     * @param originalName originalName
     * @param fileSize fileSize
     * @param chunkSize chunkSize
     * @param totalChunks totalChunks
     * @param storageType storageType
     * @param bucket bucket
     * @param key key
     * @param contentType contentType
     */
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
        task.setFilesystemUploadTaskStage(SysFileSystemUpload.Stage.CHUNK_UPLOAD);
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
    /** 更新UploadedChunks */
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
    /** 更新Status */
    public boolean updateStatus(String uploadId, Integer status) {
        SysFileSystemUpload task = getByUploadId(uploadId);
        if (task == null) {
            return false;
        }
        task.setFilesystemUploadTaskStatus(status);
        return updateById(task);
    }

    @Override
    /** 更新Stage */
    public boolean updateStage(String uploadId, Integer stage) {
        SysFileSystemUpload task = getByUploadId(uploadId);
        if (task == null) {
            return false;
        }
        task.setFilesystemUploadTaskStage(stage);
        return updateById(task);
    }

    @Override
    /** 重置Retry计算数量 */
    public boolean resetRetryCount(String uploadId) {
        return update(new LambdaUpdateWrapper<SysFileSystemUpload>()
                .eq(SysFileSystemUpload::getFilesystemUploadTaskUploadId, uploadId)
                .set(SysFileSystemUpload::getFilesystemUploadTaskRetryCount, 0));
    }

    @Override
    /** 更新Retry计算数量 */
    public boolean updateRetryCount(String uploadId, int retry) {
        return update(new LambdaUpdateWrapper<SysFileSystemUpload>()
                .eq(SysFileSystemUpload::getFilesystemUploadTaskUploadId, uploadId)
                .set(SysFileSystemUpload::getFilesystemUploadTaskRetryCount, retry));
    }

    @Override
    /** 标记Completed */
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
    /** 标记Failed */
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
    /** 获取ByUploadId */
    public SysFileSystemUpload getByUploadId(String uploadId) {
        return getOne(new LambdaQueryWrapper<SysFileSystemUpload>()
                .eq(SysFileSystemUpload::getFilesystemUploadTaskUploadId, uploadId));
    }

    @Override
    /** CleanTimeoutTasks */
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
    /** ListPending合并 */
    public List<SysFileSystemUpload> listPendingMerge() {
        return list(new LambdaQueryWrapper<SysFileSystemUpload>()
                .eq(SysFileSystemUpload::getFilesystemUploadTaskStatus, SysFileSystemUpload.Status.PENDING_MERGE));
    }

    @Override
    /** ListPendingUpload */
    public List<SysFileSystemUpload> listPendingUpload() {
        return list(new LambdaQueryWrapper<SysFileSystemUpload>()
                .eq(SysFileSystemUpload::getFilesystemUploadTaskStatus, SysFileSystemUpload.Status.PENDING_UPLOAD));
    }

    @Override
    /** ListFailedRetryable */
    public List<SysFileSystemUpload> listFailedRetryable() {
        return list(new LambdaQueryWrapper<SysFileSystemUpload>()
                .eq(SysFileSystemUpload::getFilesystemUploadTaskStatus, SysFileSystemUpload.Status.FAILED)
                .lt(SysFileSystemUpload::getFilesystemUploadTaskRetryCount, MAX_RETRY));
    }
}