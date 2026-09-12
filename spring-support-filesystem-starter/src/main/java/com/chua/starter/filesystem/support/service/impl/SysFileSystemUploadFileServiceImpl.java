package com.chua.starter.filesystem.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;
import com.chua.starter.filesystem.support.entity.SysFileSystemUploadFile;
import com.chua.starter.filesystem.support.mapper.SysFileSystemUploadFileMapper;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadFileService;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
* 分片上传文件记录服务实现。
*
* <p>负责将分片上传结果持久化到 {@code filesystem_upload_file} 表。
*
* @author CH
* @since 4.0.0
 */
@Service
@RequiredArgsConstructor
public class SysFileSystemUploadFileServiceImpl
        extends ServiceImpl<SysFileSystemUploadFileMapper, SysFileSystemUploadFile>
        implements SysFileSystemUploadFileService {

    /** 上传任务服务 */
    private final SysFileSystemUploadService uploadService;

    /**
    * 从上传任务创建文件记录。
    *
    * @param uploadId 上传任务 标识
    * @param url      文件来源 URL
    * @param etag     文件 e标签
    * @return 是否保存成功
     */
    @Override
    @Transactional
    public boolean createFromTask(String uploadId, String url, String etag) {
        SysFileSystemUpload task = uploadService.getByUploadId(uploadId);
        if (task == null) {
            return false;
        }
        SysFileSystemUploadFile file = new SysFileSystemUploadFile();
        file.setFilesystemUploadFileUploadId(uploadId);
        file.setFilesystemUploadFileName(task.getFilesystemUploadTaskFileName());
        file.setFilesystemUploadFileOriginalName(task.getFilesystemUploadTaskOriginalName());
        file.setFilesystemUploadFileSize(task.getFilesystemUploadTaskFileSize());
        file.setFilesystemUploadFileStorageType(task.getFilesystemUploadTaskStorageType());
        file.setFilesystemUploadFileBucket(task.getFilesystemUploadTaskBucket());
        file.setFilesystemUploadFileKey(task.getFilesystemUploadTaskKey());
        file.setFilesystemUploadFileUrl(url);
        file.setFilesystemUploadFileContentType(task.getFilesystemUploadTaskContentType());
        file.setFilesystemUploadFileEtag(etag);
        file.setFilesystemUploadFileCompleteTime(LocalDateTime.now());
        file.setCreateTime(LocalDateTime.now());
        file.setUpdateTime(LocalDateTime.now());
        file.setCreateBy(0);
        file.setCreateName("system");
        file.setUpdateBy(0);
        file.setUpdateName("system");
        return save(file);
    }

    /**
    * 按上传任务 标识 查询文件记录。
    *
    * @param uploadId 上传任务 标识
    * @return 文件记录，不存在返回 空
     */
    @Override
    public SysFileSystemUploadFile getByUploadId(String uploadId) {
        return getOne(new LambdaQueryWrapper<SysFileSystemUploadFile>()
                .eq(SysFileSystemUploadFile::getFilesystemUploadFileUploadId, uploadId));
    }

    /**
    * 删除指定上传任务的所有文件记录。
    *
    * @param uploadId 上传任务 标识
    * @return 是否删除成功
     */
    @Override
    @Transactional
    public boolean deleteByUploadId(String uploadId) {
        return remove(new LambdaQueryWrapper<SysFileSystemUploadFile>()
                .eq(SysFileSystemUploadFile::getFilesystemUploadFileUploadId, uploadId));
    }
}
