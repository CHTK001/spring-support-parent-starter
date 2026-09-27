package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.chua.starter.mybatis.pojo.SysBase;
import java.time.LocalDateTime;

/**
 * 题库采集任务实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_collect_task")
public class EduCollectTask extends SysBase {

    /**
     * 采集任务标识
     */
    @TableId(type = IdType.AUTO)
    private Long collectTaskId;

    /**
     * 任务名称
     */
    private String collectTaskName;

    /**
     * 来源类型（即SPI Provider type）
     */
    private String collectTaskSourceType;

    /**
     * 来源配置（JSON）
     */
    private String collectTaskSourceConfig;

    /**
     * 学科
     */
    private String collectTaskSubject;

    /**
     * 学段
     */
    private String collectTaskGradeLevel;

    /**
     * 年级
     */
    private String collectTaskGrade;

    /**
     * 难度（1-5）
     */
    private Integer collectTaskDifficulty;

    /**
     * 状态（0=禁用, 1=启用，同步到 sys_job 触发状态；cron 只存 sys_job）
     */
    private Integer collectTaskStatus;

    /**
     * 备注
     */
    private String collectTaskRemark;

    /**
     * 累计采集题数
     */
    private Integer collectTaskTotalCount;

    /**
     * 最近执行时间
     */
    private LocalDateTime collectTaskLastRunTime;

    /**
     * 最近执行结果（0=失败, 1=成功）
     */
    private Integer collectTaskLastRunStatus;

    /**
     * 最近执行采集题数
     */
    private Integer collectTaskLastRunCount;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("collect_task_owner_user_id")
    private String ownerUserId;
}
