package com.chua.starter.redis.support.utils;

/**
 * 数字工具类
 *
 * @author CH
 * @since 2024/12/25
 */
public final class NumberUtils {

    private NumberUtils() {
    }

    /**
     * 判断字符串是否为数字
     *
     * @param str 待判断的字符串
     * @return 如果字符串可以解析为数字，返回 true；否则返回 false
     */
    public static boolean isNumber(String str) {
        if (StringUtils.isBlank(str)) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 判断字符串是否为整数
     *
     * @param str 待判断的字符串
     * @return 如果字符串可以解析为整数，返回 true；否则返回 false
     */
    public static boolean isInteger(String str) {
        if (StringUtils.isBlank(str)) {
            return false;
        }
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

