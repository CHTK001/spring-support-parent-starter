package com.chua.starter.filesystem.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;
import com.chua.starter.filesystem.support.mapper.SysFileSystemUploadMapper;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SysFileSystemUploadServiceImpl extends ServiceImpl<SysFileSystemUploadMapper, SysFileSystemUpload> implements SysFileSystemUploadService {

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
        task.setFilesystemUploadTaskUploadedChunks(0);
        task.setFilesystemUploadTaskStorageType(storageType);
        task.setFilesystemUploadTaskBucket(bucket);
        task.setFilesystemUploadTaskKey(key);
        task.setFilesystemUploadTaskContentType(contentType);
        task.setFilesystemUploadTaskStatus(SysFileSystemUpload.Status.PENDING);
        task.setFilesystemUploadTaskStartTime(now);
        task.setFilesystemUploadTaskRetryCount(0);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setCreateBy(0);
        task.setCreateName("system");
        task.setUpdateBy(0);
        task.setUpdateName("system");
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
                .lt(SysFileSystemUpload::getFilesystemUploadTaskRetryCount, 3));
    }
}
