package com.chua.common.support.utils;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

/**
 * 随机工具类
 * <p>
 * 提供随机数、随机字符串、UUID 等生成方法
 * </p>
 *
 * @since 2025-07-22
 * @author CH
 */
public class RandomUtils {

    /** 随机数生成器 */
    private static final Random RANDOM = new SecureRandom();

    /** 数字字符集 */
    private static final String DIGITS = "0123456789";
    /** 字母字符集 */
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /** 字母数字混合字符集 */
    private static final String LETTERS_DIGITS = DIGITS + LETTERS;

    /** 创建 RandomUtils 实例 */
    private RandomUtils() {
    }

    /**
     * 生成指定范围内的随机整数（含下限，不含上限）
     *
     * @param min 下限（包含）
     * @param max 上限（不包含）
     * @return 随机整数
     */
    public static int nextInt(int min, int max) {
        if (min >= max) {
            return min;
        }
        return RANDOM.nextInt(max - min) + min;
    }

    /**
     * 生成指定范围内的随机整数（别名方法）
     *
     * @param min 下限（包含）
     * @param max 上限（不包含）
     * @return 随机整数
     */
    public static int randomInt(int min, int max) {
        return nextInt(min, max);
    }

    /**
     * 从枚举类中随机取一个枚举值
     *
     * @param enumClass 枚举类
     * @param <T>       枚举类型
     * @return 随机枚举值
     */
    public static <T extends Enum<T>> T randomEnum(Class<T> enumClass) {
        if (enumClass == null) {
            return null;
        }
        T[] constants = enumClass.getEnumConstants();
        if (constants == null || constants.length == 0) {
            return null;
        }
        return constants[RANDOM.nextInt(constants.length)];
    }

    /**
     * 生成指定长度的随机数字字符串
     *
     * @param length 长度
     * @return 随机数字字符串
     */
    public static String randomNumeric(int length) {
        if (length <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        }
        return sb.toString();
    }

    /**
     * 生成指定长度的随机字母字符串
     *
     * @param length 长度
     * @return 随机字母字符串
     */
    public static String randomAlphabetic(int length) {
        if (length <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        return sb.toString();
    }

    /**
     * 生成指定长度的随机字母数字混合字符串
     *
     * @param length 长度
     * @return 随机字母数字混合字符串
     */
    public static String randomAlphanumeric(int length) {
        if (length <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(LETTERS_DIGITS.charAt(RANDOM.nextInt(LETTERS_DIGITS.length())));
        }
        return sb.toString();
    }

    /**
     * 生成不含连字符的 UUID 字符串
     *
     * @return 32 位 UUID 字符串
     */
    public static String randomUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成标准 UUID 字符串（含连字符）
     *
     * @return 标准 UUID 字符串
     */
    public static String randomStandardUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 从数组中随机取一个元素
     *
     * @param array 数组
     * @param <T>   元素类型
     * @return 随机元素，数组为空返回 null
     */
    @SafeVarargs
    public static <T> T randomElement(T... array) {
        if (array == null || array.length == 0) {
            return null;
        }
        return array[RANDOM.nextInt(array.length)];
    }
}
