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

@Service
public class SysFileSystemUploadChunkServiceImpl
        extends ServiceImpl<SysFileSystemUploadChunkMapper, SysFileSystemUploadChunk>
        implements SysFileSystemUploadChunkService {

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

    @Override
    public List<SysFileSystemUploadChunk> listByUploadId(String uploadId) {
        return list(new LambdaQueryWrapper<SysFileSystemUploadChunk>()
                .eq(SysFileSystemUploadChunk::getFilesystemUploadChunkUploadId, uploadId)
                .orderByAsc(SysFileSystemUploadChunk::getFilesystemUploadChunkPartNumber));
    }

    @Override
    public int countUploaded(String uploadId) {
        Long count = count(new LambdaQueryWrapper<SysFileSystemUploadChunk>()
                .eq(SysFileSystemUploadChunk::getFilesystemUploadChunkUploadId, uploadId)
                .eq(SysFileSystemUploadChunk::getFilesystemUploadChunkStatus, SysFileSystemUploadChunk.Status.UPLOADED));
        return count.intValue();
    }

    @Override
    @Transactional
    public boolean markMerged(String uploadId) {
        SysFileSystemUploadChunk update = new SysFileSystemUploadChunk();
        update.setFilesystemUploadChunkStatus(SysFileSystemUploadChunk.Status.MERGED);
        return update(null, new LambdaQueryWrapper<SysFileSystemUploadChunk>()
                .eq(SysFileSystemUploadChunk::getFilesystemUploadChunkUploadId, uploadId)
                .eq(SysFileSystemUploadChunk::getFilesystemUploadChunkStatus, SysFileSystemUploadChunk.Status.UPLOADED));
    }

    @Override
    @Transactional
    public boolean deleteByUploadId(String uploadId) {
        return remove(new LambdaQueryWrapper<SysFileSystemUploadChunk>()
                .eq(SysFileSystemUploadChunk::getFilesystemUploadChunkUploadId, uploadId));
    }
}
