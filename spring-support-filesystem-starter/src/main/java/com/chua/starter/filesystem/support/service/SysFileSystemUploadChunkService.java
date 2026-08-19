package com.chua.starter.filesystem.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.filesystem.support.entity.SysFileSystemUploadChunk;

import java.util.List;

/**
 * 文件系统分片上传详情服务接口。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SysFileSystemUploadChunkService extends IService<SysFileSystemUploadChunk> {

    /**
     * 保存分片记录。
     */
    boolean saveChunk(String uploadId, Integer partNumber, Long chunkSize, String etag, String storagePath);

    /**
     * 查询任务的所有分片。
     */
    List<SysFileSystemUploadChunk> listByUploadId(String uploadId);

    /**
     * 查询任务的已上传分片数。
     */
    int countUploaded(String uploadId);

    /**
     * 更新分片状态为已合并。
     */
    boolean markMerged(String uploadId);

    /**
     * 删除任务的所有分片记录。
     */
    boolean deleteByUploadId(String uploadId);
}
