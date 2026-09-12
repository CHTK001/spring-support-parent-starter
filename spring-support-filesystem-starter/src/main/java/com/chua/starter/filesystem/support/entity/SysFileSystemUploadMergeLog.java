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

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    @TableId(value = "filesystem_upload_merge_log_id", type = IdType.AUTO)
    /** Filesystem上传合并日志标识 */
    private Long filesystemUploadMergeLogId;

    /** 上传任务标识 */
    @TableField("filesystem_upload_merge_log_upload_id")
    /** Filesystem上传合并日志上传标识 */
    private String filesystemUploadMergeLogUploadId;

    /** 阶段标识: 1-合并 */
    @TableField("filesystem_upload_merge_log_stage")
    /** Filesystem上传合并日志阶段 */
    private Integer filesystemUploadMergeLogStage;

    /** 结果状态: 1-成功, 0-失败 */
    @TableField("filesystem_upload_merge_log_status")
    /** Filesystem上传合并日志状态 */
    private Integer filesystemUploadMergeLogStatus;

    /** 记录信息（成功/失败原因） */
    @TableField("filesystem_upload_merge_log_message")
    /** Filesystem上传合并日志消息 */
    private String filesystemUploadMergeLogMessage;

    /** 第几次尝试 */
    @TableField("filesystem_upload_merge_log_retry_count")
    /** Filesystem上传合并日志重试数量 */
    private Integer filesystemUploadMergeLogRetryCount;

    /** 开始时间 */
    @TableField("filesystem_upload_merge_log_start_time")
    /** Filesystem上传合并日志开始时间 */
    private LocalDateTime filesystemUploadMergeLogStartTime;

    /** 结束时间 */
    @TableField("filesystem_upload_merge_log_end_time")
    /** Filesystem上传合并日志结束时间 */
    private LocalDateTime filesystemUploadMergeLogEndTime;

    /** 创建 sys文件系统upload合并日志 实例 */
    public SysFileSystemUploadMergeLog() {}

    /**
      * 获取filesystemupload合并记录日志标识
     *
     * @return 获取filesystemupload合并日志id的结果
     */
    public Long getFilesystemUploadMergeLogId() { return filesystemUploadMergeLogId; }
    /**
      * 设置filesystemupload合并记录日志标识
     *
     * @param v v
     */
    public void setFilesystemUploadMergeLogId(Long v) { this.filesystemUploadMergeLogId = v; }

    /**
     * 获取filesystemupload合并记录日志uploadid
     *
     * @return 获取filesystemupload合并日志uploadid的结果
     */
    public String getFilesystemUploadMergeLogUploadId() { return filesystemUploadMergeLogUploadId; }
    /**
     * 设置filesystemupload合并记录日志uploadid
     *
     * @param v v
     */
    public void setFilesystemUploadMergeLogUploadId(String v) { this.filesystemUploadMergeLogUploadId = v; }

    /**
     * 获取filesystemupload合并记录日志Stage
     *
     * @return 获取filesystemupload合并日志Stage的结果
     */
    public Integer getFilesystemUploadMergeLogStage() { return filesystemUploadMergeLogStage; }
    /**
     * 设置filesystemupload合并记录日志Stage
     *
     * @param v v
     */
    public void setFilesystemUploadMergeLogStage(Integer v) { this.filesystemUploadMergeLogStage = v; }

    /**
     * 获取filesystemupload合并记录日志状态
     *
     * @return 获取filesystemupload合并日志状态的结果
     */
    public Integer getFilesystemUploadMergeLogStatus() { return filesystemUploadMergeLogStatus; }
    /**
     * 设置filesystemupload合并记录日志状态
     *
     * @param v v
     */
    public void setFilesystemUploadMergeLogStatus(Integer v) { this.filesystemUploadMergeLogStatus = v; }

    /**
     * 获取filesystemupload合并记录日志消息
     *
     * @return 获取filesystemupload合并日志消息的结果
     */
    public String getFilesystemUploadMergeLogMessage() { return filesystemUploadMergeLogMessage; }
    /**
     * 设置filesystemupload合并记录日志消息
     *
     * @param v v
     */
    public void setFilesystemUploadMergeLogMessage(String v) { this.filesystemUploadMergeLogMessage = v; }

    /**
     * 获取filesystemupload合并记录日志重试计算数量
     *
     * @return 获取filesystemupload合并日志重试数量的结果
     */
    public Integer getFilesystemUploadMergeLogRetryCount() { return filesystemUploadMergeLogRetryCount; }
    /**
     * 设置filesystemupload合并记录日志重试计算数量
     *
     * @param v v
     */
    public void setFilesystemUploadMergeLogRetryCount(Integer v) { this.filesystemUploadMergeLogRetryCount = v; }

    /**
     * 获取filesystemupload合并记录日志开始时间
     *
     * @return 获取filesystemupload合并日志启动时间的结果
     */
    public LocalDateTime getFilesystemUploadMergeLogStartTime() { return filesystemUploadMergeLogStartTime; }
    /**
     * 设置filesystemupload合并记录日志开始时间
     *
     * @param v v
     */
    public void setFilesystemUploadMergeLogStartTime(LocalDateTime v) { this.filesystemUploadMergeLogStartTime = v; }

    /**
     * 获取filesystemupload合并记录日志结束时间
     *
     * @return 获取filesystemupload合并日志结束时间的结果
     */
    public LocalDateTime getFilesystemUploadMergeLogEndTime() { return filesystemUploadMergeLogEndTime; }
    /**
     * 设置filesystemupload合并记录日志结束时间
     *
     * @param v v
     */
    public void setFilesystemUploadMergeLogEndTime(LocalDateTime v) { this.filesystemUploadMergeLogEndTime = v; }

    /**
     * 合并结果状态常量。
     * @author CH
     * @since 4.0.0
     */
    public static class Result {
        /** 成功 */
        public static final int SUCCESS = 1;
        /** 失败 */
        public static final int FAILED = 0;
    }

    /**
     * 阶段标识常量。
     * @author CH
     * @since 4.0.0
     */
    public static class Stage {
        /** 合并 */
        public static final int MERGE = 1;
    }
}
