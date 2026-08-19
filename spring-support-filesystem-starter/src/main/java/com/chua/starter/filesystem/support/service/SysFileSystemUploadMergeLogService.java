package com.chua.starter.filesystem.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.filesystem.support.entity.SysFileSystemUploadMergeLog;

import java.util.List;

/**
 * 文件系统分片合并记录服务接口。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SysFileSystemUploadMergeLogService extends IService<SysFileSystemUploadMergeLog> {

    /**
     * 记录一次合并结果（成功/失败均写入）。
     *
     * @param uploadId   上传任务 ID
     * @param success    是否成功
     * @param message    记录信息
     * @param retryCount 第几次尝试
     * @return 是否写入成功
     */
    boolean record(String uploadId, boolean success, String message, int retryCount);

    /**
     * 查询某任务的全部合并记录。
     *
     * @param uploadId 上传任务 ID
     * @return 合并记录列表（按时间升序）
     */
    List<SysFileSystemUploadMergeLog> listByUploadId(String uploadId);

    /**
     * 删除某任务的全部合并记录。
     *
     * @param uploadId 上传任务 ID
     * @return 是否删除成功
     */
    boolean deleteByUploadId(String uploadId);
}
