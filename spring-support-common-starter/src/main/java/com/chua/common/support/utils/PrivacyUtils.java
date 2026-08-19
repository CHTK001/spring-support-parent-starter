package com.chua.common.support.utils;

/**
 * 隐私脱敏工具类
 * <p>
 * 提供手机号、邮箱、身份证号等敏感信息的脱敏处理
 * </p>
 *
 * @since 2025-07-22
 * @author CH
 */
public class PrivacyUtils {

    /** 默认脱敏字符 */
    private static final String MASK = "****";

    private PrivacyUtils() {
    }

    /**
     * 手机号脱敏
     * <p>
     * 保留前3位和后4位，中间用 **** 代替
     * 例如：138****5678
     * </p>
     *
     * @param phone 手机号
     * @return 脱敏后的手机号，入参为 null 或格式异常返回原值
     */
    public static String mobile(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + MASK + phone.substring(phone.length() - 4);
    }

    /**
     * 邮箱脱敏
     * <p>
     * 保留邮箱前缀首字符和 @ 及后面的域名，前缀其余部分用 **** 代替
     * 例如：a****@example.com
     * </p>
     *
     * @param email 邮箱地址
     * @return 脱敏后的邮箱，入参为 null 或格式异常返回原值
     */
    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        return email.charAt(0) + MASK + email.substring(atIndex);
    }

    /**
     * 身份证号脱敏
     * <p>
     * 保留前4位和后4位，中间用 **** 代替
     * 例如：1101****1234
     * </p>
     *
     * @param idCard 身份证号
     * @return 脱敏后的身份证号，入参为 null 或格式异常返回原值
     */
    public static String idCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 4) + MASK + idCard.substring(idCard.length() - 4);
    }

    /**
     * 通用脱敏处理
     *
     * @param value 原始值
     * @param front 保留前几位
     * @param after 保留后几位
     * @param mask  脱敏字符
     * @return 脱敏后的字符串
     */
    public static String desValue(String value, Integer front, Integer after, String mask) {
        if (value == null) {
            return null;
        }
        int frontLen = front != null ? front : 0;
        int afterLen = after != null ? after : 0;
        String maskStr = mask != null ? mask : MASK;
        if (value.length() <= frontLen + afterLen) {
            return value;
        }
        return value.substring(0, frontLen) + maskStr + value.substring(value.length() - afterLen);
    }

    /**
     * 中文姓名脱敏（只显示姓）
     *
     * @param name 中文姓名
     * @return 脱敏后的姓名，如 "张**"
     */
    public static String hideChineseName(String name) {
        if (name == null) {
            return null;
        }
        if (name.length() <= 1) {
            return name;
        }
        return name.charAt(0) + "**";
    }

    /**
     * 卡片号码脱敏（信用卡/银行卡）
     *
     * @param card 卡片号码
     * @return 脱敏后的卡片号码
     */
    public static String hideCard(String card) {
        if (card == null) {
            return null;
        }
        return desValue(card, 4, 4, MASK);
    }

    /**
     * 手机号脱敏（兼容 hidePhone 命名）
     *
     * @param phone 手机号
     * @return 脱敏后的手机号
     */
    public static String hidePhone(String phone) {
        return mobile(phone);
    }

    /**
     * 邮箱脱敏（兼容 hideEmail 命名）
     *
     * @param email 邮箱地址
     * @return 脱敏后的邮箱
     */
    public static String hideEmail(String email) {
        return email(email);
    }

    /**
     * 地址脱敏
     *
     * @param address 地址
     * @param keepLen 保留前几位
     * @return 脱敏后的地址
     */
    public static String hideAddress(String address, int keepLen) {
        if (address == null) {
            return null;
        }
        if (address.length() <= keepLen) {
            return address;
        }
        return address.substring(0, keepLen) + MASK;
    }

    /**
     * 银行卡号脱敏
     *
     * @param bankCard 银行卡号
     * @return 脱敏后的银行卡号
     */
    public static String hideBankCard(String bankCard) {
        return hideCard(bankCard);
    }

    /**
     * 密码脱敏（全部隐藏）
     *
     * @param password 密码
     * @return 全部脱敏为 ******
     */
    public static String hidePassword(String password) {
        if (password == null) {
            return null;
        }
        return "******";
    }

    /**
     * 车牌号脱敏
     *
     * @param carNumber 车牌号
     * @return 脱敏后的车牌号
     */
    public static String hideCarNumber(String carNumber) {
        if (carNumber == null) {
            return null;
        }
        if (carNumber.length() <= 3) {
            return carNumber;
        }
        return carNumber.substring(0, 3) + "***";
    }
}
