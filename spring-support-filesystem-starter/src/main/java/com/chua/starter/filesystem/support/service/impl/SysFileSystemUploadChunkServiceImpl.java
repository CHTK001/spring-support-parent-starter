package com.chua.starter.filesystem.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.chua.starter.filesystem.support.entity.SysFileSystemUploadChunk;
import com.chua.starter.filesystem.support.mapper.SysFileSystemUploadChunkMapper;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadChunkService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分片上传块记录服务实现。
 *
 * <p>负责分片上传过程中的块数据持久化与状态管理。
 *
 * @author CH
 * @since 4.0.0
 */
@Service
public class SysFileSystemUploadChunkServiceImpl
        extends ServiceImpl<SysFileSystemUploadChunkMapper, SysFileSystemUploadChunk>
        implements SysFileSystemUploadChunkService {

    /**
     * 保存单个分片记录。
     *
     * @param uploadId     上传任务 标识
     * @param partNumber   分片序号
     * @param chunkSize    分片大小（字节）
     * @param etag         分片 e标签
     * @param storagePath  存储路径
     * @return 是否保存成功
     */
    @Override
    @Transactional
    public boolean saveChunk(String uploadId, Integer partNumber, Long chunkSize, String etag, String storagePath) {
        SysFileSystemUploadChunk chunk = new SysFileSystemUploadChunk();
        chunk.setFilesystemUploadChunkUploadId(uploadId);
        chunk.setFilesystemUploadChunkPartNumber(partNumber);
        chunk.setFilesystemUploadChunkSize(chunkSize);
        chunk.setFilesystemUploadChunkEtag(etag);
        chunk.setFilesystemUploadChunkStoragePath(storagePath);
        chunk.setFilesystemUploadChunkStatus(SysFileSystemUploadChunk.Status.UPLOADED);
        chunk.setFilesystemUploadChunkUploadTime(LocalDateTime.now());
        chunk.setCreateTime(LocalDateTime.now());
        chunk.setUpdateTime(LocalDateTime.now());
        chunk.setCreateBy(0);
        chunk.setCreateName("system");
        chunk.setUpdateBy(0);
        chunk.setUpdateName("system");
        return save(chunk);
    }

    /**
      * 按上传任务 标识 查询所有分片，按序号升序排列。
     *
     * @param uploadId 上传任务 标识
     * @return 分片列表
     */
    @Override
    public List<SysFileSystemUploadChunk> listByUploadId(String uploadId) {
        return list(new LambdaQueryWrapper<SysFileSystemUploadChunk>()
                .eq(SysFileSystemUploadChunk::getFilesystemUploadChunkUploadId, uploadId)
                .orderByAsc(SysFileSystemUploadChunk::getFilesystemUploadChunkPartNumber));
    }

    /**
     * 统计已上传的分片数量。
     *
     * @param uploadId 上传任务 标识
     * @return 已上传分片数
     */
    @Override
    public int countUploaded(String uploadId) {
        Long count = count(new LambdaQueryWrapper<SysFileSystemUploadChunk>()
                .eq(SysFileSystemUploadChunk::getFilesystemUploadChunkUploadId, uploadId)
                .eq(SysFileSystemUploadChunk::getFilesystemUploadChunkStatus, SysFileSystemUploadChunk.Status.UPLOADED));
        return count.intValue();
    }

    /**
     * 批量标记指定上传任务的所有分片为已合并状态。
     *
     * @param uploadId 上传任务 标识
     * @return 是否更新成功
     */
    @Override
    @Transactional
    public boolean markMerged(String uploadId) {
        SysFileSystemUploadChunk update = new SysFileSystemUploadChunk();
        update.setFilesystemUploadChunkStatus(SysFileSystemUploadChunk.Status.MERGED);
        return update(update, new LambdaQueryWrapper<SysFileSystemUploadChunk>()
                .eq(SysFileSystemUploadChunk::getFilesystemUploadChunkUploadId, uploadId)
                .eq(SysFileSystemUploadChunk::getFilesystemUploadChunkStatus, SysFileSystemUploadChunk.Status.UPLOADED));
    }

    /**
     * 删除指定上传任务的所有分片记录。
     *
     * @param uploadId 上传任务 标识
     * @return 是否删除成功
     */
    @Override
    @Transactional
    public boolean deleteByUploadId(String uploadId) {
        return remove(new LambdaQueryWrapper<SysFileSystemUploadChunk>()
                .eq(SysFileSystemUploadChunk::getFilesystemUploadChunkUploadId, uploadId));
    }
}
