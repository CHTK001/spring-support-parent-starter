package com.chua.common.support.objects.provider;

/**
 * 对象提供者接口，定义延迟获取对象的契约
 *
 * @param <T> 对象类型
 * @author CH
 */
public interface ObjectProvider<T> {
    /**
     * 如果 Bean 可用则返回，否则返回 null
     *
     * @return Bean 实例，不可用时返回 null
     */
    T getIfAvailable();

    /**
     * 获取 Bean 实例，如果不可用则抛出异常
     *
     * @return Bean 实例
     */
    T getObject();
}
