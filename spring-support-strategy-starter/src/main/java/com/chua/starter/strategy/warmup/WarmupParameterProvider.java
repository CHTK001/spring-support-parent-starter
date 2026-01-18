package com.chua.starter.strategy.warmup;

/**
 * 预热参数提供者接口
 * <p>
 * 用于在预热时提供方法调用所需的参数。
 * 实现此接口可以自定义预热时的参数值。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@FunctionalInterface
public interface WarmupParameterProvider {

    /**
     * 提供预热参数
     *
     * @param methodName     方法名
     * @param parameterTypes 参数类型数组
     * @return 参数值数组
     */
    Object[] provideParameters(String methodName, Class<?>[] parameterTypes);
}
