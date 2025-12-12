package com.chua.starter.job.support.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 失效策略枚举
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@AllArgsConstructor
@Getter
public enum MisfireStrategyEnum {

    /**
     * 什么都不做
     */
    DO_NOTHING("do_nothing"),

    /**
     * 立即触发一次
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
