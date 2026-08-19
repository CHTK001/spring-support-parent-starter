package com.chua.starter.filesystem.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件系统上传完成主体表（分片合并后的最终文件记录）。
 *
 * @since 4.0.0.42
 * @author CH
 */
@TableName(value = "filesystem_upload_file")
public class SysFileSystemUploadFile extends SysBase implements Serializable {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    @TableId(value = "filesystem_upload_file_id", type = IdType.AUTO)
    /** Filesystem上传文件ID */
    private Long filesystemUploadFileId;

    /** 上传任务ID，关联 filesystem_upload_task.filesystem_upload_task_upload_id */
    @TableField("filesystem_upload_file_upload_id")
    /** Filesystem上传文件上传ID */
    private String filesystemUploadFileUploadId;

    /** 文件名称 */
    @TableField("filesystem_upload_file_name")
    /** Filesystem上传文件名称 */
    private String filesystemUploadFileName;

    /** 文件原始名称 */
    @TableField("filesystem_upload_file_original_name")
    /** Filesystem上传文件original名称 */
    private String filesystemUploadFileOriginalName;

    /** 文件大小(字节) */
    @TableField("filesystem_upload_file_size")
    /** Filesystem上传文件尺寸 */
    private Long filesystemUploadFileSize;

    /** 存储类型 (local/minio/oss/cos/...) */
    @TableField("filesystem_upload_file_storage_type")
    /** Filesystem上传文件存储类型 */
    private String filesystemUploadFileStorageType;

    /** 存储桶 */
    @TableField("filesystem_upload_file_bucket")
    /** Filesystem上传文件存储桶 */
    private String filesystemUploadFileBucket;

    /** 文件Key */
    @TableField("filesystem_upload_file_key")
    /** Filesystem上传文件密钥 */
    private String filesystemUploadFileKey;

    /** 文件访问URL */
    @TableField("filesystem_upload_file_url")
    /** Filesystem上传文件URL */
    private String filesystemUploadFileUrl;

    /** 内容类型(MIME) */
    @TableField("filesystem_upload_file_content_type")
    /** Filesystem上传文件内容类型 */
    private String filesystemUploadFileContentType;

    /** 文件ETag */
    @TableField("filesystem_upload_file_etag")
    /** Filesystem上传文件etag */
    private String filesystemUploadFileEtag;

    /** 合并完成时间 */
    @TableField("filesystem_upload_file_complete_time")
    /** Filesystem上传文件complete时间 */
    private LocalDateTime filesystemUploadFileCompleteTime;

    /** 创建 SysFileSystemUploadFile 实例 */
    public SysFileSystemUploadFile() {}

    /** 获取FilesystemUploadFileId */
    public Long getFilesystemUploadFileId() { return filesystemUploadFileId; }
    /** 设置FilesystemUploadFileId */
    public void setFilesystemUploadFileId(Long v) { this.filesystemUploadFileId = v; }

    /** 获取FilesystemUploadFileUploadId */
    public String getFilesystemUploadFileUploadId() { return filesystemUploadFileUploadId; }
    /** 设置FilesystemUploadFileUploadId */
    public void setFilesystemUploadFileUploadId(String v) { this.filesystemUploadFileUploadId = v; }

    /** 获取FilesystemUploadFileName */
    public String getFilesystemUploadFileName() { return filesystemUploadFileName; }
    /** 设置FilesystemUploadFileName */
    public void setFilesystemUploadFileName(String v) { this.filesystemUploadFileName = v; }

    /** 获取FilesystemUploadFileOriginalName */
    public String getFilesystemUploadFileOriginalName() { return filesystemUploadFileOriginalName; }
    /** 设置FilesystemUploadFileOriginalName */
    public void setFilesystemUploadFileOriginalName(String v) { this.filesystemUploadFileOriginalName = v; }

    /** 获取FilesystemUploadFile获取大小 */
    public Long getFilesystemUploadFileSize() { return filesystemUploadFileSize; }
    /** 设置FilesystemUploadFile获取大小 */
    public void setFilesystemUploadFileSize(Long v) { this.filesystemUploadFileSize = v; }

    /** 获取FilesystemUploadFileStorageType */
    public String getFilesystemUploadFileStorageType() { return filesystemUploadFileStorageType; }
    /** 设置FilesystemUploadFileStorageType */
    public void setFilesystemUploadFileStorageType(String v) { this.filesystemUploadFileStorageType = v; }

    /** 获取FilesystemUploadFileBucket */
    public String getFilesystemUploadFileBucket() { return filesystemUploadFileBucket; }
    /** 设置FilesystemUploadFileBucket */
    public void setFilesystemUploadFileBucket(String v) { this.filesystemUploadFileBucket = v; }

    /** 获取FilesystemUploadFileKey */
    public String getFilesystemUploadFileKey() { return filesystemUploadFileKey; }
    /** 设置FilesystemUploadFileKey */
    public void setFilesystemUploadFileKey(String v) { this.filesystemUploadFileKey = v; }

    /** 获取FilesystemUploadFileUrl */
    public String getFilesystemUploadFileUrl() { return filesystemUploadFileUrl; }
    /** 设置FilesystemUploadFileUrl */
    public void setFilesystemUploadFileUrl(String v) { this.filesystemUploadFileUrl = v; }

    /** 获取FilesystemUploadFileContentType */
    public String getFilesystemUploadFileContentType() { return filesystemUploadFileContentType; }
    /** 设置FilesystemUploadFileContentType */
    public void setFilesystemUploadFileContentType(String v) { this.filesystemUploadFileContentType = v; }

    /** 获取FilesystemUploadFileEtag */
    public String getFilesystemUploadFileEtag() { return filesystemUploadFileEtag; }
    /** 设置FilesystemUploadFileEtag */
    public void setFilesystemUploadFileEtag(String v) { this.filesystemUploadFileEtag = v; }

    /** 获取FilesystemUploadFileCompleteTime */
    public LocalDateTime getFilesystemUploadFileCompleteTime() { return filesystemUploadFileCompleteTime; }
    /** 设置FilesystemUploadFileCompleteTime */
    public void setFilesystemUploadFileCompleteTime(LocalDateTime v) { this.filesystemUploadFileCompleteTime = v; }
}
