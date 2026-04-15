package com.chua.starter.spider.support.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务运行时快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("spider_runtime_snapshot")
public class SpiderRuntimeSnapshot {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的任务 ID */
    private Long taskId;

    /** 当前任务状态 */
    private SpiderTaskStatus status;

    /** 最近执行时间 */
    @TableField("last_execute_time")
    private LocalDateTime lastExecuteTime;

    /** 成功计数 */
    @TableField("success_count")
    private Long successCount;

    /** 失败计数 */
    @TableField("failure_count")
    private Long failureCount;

    /** 最近错误摘要 */
    @TableField("last_error_summary")
    private String lastErrorSummary;

    /** Job 绑定状态（是否已绑定 job-starter） */
    @TableField("job_bound")
    private Boolean jobBound;

    /** 节点日志摘要（JSON 数组，每个节点最近一条日志） */
    @TableField("node_log_summary")
    private String nodeLogSummary;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
