package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集调度总览VO（定时任务页数据）
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class CollectScheduleOverviewVO {

    /**
     * 服务器当前时间（前端倒计时基准）
     */
    private LocalDateTime serverTime;

    /**
     * 派发器任务键（注册于 sys_job）
     */
    private String dispatchJobKey;

    /**
     * 未配置 Cron 任务的默认调度周期
     */
    private String defaultCron;

    /**
     * 已注册的采集来源类型（SPI）
     */
    private List<String> sourceTypes;

    /**
     * 任务调度视图行
     */
    private List<CollectScheduleTaskVO> tasks;
}
