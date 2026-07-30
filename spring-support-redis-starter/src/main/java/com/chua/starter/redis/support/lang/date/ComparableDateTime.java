package com.chua.starter.redis.support.lang.date;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 可比较的日期时间类
 *
 * @author CH
 * @since 2024/12/25
 */
public final class ComparableDateTime {

    private final LocalDateTime localDateTime;

    ComparableDateTime(LocalDateTime localDateTime) {
        this.localDateTime = localDateTime;
    }

    /**
     * 计算与指定时间之间的秒数差
     *
     * @param other 另一个时间
     * @return 秒数差
     */
    public long betweenOfSeconds(LocalDateTime other) {
        Duration duration = Duration.between(localDateTime, other);
        return Math.abs(duration.getSeconds());
    }
}

