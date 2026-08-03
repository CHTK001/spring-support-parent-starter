package com.chua.starter.spider.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 爬虫执行记录实体。
 * <p>记录一次爬取任务的元信息与统计数据。{@code spiderMaxDepthReached} 表示本次执行实际到达的最大深度。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("spider_execution_record")
@Schema(description = "爬虫执行记录")
public class SpiderExecutionRecord {

    /**
     * 主键，自增
     */
    @TableId(value = "spider_id", type = IdType.AUTO)
    private Long spiderId;

    /**
     * 执行批次号，唯一
     */
    @TableField("spider_execution_no")
    private String spiderExecutionNo;

    /**
     * 爬虫业务编码
     */
    @TableField("spider_definition_code")
    private String spiderDefinitionCode;

    /**
     * 触发方式：MANUAL / CRON / API
     */
    @TableField("spider_trigger_type")
    private String spiderTriggerType;

    /**
     * 执行状态：SUCCESS / FAILED / RUNNING / PAUSED
     */
    @TableField("spider_execution_status")
    private String spiderExecutionStatus;

    /**
     * 本次执行实际到达的最大深度
     */
    @TableField("spider_max_depth_reached")
    private Integer spiderMaxDepthReached;

    /**
     * 采集数据条数
     */
    @TableField("spider_collected_count")
    private Integer spiderCollectedCount;

    /**
     * 计划抓取的 URL 总数
     */
    @TableField("spider_url_total")
    private Integer spiderUrlTotal;

    /**
     * 已完成的 URL 数
     */
    @TableField("spider_url_completed")
    private Integer spiderUrlCompleted;

    /**
     * 开始时间
     */
    @TableField("spider_started_at")
    private LocalDateTime spiderStartedAt;

    /**
     * 结束时间
     */
    @TableField("spider_finished_at")
    private LocalDateTime spiderFinishedAt;

    /**
     * 失败时的错误信息
     */
    @TableField("spider_error_message")
    private String spiderErrorMessage;

    /**
     * 创建时间，插入自动填充
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
