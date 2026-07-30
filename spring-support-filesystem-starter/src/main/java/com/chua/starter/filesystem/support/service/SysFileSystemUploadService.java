package com.chua.starter.filesystem.support.service;

import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;

import java.time.LocalDateTime;

/**
 * 文件系统分片上传任务服务接口。
 *
 * @author CH
 * @since 1.0
 */
public interface SysFileSystemUploadService {

    /**
     * 创建上传任务。
     *
     * @param uploadId 上传任务ID
     * @param fileName 文件名称
     * @param originalName 原始文件名
     * @param fileSize 文件大小
     * @param chunkSize 分片大小
     * @param totalChunks 总分片数
     * @param storageType 存储类型
     * @param bucket 存储桶
     * @param key 文件Key
     * @param contentType 内容类型
     * @return 任务实体
     */
    SysFileSystemUpload createTask(String uploadId, String fileName, String originalName,
                                   Long fileSize, Long chunkSize, Integer totalChunks,
                                   String storageType, String bucket, String key, String contentType);

    /**
     * 更新已上传分片数。
     *
     * @param uploadId 上传任务ID
     * @param uploadedChunks 已上传分片数
     * @return 是否更新成功
     */
    boolean updateUploadedChunks(String uploadId, Integer uploadedChunks);

    /**
     * 更新任务状态。
     *
     * @param uploadId 上传任务ID
     * @param status 状态
     * @return 是否更新成功
     */
    boolean updateStatus(String uploadId, Integer status);

    /**
     * 标记任务完成。
     *
     * @param uploadId 上传任务ID
     * @param fileUrl 文件URL
     * @return 是否更新成功
     */
    boolean markCompleted(String uploadId, String fileUrl);

    /**
     * 标记任务失败。
     *
     * @param uploadId 上传任务ID
     * @param errorMessage 错误信息
     * @return 是否更新成功
     */
    boolean markFailed(String uploadId, String errorMessage);

    /**
     * 根据上传任务ID获取任务信息。
     *
     * @param uploadId 上传任务ID
     * @return 任务实体
     */
    SysFileSystemUpload getByUploadId(String uploadId);

    /**
     * 清理超时任务。
     *
     * @param beforeTime 早于此时间的任务将被清理
     * @return 清理数量
     */
    int cleanTimeoutTasks(LocalDateTime beforeTime);
}