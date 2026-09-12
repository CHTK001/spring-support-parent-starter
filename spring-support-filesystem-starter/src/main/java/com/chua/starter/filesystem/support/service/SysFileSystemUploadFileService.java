package com.chua.starter.filesystem.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.filesystem.support.entity.SysFileSystemUploadFile;

/**
* 文件系统上传完成主体服务接口。
*
* @since 4.0.0.42
* @author CH
 */
public interface SysFileSystemUploadFileService extends IService<SysFileSystemUploadFile> {

    /**
    * 创建上传完成记录。
    * @param uploadId uploadid
    * @param url url
    * @param etag etag
    * @return 创建从任务的结果
     */
    boolean createFromTask(String uploadId, String url, String etag);

    /**
    * 根据 uploadid 查询。
    * @param uploadId uploadid
    * @return 获取byuploadid的结果
     */
    SysFileSystemUploadFile getByUploadId(String uploadId);

    /**
    * 删除主体记录。
    * @param uploadId uploadid
    * @return 删除byuploadid的结果
     */
    boolean deleteByUploadId(String uploadId);
}
