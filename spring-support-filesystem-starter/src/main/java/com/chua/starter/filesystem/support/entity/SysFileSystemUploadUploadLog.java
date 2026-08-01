package com.chua.starter.filesystem.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件系统上传 OSS 记录表（审计）。
 *
 * <p>每次上传尝试无论成败都写入一条记录，用于排查与追溯。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@TableName(value = "filesystem_upload_upload_log")
public class SysFileSystemUploadUploadLog extends SysBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "filesystem_upload_upload_log_id", type = IdType.AUTO)
    private Long filesystemUploadUploadLogId;

    /** 上传任务ID */
    @TableField("filesystem_upload_upload_log_upload_id")
    private String filesystemUploadUploadLogUploadId;

    /** 阶段标识: 2-上传OSS */
    @TableField("filesystem_upload_upload_log_stage")
    private Integer filesystemUploadUploadLogStage;

    /** 结果状态: 1-成功, 0-失败 */
    @TableField("filesystem_upload_upload_log_status")
    private Integer filesystemUploadUploadLogStatus;

    /** 记录信息（成功/失败原因） */
    @TableField("filesystem_upload_upload_log_message")
    private String filesystemUploadUploadLogMessage;

    /** 第几次尝试 */
    @TableField("filesystem_upload_upload_log_retry_count")
    private Integer filesystemUploadUploadLogRetryCount;

    /** 开始时间 */
    @TableField("filesystem_upload_upload_log_start_time")
    private LocalDateTime filesystemUploadUploadLogStartTime;

    /** 结束时间 */
    @TableField("filesystem_upload_upload_log_end_time")
    private LocalDateTime filesystemUploadUploadLogEndTime;

    public SysFileSystemUploadUploadLog() {}

    public Long getFilesystemUploadUploadLogId() { return filesystemUploadUploadLogId; }
    public void setFilesystemUploadUploadLogId(Long v) { this.filesystemUploadUploadLogId = v; }

    public String getFilesystemUploadUploadLogUploadId() { return filesystemUploadUploadLogUploadId; }
    public void setFilesystemUploadUploadLogUploadId(String v) { this.filesystemUploadUploadLogUploadId = v; }

    public Integer getFilesystemUploadUploadLogStage() { return filesystemUploadUploadLogStage; }
    public void setFilesystemUploadUploadLogStage(Integer v) { this.filesystemUploadUploadLogStage = v; }

    public Integer getFilesystemUploadUploadLogStatus() { return filesystemUploadUploadLogStatus; }
    public void setFilesystemUploadUploadLogStatus(Integer v) { this.filesystemUploadUploadLogStatus = v; }

    public String getFilesystemUploadUploadLogMessage() { return filesystemUploadUploadLogMessage; }
    public void setFilesystemUploadUploadLogMessage(String v) { this.filesystemUploadUploadLogMessage = v; }

    public Integer getFilesystemUploadUploadLogRetryCount() { return filesystemUploadUploadLogRetryCount; }
    public void setFilesystemUploadUploadLogRetryCount(Integer v) { this.filesystemUploadUploadLogRetryCount = v; }

    public LocalDateTime getFilesystemUploadUploadLogStartTime() { return filesystemUploadUploadLogStartTime; }
    public void setFilesystemUploadUploadLogStartTime(LocalDateTime v) { this.filesystemUploadUploadLogStartTime = v; }

    public LocalDateTime getFilesystemUploadUploadLogEndTime() { return filesystemUploadUploadLogEndTime; }
    public void setFilesystemUploadUploadLogEndTime(LocalDateTime v) { this.filesystemUploadUploadLogEndTime = v; }

    /**
     * 上传结果状态常量。
     */
    public static class Result {
        public static final int SUCCESS = 1;
        public static final int FAILED = 0;
    }

    /**
     * 阶段标识常量。
     */
    public static class Stage {
        public static final int UPLOAD = 2;
    }
}
