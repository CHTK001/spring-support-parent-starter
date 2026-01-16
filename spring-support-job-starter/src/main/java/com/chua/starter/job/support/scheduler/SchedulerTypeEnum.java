package com.chua.starter.job.support.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务调度类型枚举
 * <p>
 * 定义任务的调度触发方式，支持CRON表达式和固定频率两种模式。
 * </p>
 *
 * <h3>调度类型说明</h3>
 * <ul>
 *     <li><b>NONE</b> - 无调度，仅支持手动触发</li>
 *     <li><b>CRON</b> - 使用CRON表达式定义执行时间，如 "0 0 12 * * ?"表示每天中午12点</li>
 *     <li><b>FIXED</b> - 固定频率执行，配置值为间隔秒数，如 "60"表示每60秒执行一次</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 * @see JobHelper#generateNextValidTime(SysJob, Date)
 * @see CoreTriggerHandler
 */
@AllArgsConstructor
@Getter
public enum SchedulerTypeEnum {

    /**
     * 无调度，仅支持手动触发或API触发
     */
    NONE("无"),
    
    /**
     * CRON表达式调度
     * <p>配置值示例: "0 0/5 * * * ?" 表示每5分钟执行一次</p>
     */
    CRON("cron"),
    
    /**
     * 固定频率调度
     * <p>配置值为间隔秒数，如 "30" 表示每30秒执行一次</p>
     */
    FIXED("固定频率"),
    ;
    private final String name;


    /**
     * 匹配
     *
     * @param name        名称
     * @param defaultItem 默认项目
     * @return {@link SchedulerTypeEnum}
     */
    public static SchedulerTypeEnum match(String name, SchedulerTypeEnum defaultItem){
        for (SchedulerTypeEnum item: SchedulerTypeEnum.values()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return defaultItem;
    }

}
