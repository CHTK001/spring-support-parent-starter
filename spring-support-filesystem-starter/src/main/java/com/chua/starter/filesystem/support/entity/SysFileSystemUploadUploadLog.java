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
 * @since 4.0.0.42
 * @author CH
 */
@TableName(value = "filesystem_upload_upload_log")
public class SysFileSystemUploadUploadLog extends SysBase implements Serializable {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    @TableId(value = "filesystem_upload_upload_log_id", type = IdType.AUTO)
    /** Filesystem上传上传日志ID */
    private Long filesystemUploadUploadLogId;

    /** 上传任务ID */
    @TableField("filesystem_upload_upload_log_upload_id")
    /** Filesystem上传上传日志上传ID */
    private String filesystemUploadUploadLogUploadId;

    /** 阶段标识: 2-上传OSS */
    @TableField("filesystem_upload_upload_log_stage")
    /** Filesystem上传上传日志阶段 */
    private Integer filesystemUploadUploadLogStage;

    /** 结果状态: 1-成功, 0-失败 */
    @TableField("filesystem_upload_upload_log_status")
    /** Filesystem上传上传日志状态 */
    private Integer filesystemUploadUploadLogStatus;

    /** 记录信息（成功/失败原因） */
    @TableField("filesystem_upload_upload_log_message")
    /** Filesystem上传上传日志消息 */
    private String filesystemUploadUploadLogMessage;

    /** 第几次尝试 */
    @TableField("filesystem_upload_upload_log_retry_count")
    /** Filesystem上传上传日志重试数量 */
    private Integer filesystemUploadUploadLogRetryCount;

    /** 开始时间 */
    @TableField("filesystem_upload_upload_log_start_time")
    /** Filesystem上传上传日志开始时间 */
    private LocalDateTime filesystemUploadUploadLogStartTime;

    /** 结束时间 */
    @TableField("filesystem_upload_upload_log_end_time")
    /** Filesystem上传上传日志结束时间 */
    private LocalDateTime filesystemUploadUploadLogEndTime;

    /** 创建 SysFileSystemUploadUploadLog 实例 */
    public SysFileSystemUploadUploadLog() {}

    /** 获取FilesystemUploadUpload记录日志Id */
    public Long getFilesystemUploadUploadLogId() { return filesystemUploadUploadLogId; }
    /** 设置FilesystemUploadUpload记录日志Id */
    public void setFilesystemUploadUploadLogId(Long v) { this.filesystemUploadUploadLogId = v; }

    /** 获取FilesystemUploadUpload记录日志UploadId */
    public String getFilesystemUploadUploadLogUploadId() { return filesystemUploadUploadLogUploadId; }
    /** 设置FilesystemUploadUpload记录日志UploadId */
    public void setFilesystemUploadUploadLogUploadId(String v) { this.filesystemUploadUploadLogUploadId = v; }

    /** 获取FilesystemUploadUpload记录日志Stage */
    public Integer getFilesystemUploadUploadLogStage() { return filesystemUploadUploadLogStage; }
    /** 设置FilesystemUploadUpload记录日志Stage */
    public void setFilesystemUploadUploadLogStage(Integer v) { this.filesystemUploadUploadLogStage = v; }

    /** 获取FilesystemUploadUpload记录日志Status */
    public Integer getFilesystemUploadUploadLogStatus() { return filesystemUploadUploadLogStatus; }
    /** 设置FilesystemUploadUpload记录日志Status */
    public void setFilesystemUploadUploadLogStatus(Integer v) { this.filesystemUploadUploadLogStatus = v; }

    /** 获取FilesystemUploadUpload记录日志Message */
    public String getFilesystemUploadUploadLogMessage() { return filesystemUploadUploadLogMessage; }
    /** 设置FilesystemUploadUpload记录日志Message */
    public void setFilesystemUploadUploadLogMessage(String v) { this.filesystemUploadUploadLogMessage = v; }

    /** 获取FilesystemUploadUpload记录日志Retry计算数量 */
    public Integer getFilesystemUploadUploadLogRetryCount() { return filesystemUploadUploadLogRetryCount; }
    /** 设置FilesystemUploadUpload记录日志Retry计算数量 */
    public void setFilesystemUploadUploadLogRetryCount(Integer v) { this.filesystemUploadUploadLogRetryCount = v; }

    /** 获取FilesystemUploadUpload记录日志开始Time */
    public LocalDateTime getFilesystemUploadUploadLogStartTime() { return filesystemUploadUploadLogStartTime; }
    /** 设置FilesystemUploadUpload记录日志开始Time */
    public void setFilesystemUploadUploadLogStartTime(LocalDateTime v) { this.filesystemUploadUploadLogStartTime = v; }

    /** 获取FilesystemUploadUpload记录日志EndTime */
    public LocalDateTime getFilesystemUploadUploadLogEndTime() { return filesystemUploadUploadLogEndTime; }
    /** 设置FilesystemUploadUpload记录日志EndTime */
    public void setFilesystemUploadUploadLogEndTime(LocalDateTime v) { this.filesystemUploadUploadLogEndTime = v; }

    /**
     * 上传结果状态常量。
     */
    public static class Result {
        /** Success */
        public static final int SUCCESS = 1;
        /** Failed */
        public static final int FAILED = 0;
    }

    /**
     * 阶段标识常量。
     */
    public static class Stage {
        /** 上传 */
        public static final int UPLOAD = 2;
    }
}
