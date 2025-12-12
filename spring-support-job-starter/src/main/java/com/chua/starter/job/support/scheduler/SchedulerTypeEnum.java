package com.chua.starter.job.support.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 调度程序类型枚举
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@AllArgsConstructor
@Getter
public enum SchedulerTypeEnum {

    NONE("无"),
    CRON("cron"),
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
