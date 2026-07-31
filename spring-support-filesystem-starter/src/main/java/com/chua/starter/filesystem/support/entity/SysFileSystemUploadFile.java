package com.chua.starter.filesystem.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件系统上传完成主体表（分片合并后的最终文件记录）。
 *
 * @author CH
 * @since 4.0.0.42
 */
@TableName(value = "filesystem_upload_file")
public class SysFileSystemUploadFile extends SysBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "filesystem_upload_file_id", type = IdType.AUTO)
    private Long filesystemUploadFileId;

    /** 上传任务ID，关联 filesystem_upload_task.filesystem_upload_task_upload_id */
    @TableField("filesystem_upload_file_upload_id")
    private String filesystemUploadFileUploadId;

    /** 文件名称 */
    @TableField("filesystem_upload_file_name")
    private String filesystemUploadFileName;

    /** 文件原始名称 */
    @TableField("filesystem_upload_file_original_name")
    private String filesystemUploadFileOriginalName;

    /** 文件大小(字节) */
    @TableField("filesystem_upload_file_size")
    private Long filesystemUploadFileSize;

    /** 存储类型 (local/minio/oss/cos/...) */
    @TableField("filesystem_upload_file_storage_type")
    private String filesystemUploadFileStorageType;

    /** 存储桶 */
    @TableField("filesystem_upload_file_bucket")
    private String filesystemUploadFileBucket;

    /** 文件Key */
    @TableField("filesystem_upload_file_key")
    private String filesystemUploadFileKey;

    /** 文件访问URL */
    @TableField("filesystem_upload_file_url")
    private String filesystemUploadFileUrl;

    /** 内容类型(MIME) */
    @TableField("filesystem_upload_file_content_type")
    private String filesystemUploadFileContentType;

    /** 文件ETag */
    @TableField("filesystem_upload_file_etag")
    private String filesystemUploadFileEtag;

    /** 合并完成时间 */
    @TableField("filesystem_upload_file_complete_time")
    private LocalDateTime filesystemUploadFileCompleteTime;

    public SysFileSystemUploadFile() {}

    public Long getFilesystemUploadFileId() { return filesystemUploadFileId; }
    public void setFilesystemUploadFileId(Long v) { this.filesystemUploadFileId = v; }

    public String getFilesystemUploadFileUploadId() { return filesystemUploadFileUploadId; }
    public void setFilesystemUploadFileUploadId(String v) { this.filesystemUploadFileUploadId = v; }

    public String getFilesystemUploadFileName() { return filesystemUploadFileName; }
    public void setFilesystemUploadFileName(String v) { this.filesystemUploadFileName = v; }

    public String getFilesystemUploadFileOriginalName() { return filesystemUploadFileOriginalName; }
    public void setFilesystemUploadFileOriginalName(String v) { this.filesystemUploadFileOriginalName = v; }

    public Long getFilesystemUploadFileSize() { return filesystemUploadFileSize; }
    public void setFilesystemUploadFileSize(Long v) { this.filesystemUploadFileSize = v; }

    public String getFilesystemUploadFileStorageType() { return filesystemUploadFileStorageType; }
    public void setFilesystemUploadFileStorageType(String v) { this.filesystemUploadFileStorageType = v; }

    public String getFilesystemUploadFileBucket() { return filesystemUploadFileBucket; }
    public void setFilesystemUploadFileBucket(String v) { this.filesystemUploadFileBucket = v; }

    public String getFilesystemUploadFileKey() { return filesystemUploadFileKey; }
    public void setFilesystemUploadFileKey(String v) { this.filesystemUploadFileKey = v; }

    public String getFilesystemUploadFileUrl() { return filesystemUploadFileUrl; }
    public void setFilesystemUploadFileUrl(String v) { this.filesystemUploadFileUrl = v; }

    public String getFilesystemUploadFileContentType() { return filesystemUploadFileContentType; }
    public void setFilesystemUploadFileContentType(String v) { this.filesystemUploadFileContentType = v; }

    public String getFilesystemUploadFileEtag() { return filesystemUploadFileEtag; }
    public void setFilesystemUploadFileEtag(String v) { this.filesystemUploadFileEtag = v; }

    public LocalDateTime getFilesystemUploadFileCompleteTime() { return filesystemUploadFileCompleteTime; }
    public void setFilesystemUploadFileCompleteTime(LocalDateTime v) { this.filesystemUploadFileCompleteTime = v; }
}
