package com.chua.starter.spider.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 执行记录视图。
 *
 * <p>按 execution_no 聚合一次执行的开始/结束/状态等概要信息，
 * 用于执行历史分页与状态展示。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class ExecutionRecordVO {
    /**
     * 执行号
     */
    private String executionNo;
    /**
     * 触发类型（MANUAL / CRON / ONCE）
     */
    private String triggerType;
    /**
     * 执行状态（RUNNING / 成功 / 失败）
     */
    private String executionStatus;
    /**
     * 最大爬取深度
     */
    private Integer maxDepthReached;
    /**
     * 已采集数据条数
     */
    private Integer collectedCount;
    /**
     * 已完成 URL 数
     */
    private Integer urlCompleted;
    /**
     * 总 URL 数
     */
    private Integer urlTotal;
    /**
     * 开始时间
     */
    private LocalDateTime startedAt;
    /**
     * 结束时间
     */
    private LocalDateTime finishedAt;
}
