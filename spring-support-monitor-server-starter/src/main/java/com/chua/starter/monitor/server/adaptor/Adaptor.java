package com.chua.starter.monitor.server.adaptor;

/**
 * 适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public interface Adaptor<T> {


    /**
     * do适配器
     *
     * @param t t
     */
    void doAdaptor(T t);


    /**
     * 获取类型
     *
     * @return {@link Class}<{@link T}>
     */
    Class<T> getType();
}