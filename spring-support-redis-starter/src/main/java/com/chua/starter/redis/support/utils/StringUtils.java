package com.chua.starter.redis.support.utils;

/**
 * 字符串工具类
 *
 * @author CH
 * @since 2024/12/25
 */
public final class StringUtils {

    private StringUtils() {
    }

    /**
     * 判断字符串是否为空
     *
     * @param str 待判断的字符串
     * @return 如果字符串为 null 或空字符串，返回 true；否则返回 false
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否不为空
     *
     * @param str 待判断的字符串
     * @return 如果字符串不为 null 且不为空字符串，返回 true；否则返回 false
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 判断字符串是否为空白（null、空字符串或只包含空白字符）
     *
     * @param str 待判断的字符串
     * @return 如果字符串为 null、空字符串或只包含空白字符，返回 true；否则返回 false
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 判断字符串是否不为空白
     *
     * @param str 待判断的字符串
     * @return 如果字符串不为 null、不为空字符串且不只包含空白字符，返回 true；否则返回 false
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 如果字符串为 null 或空，返回默认值
     *
     * @param str        待检查的字符串
     * @param defaultStr 默认值
     * @return 如果字符串为 null 或空，返回默认值；否则返回原字符串
     */
    public static String defaultString(String str, String defaultStr) {
        return isEmpty(str) ? defaultStr : str;
    }
}

