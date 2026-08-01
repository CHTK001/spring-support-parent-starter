package com.chua.starter.filesystem.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件系统分片合并记录表（审计）。
 *
 * <p>每次合并尝试无论成败都写入一条记录，用于排查与追溯。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@TableName(value = "filesystem_upload_merge_log")
public class SysFileSystemUploadMergeLog extends SysBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "filesystem_upload_merge_log_id", type = IdType.AUTO)
    private Long filesystemUploadMergeLogId;

    /** 上传任务ID */
    @TableField("filesystem_upload_merge_log_upload_id")
    private String filesystemUploadMergeLogUploadId;

    /** 阶段标识: 1-合并 */
    @TableField("filesystem_upload_merge_log_stage")
    private Integer filesystemUploadMergeLogStage;

    /** 结果状态: 1-成功, 0-失败 */
    @TableField("filesystem_upload_merge_log_status")
    private Integer filesystemUploadMergeLogStatus;

    /** 记录信息（成功/失败原因） */
    @TableField("filesystem_upload_merge_log_message")
    private String filesystemUploadMergeLogMessage;

    /** 第几次尝试 */
    @TableField("filesystem_upload_merge_log_retry_count")
    private Integer filesystemUploadMergeLogRetryCount;

    /** 开始时间 */
    @TableField("filesystem_upload_merge_log_start_time")
    private LocalDateTime filesystemUploadMergeLogStartTime;

    /** 结束时间 */
    @TableField("filesystem_upload_merge_log_end_time")
    private LocalDateTime filesystemUploadMergeLogEndTime;

    public SysFileSystemUploadMergeLog() {}

    public Long getFilesystemUploadMergeLogId() { return filesystemUploadMergeLogId; }
    public void setFilesystemUploadMergeLogId(Long v) { this.filesystemUploadMergeLogId = v; }

    public String getFilesystemUploadMergeLogUploadId() { return filesystemUploadMergeLogUploadId; }
    public void setFilesystemUploadMergeLogUploadId(String v) { this.filesystemUploadMergeLogUploadId = v; }

    public Integer getFilesystemUploadMergeLogStage() { return filesystemUploadMergeLogStage; }
    public void setFilesystemUploadMergeLogStage(Integer v) { this.filesystemUploadMergeLogStage = v; }

    public Integer getFilesystemUploadMergeLogStatus() { return filesystemUploadMergeLogStatus; }
    public void setFilesystemUploadMergeLogStatus(Integer v) { this.filesystemUploadMergeLogStatus = v; }

    public String getFilesystemUploadMergeLogMessage() { return filesystemUploadMergeLogMessage; }
    public void setFilesystemUploadMergeLogMessage(String v) { this.filesystemUploadMergeLogMessage = v; }

    public Integer getFilesystemUploadMergeLogRetryCount() { return filesystemUploadMergeLogRetryCount; }
    public void setFilesystemUploadMergeLogRetryCount(Integer v) { this.filesystemUploadMergeLogRetryCount = v; }

    public LocalDateTime getFilesystemUploadMergeLogStartTime() { return filesystemUploadMergeLogStartTime; }
    public void setFilesystemUploadMergeLogStartTime(LocalDateTime v) { this.filesystemUploadMergeLogStartTime = v; }

    public LocalDateTime getFilesystemUploadMergeLogEndTime() { return filesystemUploadMergeLogEndTime; }
    public void setFilesystemUploadMergeLogEndTime(LocalDateTime v) { this.filesystemUploadMergeLogEndTime = v; }

    /**
     * 合并结果状态常量。
     */
    public static class Result {
        public static final int SUCCESS = 1;
        public static final int FAILED = 0;
    }

    /**
     * 阶段标识常量。
     */
    public static class Stage {
        public static final int MERGE = 1;
    }
}
