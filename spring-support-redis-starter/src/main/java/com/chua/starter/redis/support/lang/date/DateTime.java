package com.chua.starter.redis.support.lang.date;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 日期时间工具类
 *
 * @author CH
 * @since 2024/12/25
 */
public final class DateTime {

    private final LocalDateTime localDateTime;

    private DateTime(LocalDateTime localDateTime) {
        this.localDateTime = localDateTime;
    }

    /**
     * 获取当前时间
     *
     * @return DateTime 实例
     */
    public static DateTime now() {
        return new DateTime(LocalDateTime.now());
    }

    /**
     * 获取当天的最后时刻（23:59:59.999）
     *
     * @return ComparableDateTime 实例
     */
    public ComparableDateTime withLastTimeOfDay() {
        LocalDateTime lastTime = localDateTime.withHour(23)
                .withMinute(59)
                .withSecond(59)
                .withNano(999_999_999);
        return new ComparableDateTime(lastTime);
    }

    /**
     * 获取 LocalDateTime
     *
     * @return LocalDateTime
     */
    public LocalDateTime toLocalDateTime() {
        return localDateTime;
    }
}

