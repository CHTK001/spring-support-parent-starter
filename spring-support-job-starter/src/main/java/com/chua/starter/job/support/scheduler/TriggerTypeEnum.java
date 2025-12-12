package com.chua.starter.job.support.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 触发器类型枚举
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@AllArgsConstructor
@Getter
public enum TriggerTypeEnum {

    /**
     * 手动触发，需要用户手动发起触发指令
     */
    MANUAL("jobconf_trigger_type_manual"),
    /**
     * 定时触发，根据CRON表达式定时触发作业
     */
    CRON("jobconf_trigger_type_cron"),
    /**
     * 重试触发，当作业执行失败时，根据重试策略进行重试触发
     */
    RETRY("jobconf_trigger_type_retry"),
    /**
     * 父作业触发，作为某个父作业的子作业，依赖于父作业的完成状态触发
     */
    PARENT("jobconf_trigger_type_parent"),
    /**
     * API触发，通过调用API接口触发作业执行
     */
    API("jobconf_trigger_type_api"),
    /**
     * 误火触发，用于处理作业触发器的误火情况，即在预定触发时间之外触发的情况
     */
    MISFIRE("jobconf_trigger_type_misfire");

    private String name;

}
