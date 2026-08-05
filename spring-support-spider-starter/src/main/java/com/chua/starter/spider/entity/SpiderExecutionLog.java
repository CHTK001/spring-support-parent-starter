package com.chua.starter.spider.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 执行日志实体。
 *
 * <p>对应 {@code spider_execution_log} 表，记录每次执行中每个节点的输入输出、
 * 状态与耗时，以 execution_no（traceId）串联一次执行的完整链路。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("spider_execution_log")
public class SpiderExecutionLog extends SpiderBase {

    /**
     * 执行号（traceId）
     */
    private String executionNo;

    /**
     * 爬虫编码
     */
    private String definitionCode;

    /**
     * 流程 ID
     */
    private Long flowId;

    /**
     * 节点 ID
     */
    private String nodeId;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 节点类型
     */
    private String nodeType;

    /**
     * 状态（RUNNING/SUCCESS/FAIL）
     */
    private String status;

    /**
     * 节点输入数据（JSON）
     */
    private String inputData;

    /**
     * 节点输出数据（JSON）
     */
    private String outputData;

    /**
     * 失败信息
     */
    private String errorMessage;

    /**
     * 节点开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startAt;

    /**
     * 节点结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endAt;
}