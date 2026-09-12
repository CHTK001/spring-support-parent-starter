package com.chua.starter.filesystem.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件系统分片上传任务服务接口。
 *
 * <p>继承 MyBatis-Plus {@link IService} 以复用 Base CRUD 能力，
 * 并扩展分片上传任务特有的业务方法。</p>
 *
 * @since 1.0
 * @author CH
 */
public interface SysFileSystemUploadService extends IService<SysFileSystemUpload> {

    /**
     * 创建上传任务。
     *
     * @param uploadId     上传任务 标识
     * @param fileName     文件名
     * @param originalName 原始文件名
     * @param fileSize     文件大小（字节）
     * @param chunkSize    分片大小（字节）
     * @param totalChunks  总分片数
     * @param storageType  存储类型
     * @param bucket       存储桶
     * @param key          对象 钥匙
     * @param contentType  内容类型
     * @return 创建后的任务实体
     */
    SysFileSystemUpload createTask(String uploadId, String fileName, String originalName,
                                   Long fileSize, Long chunkSize, Integer totalChunks,
                                   String storageType, String bucket, String key, String contentType);

    /**
     * 更新已上传分片数。
     *
     * @param uploadId       上传任务 标识
     * @param uploadedChunks 已上传分片数
     * @return 是否更新成功
     */
    boolean updateUploadedChunks(String uploadId, Integer uploadedChunks);

    /**
     * 更新任务状态。
     *
     * @param uploadId 上传任务 标识
     * @param status   目标状态
     * @return 是否更新成功
     */
    boolean updateStatus(String uploadId, Integer status);

    /**
     * 更新任务阶段。
     *
     * @param uploadId 上传任务 标识
     * @param stage    目标阶段
     * @return 是否更新成功
     */
    boolean updateStage(String uploadId, Integer stage);

    /**
     * 重置重试次数（阶段切换时调用，保证各阶段独立重试）。
     *
     * @param uploadId 上传任务 标识
     * @return 是否更新成功
     */
    boolean resetRetryCount(String uploadId);

    /**
      * 递增重试次数（独立字段更新，避免覆盖 状态/Stage）。
     *
     * @param uploadId 上传任务 标识
     * @param retry    新的重试次数
     * @return 是否更新成功
     */
    boolean updateRetryCount(String uploadId, int retry);

    /**
     * 标记任务完成。
     *
     * @param uploadId 上传任务 标识
     * @param fileUrl  最终文件 URL
     * @return 是否更新成功
     */
    boolean markCompleted(String uploadId, String fileUrl);

    /**
     * 标记任务失败。
     *
     * @param uploadId     上传任务 标识
     * @param errorMessage 错误信息
     * @return 是否更新成功
     */
    boolean markFailed(String uploadId, String errorMessage);

    /**
      * 根据 uploadid 查询任务。
     *
     * @param uploadId 上传任务 标识
     * @return 任务实体；不存在返回 空
     */
    SysFileSystemUpload getByUploadId(String uploadId);

    /**
     * 清理超时未完成任务。
     *
     * @param beforeTime 截止时间
     * @return 受影响行数（0 或 1）
     */
    int cleanTimeoutTasks(LocalDateTime beforeTime);

    /**
     * 查询待合并任务列表。
     *
     * @return 待合并任务列表
     */
    List<SysFileSystemUpload> listPendingMerge();

    /**
      * 查询待上传 对象存储 任务列表。
     *
     * @return 待上传 对象存储 任务列表
     */
    List<SysFileSystemUpload> listPendingUpload();

    /**
     * 查询可重试的失败任务列表。
     *
     * @return 可重试任务列表
     */
    List<SysFileSystemUpload> listFailedRetryable();
}
