package com.chua.starter.device.support.timeseries;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 设备时序查询条件。
 *
 * <p>三种引擎都以这个条件为唯一查询口径：分类必填（避免跨分类混读），
 * 设备 / 序列键 / 时间窗可选，排序与条数由 {@code ascending} 与 {@code limit} 决定。</p>
 *
 * @since 4.0.0.55
 * @author CH
 */
@Data
public class DeviceSeriesQuery {

    /**
     * 单次查询默认条数
     */
    public static final int DEFAULT_LIMIT = 500;

    /**
     * 单次查询条数上限（页面分页在前端做，后端只挡住「一次拉全库」）
     */
    public static final int MAX_LIMIT = 5000;

    /**
     * yyyy-MM-dd 字符串长度
     */
    private static final int DATE_LENGTH = 10;

    /**
     * yyyy-MM-dd HH:mm 字符串长度
     */
    private static final int MINUTE_DATETIME_LENGTH = 16;

    /**
     * 秒级时间格式（页面与接口统一使用）
     */
    private static final DateTimeFormatter SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 分钟级时间格式
     */
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 分类编码，必填
     */
    private String category;

    /**
     * 设备 ID，可空（空表示该分类下全部设备）
     */
    private String deviceId;

    /**
     * 序列键，可空
     */
    private String key;

    /**
     * 起始时间（含），可空
     */
    private LocalDateTime from;

    /**
     * 结束时间（含），可空
     */
    private LocalDateTime to;

    /**
     * 条数，{@code <=0} 时取 {@link #DEFAULT_LIMIT}，超过 {@link #MAX_LIMIT} 时按上限截断。
     *
     * <p>语义是「最近的 N 条」，再按 {@link #ascending} 排序返回；
     * 三种引擎都按这个口径实现，避免升序视图拿到最老的一段。</p>
     */
    private int limit = DEFAULT_LIMIT;

    /**
     * 是否按时间升序；默认倒序（列表页先看最新的）
     */
    private boolean ascending;

    /**
     * 归一化后的条数。
     *
     * @return 1..{@link #MAX_LIMIT} 之间的条数
     */
    public int normalizedLimit() {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 解析时间文本。
     *
     * <p>支持三种常见写法，空值返回 {@code null}（表示不加时间条件）：</p>
     * <ul>
     *   <li>{@code yyyy-MM-dd HH:mm:ss}</li>
     *   <li>{@code yyyy-MM-dd HH:mm} / ISO {@code yyyy-MM-ddTHH:mm}</li>
     *   <li>{@code yyyy-MM-dd}（按当天 00:00:00）</li>
     * </ul>
     * <p>另外接受纯数字毫秒时间戳，便于前端直接传 {@code Date#getTime()}。</p>
     *
     * @param text 时间文本
     * @return 本地时间；无法解析时抛 {@link IllegalArgumentException}（不静默当空条件）
     */
    public static LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim().replace('T', ' ');
        try {
            if (value.matches("\\d{10,}")) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(value)), ZoneId.systemDefault());
            }
            if (value.length() == DATE_LENGTH) {
                return LocalDate.parse(value).atStartOfDay();
            }
            if (value.length() == MINUTE_DATETIME_LENGTH) {
                return LocalDateTime.parse(value, MINUTE);
            }
            return LocalDateTime.parse(value, SECOND);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("无法解析时间: " + text + "（支持 yyyy-MM-dd HH:mm:ss / yyyy-MM-dd / 毫秒时间戳）", ex);
        }
    }
}
