package com.chua.starter.filesystem.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件系统分片上传详情表。
 *
 * @since 4.0.0.42
 * @author CH
 */
@TableName(value = "filesystem_upload_chunk")
public class SysFileSystemUploadChunk extends SysBase implements Serializable {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    @TableId(value = "filesystem_upload_chunk_id", type = IdType.AUTO)
    /** Filesystem上传chunkID */
    private Long filesystemUploadChunkId;

    /** 上传任务ID，关联 filesystem_upload_task.filesystem_upload_task_upload_id */
    @TableField("filesystem_upload_chunk_upload_id")
    /** Filesystem上传chunk上传ID */
    private String filesystemUploadChunkUploadId;

    /** 分片序号(1-based) */
    @TableField("filesystem_upload_chunk_part_number")
    /** Filesystem上传chunkpart数字 */
    private Integer filesystemUploadChunkPartNumber;

    /** 分片大小(字节) */
    @TableField("filesystem_upload_chunk_size")
    /** Filesystem上传chunk尺寸 */
    private Long filesystemUploadChunkSize;

    /** 分片ETag */
    @TableField("filesystem_upload_chunk_etag")
    /** Filesystem上传chunketag */
    private String filesystemUploadChunkEtag;

    /** 分片存储路径（本地文件路径或对象存储key） */
    @TableField("filesystem_upload_chunk_storage_path")
    /** Filesystem上传chunk存储路径 */
    private String filesystemUploadChunkStoragePath;

    /** 分片状态: 0-已上传, 1-已合并, -1-失败 */
    @TableField("filesystem_upload_chunk_status")
    /** Filesystem上传chunk状态 */
    private Integer filesystemUploadChunkStatus;

    /** 上传时间 */
    @TableField("filesystem_upload_chunk_upload_time")
    /** Filesystem上传chunk上传时间 */
    private LocalDateTime filesystemUploadChunkUploadTime;

    /** 创建 SysFileSystemUploadChunk 实例 */
    public SysFileSystemUploadChunk() {}

    /** 获取FilesystemUploadChunkId */
    public Long getFilesystemUploadChunkId() { return filesystemUploadChunkId; }
    /** 设置FilesystemUploadChunkId */
    public void setFilesystemUploadChunkId(Long v) { this.filesystemUploadChunkId = v; }

    /** 获取FilesystemUploadChunkUploadId */
    public String getFilesystemUploadChunkUploadId() { return filesystemUploadChunkUploadId; }
    /** 设置FilesystemUploadChunkUploadId */
    public void setFilesystemUploadChunkUploadId(String v) { this.filesystemUploadChunkUploadId = v; }

    /** 获取FilesystemUploadChunkPartNumber */
    public Integer getFilesystemUploadChunkPartNumber() { return filesystemUploadChunkPartNumber; }
    /** 设置FilesystemUploadChunkPartNumber */
    public void setFilesystemUploadChunkPartNumber(Integer v) { this.filesystemUploadChunkPartNumber = v; }

    /** 获取FilesystemUploadChunk获取大小 */
    public Long getFilesystemUploadChunkSize() { return filesystemUploadChunkSize; }
    /** 设置FilesystemUploadChunk获取大小 */
    public void setFilesystemUploadChunkSize(Long v) { this.filesystemUploadChunkSize = v; }

    /** 获取FilesystemUploadChunkEtag */
    public String getFilesystemUploadChunkEtag() { return filesystemUploadChunkEtag; }
    /** 设置FilesystemUploadChunkEtag */
    public void setFilesystemUploadChunkEtag(String v) { this.filesystemUploadChunkEtag = v; }

    /** 获取FilesystemUploadChunkStoragePath */
    public String getFilesystemUploadChunkStoragePath() { return filesystemUploadChunkStoragePath; }
    /** 设置FilesystemUploadChunkStoragePath */
    public void setFilesystemUploadChunkStoragePath(String v) { this.filesystemUploadChunkStoragePath = v; }

    /** 获取FilesystemUploadChunkStatus */
    public Integer getFilesystemUploadChunkStatus() { return filesystemUploadChunkStatus; }
    /** 设置FilesystemUploadChunkStatus */
    public void setFilesystemUploadChunkStatus(Integer v) { this.filesystemUploadChunkStatus = v; }

    /** 获取FilesystemUploadChunkUploadTime */
    public LocalDateTime getFilesystemUploadChunkUploadTime() { return filesystemUploadChunkUploadTime; }
    /** 设置FilesystemUploadChunkUploadTime */
    public void setFilesystemUploadChunkUploadTime(LocalDateTime v) { this.filesystemUploadChunkUploadTime = v; }

    public static class Status {
        /** Uploaded */
        public static final int UPLOADED = 0;
        /** Merged */
        public static final int MERGED = 1;
        /** Failed */
        public static final int FAILED = -1;
    }
}
