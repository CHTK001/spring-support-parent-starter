package com.chua.common.support.utils;

/**
 * 前置条件检查工具类
 *
 * @author CH
 */
public class Preconditions {

    /**
     * 检查表达式是否为 true
     *
     * @param expression 表达式
     * @param message 错误消息
     */
    public static void checkArgument(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 检查对象是否为 null
     *
     * @param reference 对象
     * @param message 错误消息
     */
    public static void checkNotNull(Object reference, String message) {
        if (reference == null) {
            throw new NullPointerException(message);
        }
    }

    /**
     * 检查状态是否为 true
     *
     * @param expression 表达式
     * @param message 错误消息
     */
    public static void checkState(boolean expression, String message) {
        if (!expression) {
            throw new IllegalStateException(message);
        }
    }

    public static <T> T notEmpty(T reference, String message, String errorCode) {
        if (reference == null || (reference instanceof String && ((String) reference).isEmpty())) {
            throw new IllegalArgumentException("[" + errorCode + "] " + message);
        }
        return reference;
    }
}
