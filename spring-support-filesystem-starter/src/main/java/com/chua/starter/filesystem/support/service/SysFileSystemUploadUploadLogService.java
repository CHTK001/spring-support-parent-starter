package com.chua.starter.filesystem.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.filesystem.support.entity.SysFileSystemUploadUploadLog;

import java.util.List;

/**
* 文件系统上传 对象存储 记录服务接口。
*
* @since 4.0.0.42
* @author CH
 */
public interface SysFileSystemUploadUploadLogService extends IService<SysFileSystemUploadUploadLog> {

    /**
    * 记录一次上传结果（成功/失败均写入）。
    *
    * @param uploadId   上传任务 标识
    * @param success    是否成功
    * @param message    记录信息
    * @param retryCount 第几次尝试
    * @return 是否写入成功
    */
    boolean record(String uploadId, boolean success, String message, int retryCount);

    /**
    * 查询某任务的全部上传记录。
    *
    * @param uploadId 上传任务 标识
    * @return 上传记录列表（按时间升序）
    */
    List<SysFileSystemUploadUploadLog> listByUploadId(String uploadId);

    /**
    * 删除某任务的全部上传记录。
    *
    * @param uploadId 上传任务 标识
    * @return 是否删除成功
    */
    boolean deleteByUploadId(String uploadId);
}
