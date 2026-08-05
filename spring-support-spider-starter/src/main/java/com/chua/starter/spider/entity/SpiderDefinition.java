package com.chua.starter.spider.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 爬虫定义实体。
 *
 * <p>对应 {@code spider_definition} 表，描述一个爬虫的基本信息：编码、名称、
 * 状态、关联流程与定时调度表达式。cron_expression 为空时不参与自动调度。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("spider_definition")
public class SpiderDefinition extends SpiderBase {

    /**
     * 爬虫唯一编码
     */
    private String code;

    /**
     * 爬虫名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 关联流程 ID（spider_flow.id）
     */
    private Long flowId;

    /**
     * 是否启用（1 启用 / 0 停用）
     */
    private Integer enabled;

    /**
     * 定时 cron 表达式，为空则不自动调度
     */
    private String cronExpression;

    /**
     * 上次执行时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastRunAt;

    /**
     * 上次执行状态（SUCCESS/FAIL/RUNNING）
     */
    private String lastStatus;

    /**
     * 上次执行消息
     */
    private String lastMessage;
}