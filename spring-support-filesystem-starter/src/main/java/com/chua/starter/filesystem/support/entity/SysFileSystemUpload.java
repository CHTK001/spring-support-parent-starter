package com.chua.starter.filesystem.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "文件系统分片上传任务表")
@TableName(value = "filesystem_upload_task")
public class SysFileSystemUpload extends SysBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "filesystem_upload_task_id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long filesystemUploadTaskId;

    @TableField(value = "filesystem_upload_task_upload_id")
    @Schema(description = "上传任务ID（ULID）")
    private String filesystemUploadTaskUploadId;

    @TableField("filesystem_upload_task_file_name")
    @Schema(description = "文件名称")
    private String filesystemUploadTaskFileName;

    @TableField("filesystem_upload_task_original_name")
    @Schema(description = "文件原始名称")
    private String filesystemUploadTaskOriginalName;

    @TableField("filesystem_upload_task_file_size")
    @Schema(description = "文件大小(字节)")
    private Long filesystemUploadTaskFileSize;

    @TableField("filesystem_upload_task_chunk_size")
    @Schema(description = "分片大小(字节)")
    private Long filesystemUploadTaskChunkSize;

    @TableField("filesystem_upload_task_total_chunks")
    @Schema(description = "总分片数")
    private Integer filesystemUploadTaskTotalChunks;

    @TableField("filesystem_upload_task_uploaded_chunks")
    @Schema(description = "已上传分片数")
    private Integer filesystemUploadTaskUploadedChunks;

    @TableField("filesystem_upload_task_storage_type")
    @Schema(description = "存储类型")
    private String filesystemUploadTaskStorageType;

    @TableField("filesystem_upload_task_bucket")
    @Schema(description = "存储桶")
    private String filesystemUploadTaskBucket;

    @TableField("filesystem_upload_task_key")
    @Schema(description = "文件Key")
    private String filesystemUploadTaskKey;

    @TableField("filesystem_upload_task_content_type")
    @Schema(description = "内容类型")
    private String filesystemUploadTaskContentType;

    @TableField("filesystem_upload_task_status")
    @Schema(description = "状态: 0-待上传, 1-上传中, 2-待合并, 3-合并中, 4-已完成, -1-失败, -2-已取消")
    private Integer filesystemUploadTaskStatus;

    @TableField("filesystem_upload_task_error_message")
    @Schema(description = "错误信息")
    private String filesystemUploadTaskErrorMessage;

    @TableField("filesystem_upload_task_file_url")
    @Schema(description = "已完成文件URL")
    private String filesystemUploadTaskFileUrl;

    @TableField("filesystem_upload_task_start_time")
    @Schema(description = "上传开始时间")
    private LocalDateTime filesystemUploadTaskStartTime;

    @TableField("filesystem_upload_task_complete_time")
    @Schema(description = "上传完成时间")
    private LocalDateTime filesystemUploadTaskCompleteTime;

    public SysFileSystemUpload() {}

    public Long getFilesystemUploadTaskId() { return filesystemUploadTaskId; }
    public void setFilesystemUploadTaskId(Long filesystemUploadTaskId) { this.filesystemUploadTaskId = filesystemUploadTaskId; }

    public String getFilesystemUploadTaskUploadId() { return filesystemUploadTaskUploadId; }
    public void setFilesystemUploadTaskUploadId(String filesystemUploadTaskUploadId) { this.filesystemUploadTaskUploadId = filesystemUploadTaskUploadId; }

    public String getFilesystemUploadTaskFileName() { return filesystemUploadTaskFileName; }
    public void setFilesystemUploadTaskFileName(String filesystemUploadTaskFileName) { this.filesystemUploadTaskFileName = filesystemUploadTaskFileName; }

    public String getFilesystemUploadTaskOriginalName() { return filesystemUploadTaskOriginalName; }
    public void setFilesystemUploadTaskOriginalName(String filesystemUploadTaskOriginalName) { this.filesystemUploadTaskOriginalName = filesystemUploadTaskOriginalName; }

    public Long getFilesystemUploadTaskFileSize() { return filesystemUploadTaskFileSize; }
    public void setFilesystemUploadTaskFileSize(Long filesystemUploadTaskFileSize) { this.filesystemUploadTaskFileSize = filesystemUploadTaskFileSize; }

    public Long getFilesystemUploadTaskChunkSize() { return filesystemUploadTaskChunkSize; }
    public void setFilesystemUploadTaskChunkSize(Long filesystemUploadTaskChunkSize) { this.filesystemUploadTaskChunkSize = filesystemUploadTaskChunkSize; }

    public Integer getFilesystemUploadTaskTotalChunks() { return filesystemUploadTaskTotalChunks; }
    public void setFilesystemUploadTaskTotalChunks(Integer filesystemUploadTaskTotalChunks) { this.filesystemUploadTaskTotalChunks = filesystemUploadTaskTotalChunks; }

    public Integer getFilesystemUploadTaskUploadedChunks() { return filesystemUploadTaskUploadedChunks; }
    public void setFilesystemUploadTaskUploadedChunks(Integer filesystemUploadTaskUploadedChunks) { this.filesystemUploadTaskUploadedChunks = filesystemUploadTaskUploadedChunks; }

    public String getFilesystemUploadTaskStorageType() { return filesystemUploadTaskStorageType; }
    public void setFilesystemUploadTaskStorageType(String filesystemUploadTaskStorageType) { this.filesystemUploadTaskStorageType = filesystemUploadTaskStorageType; }

    public String getFilesystemUploadTaskBucket() { return filesystemUploadTaskBucket; }
    public void setFilesystemUploadTaskBucket(String filesystemUploadTaskBucket) { this.filesystemUploadTaskBucket = filesystemUploadTaskBucket; }

    public String getFilesystemUploadTaskKey() { return filesystemUploadTaskKey; }
    public void setFilesystemUploadTaskKey(String filesystemUploadTaskKey) { this.filesystemUploadTaskKey = filesystemUploadTaskKey; }

    public String getFilesystemUploadTaskContentType() { return filesystemUploadTaskContentType; }
    public void setFilesystemUploadTaskContentType(String filesystemUploadTaskContentType) { this.filesystemUploadTaskContentType = filesystemUploadTaskContentType; }

    public Integer getFilesystemUploadTaskStatus() { return filesystemUploadTaskStatus; }
    public void setFilesystemUploadTaskStatus(Integer filesystemUploadTaskStatus) { this.filesystemUploadTaskStatus = filesystemUploadTaskStatus; }

    public String getFilesystemUploadTaskErrorMessage() { return filesystemUploadTaskErrorMessage; }
    public void setFilesystemUploadTaskErrorMessage(String filesystemUploadTaskErrorMessage) { this.filesystemUploadTaskErrorMessage = filesystemUploadTaskErrorMessage; }

    public String getFilesystemUploadTaskFileUrl() { return filesystemUploadTaskFileUrl; }
    public void setFilesystemUploadTaskFileUrl(String filesystemUploadTaskFileUrl) { this.filesystemUploadTaskFileUrl = filesystemUploadTaskFileUrl; }

    public LocalDateTime getFilesystemUploadTaskStartTime() { return filesystemUploadTaskStartTime; }
    public void setFilesystemUploadTaskStartTime(LocalDateTime filesystemUploadTaskStartTime) { this.filesystemUploadTaskStartTime = filesystemUploadTaskStartTime; }

    public LocalDateTime getFilesystemUploadTaskCompleteTime() { return filesystemUploadTaskCompleteTime; }
    public void setFilesystemUploadTaskCompleteTime(LocalDateTime filesystemUploadTaskCompleteTime) { this.filesystemUploadTaskCompleteTime = filesystemUploadTaskCompleteTime; }

    public static class Status {
        public static final int PENDING = 0;
        public static final int UPLOADING = 1;
        public static final int PENDING_MERGE = 2;
        public static final int MERGING = 3;
        public static final int COMPLETED = 4;
        public static final int FAILED = -1;
        public static final int CANCELLED = -2;
    }
}