package com.chua.starter.job.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务日志备份记录
 * <p>
 * 记录日志压缩备份的历史记录
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 */
@Data
@TableName(value = "sys_job_log_backup")
public class SysJobLogBackup implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 备份记录ID
     */
    @TableId(value = "job_log_backup_id", type = IdType.AUTO)
    private Long jobLogBackupId;

    /**
     * 备份文件名
     */
    @TableField(value = "job_log_backup_file_name")
    private String jobLogBackupFileName;

    /**
     * 备份文件路径
     */
    @TableField(value = "job_log_backup_file_path")
    private String jobLogBackupFilePath;

    /**
     * 备份文件大小(字节)
     */
    @TableField(value = "job_log_backup_file_size")
    private Long jobLogBackupFileSize;

    /**
     * 备份日志条数
     */
    @TableField(value = "job_log_backup_count")
    private Long jobLogBackupCount;

    /**
     * 备份日志开始日期
     */
    @TableField(value = "job_log_backup_start_date")
    private LocalDate jobLogBackupStartDate;

    /**
     * 备份日志结束日期
     */
    @TableField(value = "job_log_backup_end_date")
    private LocalDate jobLogBackupEndDate;

    /**
     * 备份状态: RUNNING/SUCCESS/FAILED
     */
    @TableField(value = "job_log_backup_status")
    private String jobLogBackupStatus;

    /**
     * 备份类型: AUTO/MANUAL
     */
    @TableField(value = "job_log_backup_type")
    private String jobLogBackupType;

    /**
     * 备份开始时间
     */
    @TableField(value = "job_log_backup_start_time")
    private LocalDateTime jobLogBackupStartTime;

    /**
     * 备份完成时间
     */
    @TableField(value = "job_log_backup_end_time")
    private LocalDateTime jobLogBackupEndTime;

    /**
     * 备份耗时(毫秒)
     */
    @TableField(value = "job_log_backup_cost")
    private Long jobLogBackupCost;

    /**
     * 备份消息/错误信息
     */
    @TableField(value = "job_log_backup_message")
    private String jobLogBackupMessage;

    /**
     * 压缩格式: ZIP/GZIP/TAR
     */
    @TableField(value = "job_log_backup_compress_type")
    private String jobLogBackupCompressType;

    /**
     * 是否已删除原始日志: 0否 1是
     */
    @TableField(value = "job_log_backup_cleaned")
    private Integer jobLogBackupCleaned;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;
}
