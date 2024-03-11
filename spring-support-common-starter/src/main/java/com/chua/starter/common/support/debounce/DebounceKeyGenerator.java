package com.chua.starter.common.support.debounce;

import java.lang.reflect.Method;

/**
 * 防抖生成器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/10
 */
public interface DebounceKeyGenerator {


    /**
     * 根据方法和参数数组生成一个唯一的键。
     *
     * @param method 提供方法对象，用于生成键的一部分。
     * @param args   方法调用时的参数数组，也用于生成键的一部分。
     * @param prefix 前缀
     * @return 返回一个字符串键，该键唯一地标识了给定方法和参数组合。
     */
    String getKey(String prefix, Method method, Object[] args);
}

