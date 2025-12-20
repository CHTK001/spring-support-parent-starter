package com.chua.starter.job.support.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务触发类型枚举
 * <p>
 * 定义任务被触发执行的来源类型，用于日志记录和统计分析。
 * </p>
 *
 * <h3>触发来源</h3>
 * <ul>
 *     <li><b>MANUAL</b> - 用户通过控制台手动触发</li>
 *     <li><b>CRON</b> - 由CRON调度器自动触发</li>
 *     <li><b>RETRY</b> - 任务失败后的重试触发</li>
 *     <li><b>PARENT</b> - 父任务完成后触发子任务</li>
 *     <li><b>API</b> - 通过REST API接口触发</li>
 *     <li><b>MISFIRE</b> - 失效补征触发</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 * @see LocalJobTrigger
 * @see JobTriggerPoolHelper
 */
@AllArgsConstructor
@Getter
public enum TriggerTypeEnum {

    /**
     * 手动触发
     * <p>用户通过控制台界面手动发起的触发</p>
     */
    MANUAL("jobconf_trigger_type_manual"),
    
    /**
     * 定时触发
     * <p>由CRON调度器根据表达式配置自动触发</p>
     */
    CRON("jobconf_trigger_type_cron"),
    
    /**
     * 重试触发
     * <p>任务执行失败后，根据重试策略自动触发的重新执行</p>
     */
    RETRY("jobconf_trigger_type_retry"),
    
    /**
     * 父任务触发
     * <p>父任务执行完成后自动触发子任务，用于任务链场景</p>
     */
    PARENT("jobconf_trigger_type_parent"),
    
    /**
     * API触发
     * <p>通过调用REST API接口程序化触发任务执行</p>
     */
    API("jobconf_trigger_type_api"),
    
    /**
     * 失效补征触发
     * <p>当任务触发时间超时且失效策略为FIRE_ONCE_NOW时的补征触发</p>
     */
    MISFIRE("jobconf_trigger_type_misfire");

    private String name;

}
