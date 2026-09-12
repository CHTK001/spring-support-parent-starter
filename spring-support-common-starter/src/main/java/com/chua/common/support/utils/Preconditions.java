package com.chua.common.support.utils;

/**
* 前置条件检查工具类
*
* @author CH
* @since 4.0.0
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
            /**
            * illegal参数异常。
            * @param message 消息
            * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException(message);
        }
    }

    /**
    * 检查对象是否为 空
    *
    * @param reference 对象
    * @param message 错误消息
     */
    public static void checkNotNull(Object reference, String message) {
        if (reference == null) {
            /**
            * 空pointer异常。
            * @param message 消息
            * @return 空pointer异常的结果
             */
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
            /**
            * illegal状态异常。
            * @param message 消息
            * @return illegal状态异常的结果
             */
            throw new IllegalStateException(message);
        }
    }

    /**
    * not空
    *
    * @param reference 引用
    * @param message 消息
    * @param errorCode 错误编码
    * @return not空的结果
     */
    public static <T> T notEmpty(T reference, String message, String errorCode) {
        if (reference == null || (reference instanceof String && ((String) reference).isEmpty())) {
            /**
            * illegal参数异常。
            * @param message 消息
            * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("[" + errorCode + "] " + message);
        }
        return reference;
    }
}
