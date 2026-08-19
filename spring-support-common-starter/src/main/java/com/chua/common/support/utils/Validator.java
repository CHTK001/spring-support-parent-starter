package com.chua.common.support.utils;

import java.util.regex.Pattern;

/**
 * 验证器工具类
 * <p>
 * 提供手机号、邮箱、身份证号等常见格式的验证
 * </p>
 *
 * @since 2025-07-22
 * @author CH
 */
public class Validator {

    /** Mobile_pattern */
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    /** Email_pattern */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    /** Id_card_pattern */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$");
    /** Ip_pattern */
    private static final Pattern IP_PATTERN = Pattern.compile("^(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    /** 创建 Validator 实例 */
    private Validator() {
    }

    /**
     * 验证手机号格式（中国）
     *
     * @param phone 手机号
     * @return 格式正确返回 true，否则返回 false
     */
    public static boolean isMobile(String phone) {
        if (phone == null) {
            return false;
        }
        return MOBILE_PATTERN.matcher(phone.trim()).matches();
    }

    /**
     * 验证邮箱格式
     *
     * @param email 邮箱地址
     * @return 格式正确返回 true，否则返回 false
     */
    public static boolean isEmail(String email) {
        if (email == null) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * 验证身份证号格式（18 位）
     *
     * @param idCard 身份证号
     * @return 格式正确返回 true，否则返回 false
     */
    public static boolean isIdCard(String idCard) {
        if (idCard == null) {
            return false;
        }
        return ID_CARD_PATTERN.matcher(idCard.trim()).matches();
    }

    /**
     * 验证 IPv4 地址格式
     *
     * @param ip IP 地址
     * @return 格式正确返回 true，否则返回 false
     */
    public static boolean isIp(String ip) {
        if (ip == null) {
            return false;
        }
        return IP_PATTERN.matcher(ip.trim()).matches();
    }

    /**
     * 验证字符串是否全部由数字组成
     *
     * @param str 字符串
     * @return 全部为数字返回 true，否则返回 false
     */
    public static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 验证字符串是否为纯字母
     *
     * @param str 字符串
     * @return 全部为字母返回 true，否则返回 false
     */
    public static boolean isAlpha(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isLetter(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 验证字符串是否为字母数字组合
     *
     * @param str 字符串
     * @return 全部为字母或数字返回 true，否则返回 false
     */
    public static boolean isAlphanumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isLetterOrDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 验证 URL 格式（http/https 开头）
     *
     * @param url URL 字符串
     * @return 格式正确返回 true，否则返回 false
     */
    public static boolean isUrl(String url) {
        if (url == null) {
            return false;
        }
        String trimmed = url.trim().toLowerCase();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    /**
     * 判断字符串是否包含中文字符
     *
     * @param str 字符串
     * @return 包含中文字符返回 true，否则返回 false
     */
    public static boolean hasChinese(String str) {
        if (str == null) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}
