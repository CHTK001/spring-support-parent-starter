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
 * @since 4.0.0.42
 * @author CH
 */
@TableName(value = "filesystem_upload_merge_log")
public class SysFileSystemUploadMergeLog extends SysBase implements Serializable {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    @TableId(value = "filesystem_upload_merge_log_id", type = IdType.AUTO)
    /** Filesystem上传merge日志ID */
    private Long filesystemUploadMergeLogId;

    /** 上传任务ID */
    @TableField("filesystem_upload_merge_log_upload_id")
    /** Filesystem上传merge日志上传ID */
    private String filesystemUploadMergeLogUploadId;

    /** 阶段标识: 1-合并 */
    @TableField("filesystem_upload_merge_log_stage")
    /** Filesystem上传merge日志阶段 */
    private Integer filesystemUploadMergeLogStage;

    /** 结果状态: 1-成功, 0-失败 */
    @TableField("filesystem_upload_merge_log_status")
    /** Filesystem上传merge日志状态 */
    private Integer filesystemUploadMergeLogStatus;

    /** 记录信息（成功/失败原因） */
    @TableField("filesystem_upload_merge_log_message")
    /** Filesystem上传merge日志消息 */
    private String filesystemUploadMergeLogMessage;

    /** 第几次尝试 */
    @TableField("filesystem_upload_merge_log_retry_count")
    /** Filesystem上传merge日志重试数量 */
    private Integer filesystemUploadMergeLogRetryCount;

    /** 开始时间 */
    @TableField("filesystem_upload_merge_log_start_time")
    /** Filesystem上传merge日志开始时间 */
    private LocalDateTime filesystemUploadMergeLogStartTime;

    /** 结束时间 */
    @TableField("filesystem_upload_merge_log_end_time")
    /** Filesystem上传merge日志结束时间 */
    private LocalDateTime filesystemUploadMergeLogEndTime;

    /** 创建 SysFileSystemUploadMergeLog 实例 */
    public SysFileSystemUploadMergeLog() {}

    /** 获取FilesystemUpload合并记录日志Id */
    public Long getFilesystemUploadMergeLogId() { return filesystemUploadMergeLogId; }
    /** 设置FilesystemUpload合并记录日志Id */
    public void setFilesystemUploadMergeLogId(Long v) { this.filesystemUploadMergeLogId = v; }

    /** 获取FilesystemUpload合并记录日志UploadId */
    public String getFilesystemUploadMergeLogUploadId() { return filesystemUploadMergeLogUploadId; }
    /** 设置FilesystemUpload合并记录日志UploadId */
    public void setFilesystemUploadMergeLogUploadId(String v) { this.filesystemUploadMergeLogUploadId = v; }

    /** 获取FilesystemUpload合并记录日志Stage */
    public Integer getFilesystemUploadMergeLogStage() { return filesystemUploadMergeLogStage; }
    /** 设置FilesystemUpload合并记录日志Stage */
    public void setFilesystemUploadMergeLogStage(Integer v) { this.filesystemUploadMergeLogStage = v; }

    /** 获取FilesystemUpload合并记录日志Status */
    public Integer getFilesystemUploadMergeLogStatus() { return filesystemUploadMergeLogStatus; }
    /** 设置FilesystemUpload合并记录日志Status */
    public void setFilesystemUploadMergeLogStatus(Integer v) { this.filesystemUploadMergeLogStatus = v; }

    /** 获取FilesystemUpload合并记录日志Message */
    public String getFilesystemUploadMergeLogMessage() { return filesystemUploadMergeLogMessage; }
    /** 设置FilesystemUpload合并记录日志Message */
    public void setFilesystemUploadMergeLogMessage(String v) { this.filesystemUploadMergeLogMessage = v; }

    /** 获取FilesystemUpload合并记录日志Retry计算数量 */
    public Integer getFilesystemUploadMergeLogRetryCount() { return filesystemUploadMergeLogRetryCount; }
    /** 设置FilesystemUpload合并记录日志Retry计算数量 */
    public void setFilesystemUploadMergeLogRetryCount(Integer v) { this.filesystemUploadMergeLogRetryCount = v; }

    /** 获取FilesystemUpload合并记录日志开始Time */
    public LocalDateTime getFilesystemUploadMergeLogStartTime() { return filesystemUploadMergeLogStartTime; }
    /** 设置FilesystemUpload合并记录日志开始Time */
    public void setFilesystemUploadMergeLogStartTime(LocalDateTime v) { this.filesystemUploadMergeLogStartTime = v; }

    /** 获取FilesystemUpload合并记录日志EndTime */
    public LocalDateTime getFilesystemUploadMergeLogEndTime() { return filesystemUploadMergeLogEndTime; }
    /** 设置FilesystemUpload合并记录日志EndTime */
    public void setFilesystemUploadMergeLogEndTime(LocalDateTime v) { this.filesystemUploadMergeLogEndTime = v; }

    /**
     * 合并结果状态常量。
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
        /** Merge */
        public static final int MERGE = 1;
    }
}
