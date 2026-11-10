package com.chua.starter.monitor.support.operlog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 操作日志查询的时间参数解析。
 *
 * <p>为什么不直接用 {@code @RequestParam LocalDateTime}：项目里注册了全局
 * {@code StringToLocalDateTimeTypeConverter}，它遇到不认识的格式会**返回 null**，
 * 于是 {@code from=2026-11-10T05:00:00} 这种标准 ISO 写法会静默变成「不限时间」，
 * 接口看起来成功、返回的却是全量。这里把参数当字符串收进来，按固定格式表解析：
 * 认识的格式一定生效，认不出的一律报错，不再有「悄悄忽略」这条路。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
public final class OperLogTimeParser {

    /**
     * 可接受的「日期 + 时间」格式，按从精确到宽松的顺序尝试。
     */
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

    /**
     * 只有日期的写法补成当天零点。
     */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private OperLogTimeParser() {
    }

    /**
     * 解析查询时间参数。
     *
     * @param text 原始参数，空则返回 null 表示不限
     * @return 本地时间；空参数返回 null
     * @throws IllegalArgumentException 格式无法识别
     */
    public static LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 换下一个格式继续试：格式表就是候选清单
            }
        }
        try {
            return LocalDate.parse(value, DATE_FORMAT).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式不正确: " + value);
        }
    }
}
