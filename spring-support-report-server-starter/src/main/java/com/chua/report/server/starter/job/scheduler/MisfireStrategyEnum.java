package com.chua.report.server.starter.job.scheduler;


import lombok.Getter;

/**
 * @author xuxueli 2020-10-29 21:11:23
 */
@Getter
public enum MisfireStrategyEnum {

    /**
     * do nothing
     */
    DO_NOTHING("misfire_strategy_do_nothing"),

    /**
     * fire once now
     */
    FIRE_ONCE_NOW("misfire_strategy_fire_once_now");

    private final String title;

    MisfireStrategyEnum(String title) {
        this.title = title;
    }

    public static MisfireStrategyEnum match(String name, MisfireStrategyEnum defaultItem) {
        for (MisfireStrategyEnum item : MisfireStrategyEnum.values()) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return defaultItem;
    }

}
