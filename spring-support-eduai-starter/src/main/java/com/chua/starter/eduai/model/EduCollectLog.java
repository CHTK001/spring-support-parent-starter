package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题库采集执行日志实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("edu_collect_log")
public class EduCollectLog {

    /**
     * 日志标识
     */
    @TableId(type = IdType.AUTO)
    private Long collectLogId;

    /**
     * 采集任务标识
     */
    private Long collectLogTaskId;

    /**
     * 任务名称快照
     */
    private String collectLogTaskName;

    /**
     * 触发方式（AUTO=定时, MANUAL=手动）
     */
    private String collectLogTriggerType;

    /**
     * 来源类型快照
     */
    private String collectLogSourceType;

    /**
     * 执行状态（0=执行中, 1=成功, 2=失败）
     */
    private Integer collectLogStatus;

    /**
     * 本次采集入库题数
     */
    private Integer collectLogQuestionCount;

    /**
     * 失败原因
     */
    private String collectLogErrorMsg;

    /**
     * 开始时间
     */
    private LocalDateTime collectLogStartTime;

    /**
     * 结束时间
     */
    private LocalDateTime collectLogEndTime;

    /**
     * 创建时间
     */
    private LocalDateTime collectLogCreateTime;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("collect_log_owner_user_id")
    private String ownerUserId;
}
