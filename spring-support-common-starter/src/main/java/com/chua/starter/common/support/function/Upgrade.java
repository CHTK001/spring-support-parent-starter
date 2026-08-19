package com.chua.starter.common.support.function;

/**
 * 配置升级回调。
 * @author CH
 */
@FunctionalInterface
public interface Upgrade<T> {

    void upgrade(T t);
}

