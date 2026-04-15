package com.chua.starter.spider.support.domain;

import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 爬虫任务执行策略
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpiderExecutionPolicy {

    /** 执行类型：ONCE / REPEAT_N / SCHEDULED */
    private SpiderExecutionType executionType;

    /** 并发线程数 */
    private Integer threadCount;

    /** 重试策略 */
    private RetryPolicy retryPolicy;

    /** 重复次数（REPEAT_N 专用） */
    private Integer repeatTimes;

    /** 执行间隔（秒，REPEAT_N 专用） */
    private Long repeatInterval;

    /** Cron 表达式（SCHEDULED 专用） */
    private String cron;

    /** 时区（SCHEDULED 专用，如 Asia/Shanghai） */
    private String timezone;

    /** 错过触发策略（SCHEDULED 专用，如 DO_NOTHING、FIRE_ONCE_NOW） */
    private String misfirePolicy;

    /** 调度通道（SCHEDULED 专用） */
    private String jobChannel;

    /** 是否为手动触发（ONCE 类型为 true） */
    private Boolean manualTrigger;
}
