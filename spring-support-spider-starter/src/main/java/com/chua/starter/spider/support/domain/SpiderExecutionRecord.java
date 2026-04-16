package com.chua.starter.spider.support.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单次执行记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("spider_execution_record")
public class SpiderExecutionRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的任务 ID */
    private Long taskId;

    /** 执行类型 */
    @TableField("execution_type")
    private SpiderExecutionType executionType;

    /** 开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 成功计数 */
    @TableField("success_count")
    private Long successCount;

    /** 失败计数 */
    @TableField("failure_count")
    private Long failureCount;

    /** URL 收集总数 */
    @TableField("total_requests")
    private Long totalRequests;

    /** 触发来源（MANUAL / SCHEDULED） */
    @TableField("trigger_source")
    private String triggerSource;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
