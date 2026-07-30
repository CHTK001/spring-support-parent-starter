package com.chua.common.support.utils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 日期时间工具类
 *
 * <p>
 * 提供 LocalTime、LocalDate、LocalDateTime 的解析与格式化功能。
 * 支持多种字符串格式，包括 ISO 格式和中文习惯的格式（如 "13时30分"）。
 * </p>
 *
 * <p>
 * 主要功能：
 * <ul>
 *   <li>{@code toLocalTime(String)} 字符串转 {@link LocalTime}</li>
 *   <li>{@code toLocalDate(String)} 字符串转 {@link java.time.LocalDate}</li>
 *   <li>{@code format(LocalTime)} 时间格式化</li>
 *   <li>{@code format(LocalDate)} 日期格式化</li>
 * </ul>
 * </p>
 *
 * @author CH
 */
public class DateUtils {

    /**
     * 时间格式化器（ISO："HH:mm:ss"）
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 日期格式化器（ISO："yyyy-MM-dd"）
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 日期时间格式化器（ISO："yyyy-MM-dd HH:mm:ss"）
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateUtils() {
    }

    /**
     * 将字符串解析为 {@link LocalTime} 对象
     * <p>
     * 支持的格式：
     * </p>
     * <ul>
     *   <li>ISO_LOCAL_TIME: "10:15:30"</li>
     *   <li>自定义格式: "10:15:30"</li>
     *   <li>中文格式: "10时15分30秒"、"10时15分"、"10点15分30秒"等</li>
     * </ul>
     *
     * @param time 时间字符串
     * @return 本地时间，解析失败时返回 {@code null}
     */
    public static LocalTime toLocalTime(String time) {
        if (time == null) {
            return null;
        }
        String trimmed = time.trim();
        try {
            return LocalTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (DateTimeParseException e) {
            // 尝试标准格式化器 "HH:mm:ss"
            try {
                return LocalTime.parse(trimmed, TIME_FORMATTER);
            } catch (DateTimeParseException ex) {
                // 尝试中文格式: 替换"时"/"点"为":"，替换"分"为":"，去掉"秒"
                String normalized = trimmed.replace("时", ":")
                                            .replace("点", ":")
                                            .replace("分", ":")
                                            .replace("秒", "");
                try {
                    return LocalTime.parse(normalized, TIME_FORMATTER);
                } catch (DateTimeParseException px) {
                    return null;
                }
            }
        }
    }

    /**
     * 将字符串解析为 {@link java.time.LocalDate} 对象
     * <p>
     * 支持的格式:
     * </p>
     * <ul>
     *   <li>ISO_LOCAL_DATE: "2025-07-25"</li>
     *   <li>自定义格式: "2025-07-25"</li>
     *   <li>中文格式: "2025年07月25日"</li>
     * </ul>
     *
     * @param date 日期字符串
     * @return 本地日期，解析失败时返回 {@code null}
     */
    public static java.time.LocalDate toLocalDate(String date) {
        if (date == null) {
            return null;
        }
        String trimmed = date.trim();
        try {
            return java.time.LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            try {
                return java.time.LocalDate.parse(trimmed, DATE_FORMATTER);
            } catch (DateTimeParseException ex) {
                String normalized = trimmed.replace("年", "-")
                                            .replace("月", "-")
                                            .replace("日", "");
                try {
                    return java.time.LocalDate.parse(normalized, DATE_FORMATTER);
                } catch (DateTimeParseException px) {
                    return null;
                }
            }
        }
    }

    /**
     * 解析日期字符串为 {@link java.util.Date} 对象
     * <p>
     * 优先尝试 {@code java.sql.Date.valueOf} 解析 "yyyy-MM-dd" 格式，
     * 失败后回退到 {@link #toLocalDate(String)} 再转为 {@link java.sql.Date}。
     * </p>
     *
     * @param date 日期字符串
     * @return Date 实例，解析失败返回 null
     */
    public static java.util.Date parseDate(String date) {
        if (date == null) {
            return null;
        }
        String trimmed = date.trim();
        try {
            return java.sql.Date.valueOf(trimmed);
        } catch (Exception e) {
            java.time.LocalDate localDate = toLocalDate(trimmed);
            if (localDate != null) {
                return java.sql.Date.valueOf(localDate);
            }
            return null;
        }
    }

    /**
     * 格式化时间对象为字符串 "HH:mm:ss"
     *
     * @param time LocalTime 对象
     * @return 格式化后的字符串，如果 time 为 null 则返回 "00:00:00"
     */
    public static String format(LocalTime time) {
        if (time == null) {
            return "00:00:00";
        }
        return time.format(TIME_FORMATTER);
    }

    /**
     * 格式化日期对象为字符串 "yyyy-MM-dd"
     *
     * @param date LocalDate 对象
     * @return 格式化后的字符串，如果 date 为 null 则返回 "1970-01-01"
     */
    public static String format(java.time.LocalDate date) {
        if (date == null) {
            return "1970-01-01";
        }
        return date.format(DATE_FORMATTER);
    }

    /**
     * 将字符串解析为 LocalDateTime 对象
     * <p>
     * 支持的格式：
     * </p>
     * <ul>
     *   <li>ISO_LOCAL_DATE_TIME: "2025-07-25T13:30:45"</li>
     *   <li>"yyyy-MM-dd HH:mm:ss"</li>
     * </ul>
     *
     * @param dateTime 日期时间字符串
     * @return LocalDateTime 实例，解析失败或参数为 null 时返回 null
     */
    public static java.time.LocalDateTime toLocalDateTime(String dateTime) {
        if (dateTime == null) {
            return null;
        }
        String trimmed = dateTime.trim().replace('T', ' ');
        try {
            return java.time.LocalDateTime.parse(trimmed, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            try {
                return java.time.LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    /**
     * 格式化 LocalDateTime 对象为字符串 "yyyy-MM-dd HH:mm:ss"
     *
     * @param dateTime LocalDateTime 对象
     * @return 格式化后的字符串，如果 dateTime 为 null 则返回 "1970-01-01 00:00:00"
     */
    public static String format(java.time.LocalDateTime dateTime) {
        if (dateTime == null) {
            return "1970-01-01 00:00:00";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}