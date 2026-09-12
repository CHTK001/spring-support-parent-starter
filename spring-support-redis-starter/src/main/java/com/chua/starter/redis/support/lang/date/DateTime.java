package com.chua.starter.redis.support.lang.date;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
* 日期时间工具类
*
* @since 2024/12/25
* @author CH
 */
public final class DateTime {

    /** 本地日期时间 */
    private final LocalDateTime localDateTime;

    /**
    * 创建 日期时间 实例
    * @param localDateTime 本地日期时间
     */
    private DateTime(LocalDateTime localDateTime) {
        this.localDateTime = localDateTime;
    }

    /**
    * 获取当前时间
    *
    * @return DateTime 实例
     */
    public static DateTime now() {
        /**
        * 日期时间。
        * @param LocalDateTime.now( 本地日期时间.now(
         */
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
        /**
        * comparable日期时间。
        * @param lastTime 最后一个时间
        * @return comparable日期时间的结果
         */
        return new ComparableDateTime(lastTime);
    }

    /**
    * 获取 本地日期时间
    *
    * @return LocalDateTime
     */
    public LocalDateTime toLocalDateTime() {
        return localDateTime;
    }
}

