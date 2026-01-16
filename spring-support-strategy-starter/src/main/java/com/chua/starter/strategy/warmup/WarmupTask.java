package com.chua.starter.strategy.warmup;

import java.lang.reflect.Method;

/**
 * 预热任务记录
 * <p>
 * 使用record类型定义不可变的预热任务信息载体。
 * </p>
 *
 * @param name              任务名称
 * @param bean              目标Bean
 * @param method            目标方法
 * @param order             执行优先级
 * @param async             是否异步执行
 * @param timeout           超时时间（毫秒）
 * @param failOnError       失败是否阻止启动
 * @param iterations        执行次数
 * @param parameterProvider 参数提供者
 * @param description       描述信息
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public record WarmupTask(
        String name,
        Object bean,
        Method method,
        int order,
        boolean async,
        long timeout,
        boolean failOnError,
        int iterations,
        WarmupParameterProvider parameterProvider,
        String description
) implements Comparable<WarmupTask> {

    /**
     * 按优先级排序
     *
     * @param other 另一个预热任务
     * @return 比较结果
     */
    @Override
    public int compareTo(WarmupTask other) {
        return Integer.compare(this.order, other.order);
    }
}
