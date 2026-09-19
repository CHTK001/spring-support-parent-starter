package com.chua.starter.redis.support.lang.date;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 可比较的日期时间类
 *
 * @since 2024/12/25
 * @author CH
 */
public final class ComparableDateTime {

    /**
     * 本地日期时间
    */
    private final LocalDateTime localDateTime;

    /**
     * comparable日期时间。
     * @param localDateTime 本地日期时间
     */
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

