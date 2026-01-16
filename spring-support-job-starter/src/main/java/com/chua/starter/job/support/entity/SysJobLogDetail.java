package com.chua.starter.job.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务调度日志详情
 * <p>
 * 存储任务执行过程中的详细日志记录，支持文件归档
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 */
@Data
@TableName(value = "sys_job_log_detail")
public class SysJobLogDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志详情ID
     */
    @TableId(value = "job_log_detail_id", type = IdType.AUTO)
    private Long jobLogDetailId;

    /**
     * 关联的任务日志ID
     */
    @TableField(value = "job_log_id")
    private Integer jobLogId;

    /**
     * 任务ID
     */
    @TableField(value = "job_id")
    private Integer jobId;

    /**
     * 日志级别: DEBUG/INFO/WARN/ERROR
     */
    @TableField(value = "job_log_detail_level")
    private String jobLogDetailLevel;

    /**
     * 日志内容
     */
    @TableField(value = "job_log_detail_content")
    private String jobLogDetailContent;

    /**
     * 日志时间戳
     */
    @TableField(value = "job_log_detail_time")
    private LocalDateTime jobLogDetailTime;

    /**
     * 执行阶段: START/RUNNING/END
     */
    @TableField(value = "job_log_detail_phase")
    private String jobLogDetailPhase;

    /**
     * 执行进度(0-100)
     */
    @TableField(value = "job_log_detail_progress")
    private Integer jobLogDetailProgress;

    /**
     * 日志文件路径（如果已归档到文件）
     */
    @TableField(value = "job_log_detail_file_path")
    private String jobLogDetailFilePath;

    /**
     * 执行的方法/Handler名
     */
    @TableField(value = "job_log_detail_handler")
    private String jobLogDetailHandler;

    /**
     * 执行环境/Profile
     */
    @TableField(value = "job_log_detail_profile")
    private String jobLogDetailProfile;

    /**
     * 执行地址
     */
    @TableField(value = "job_log_detail_address")
    private String jobLogDetailAddress;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;
}
