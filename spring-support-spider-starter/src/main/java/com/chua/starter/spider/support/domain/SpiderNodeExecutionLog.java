package com.chua.starter.spider.support.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 节点执行日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("spider_node_execution_log")
public class SpiderNodeExecutionLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联执行记录 ID */
    @TableField("record_id")
    private Long recordId;

    /** 关联任务 ID */
    @TableField("task_id")
    private Long taskId;

    /** 节点 ID */
    @TableField("node_id")
    private String nodeId;

    /** 节点类型 */
    @TableField("node_type")
    private String nodeType;

    /** 执行状态 */
    private String status;

    /** 开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 执行耗时（毫秒） */
    @TableField("duration_ms")
    private Long durationMs;

    /** 输入摘要 */
    @TableField("input_summary")
    private String inputSummary;

    /** 输出摘要 */
    @TableField("output_summary")
    private String outputSummary;

    /** 成功处理数 */
    @TableField("success_count")
    private Long successCount;

    /** 失败处理数 */
    @TableField("failure_count")
    private Long failureCount;

    /** 错误信息 */
    @TableField("error_msg")
    private String errorMsg;

    /** 重试次数 */
    @TableField("retry_count")
    private Integer retryCount;

    /** 是否使用了 AI */
    @TableField("ai_used")
    private Boolean aiUsed;

    /** AI 消耗 token 数 */
    @TableField("ai_tokens")
    private Integer aiTokens;
}
