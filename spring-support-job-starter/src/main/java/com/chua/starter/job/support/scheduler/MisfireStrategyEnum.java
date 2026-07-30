package com.chua.starter.job.support.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务失效策略枚举
 * <p>
 * 定义当任务触发时间超时（超过预读时间窗口5秒）时的处理策略。
 * 超时情况可能由于服务重启、系统负载过高等原因导致。
 * </p>
 *
 * <h3>应用场景</h3>
 * <ul>
 *     <li><b>DO_NOTHING</b> - 适用于对时效性要求不高的任务，如每日统计</li>
 *     <li><b>FIRE_ONCE_NOW</b> - 适用于重要任务，确保至少执行一次</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 * @see CoreTriggerHandler
 */
@AllArgsConstructor
@Getter
public enum MisfireStrategyEnum {

    /**
     * 忽略失效
     * <p>
     * 跳过本次失效的执行，直接计算并更新下次触发时间。
     * 适用于非关键性任务，错过就算了。
     * </p>
     */
    DO_NOTHING("do_nothing"),

    /**
     * 立即触发一次
     * <p>
     * 虽然超时，仍然立即触发执行一次，然后更新下次触发时间。
     * 适用于重要任务，确保不会因临时问题而遗漏执行。
     * </p>
     */
    FIRE_ONCE_NOW("fire_once_now");

    private final String name;

    /**
     * 匹配
     *
     * @param name        名称
     * @param defaultItem 默认项目
     * @return {@link MisfireStrategyEnum}
     */
    public static MisfireStrategyEnum match(String name, MisfireStrategyEnum defaultItem) {
        for (MisfireStrategyEnum item : MisfireStrategyEnum.values()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return defaultItem;
    }
}
