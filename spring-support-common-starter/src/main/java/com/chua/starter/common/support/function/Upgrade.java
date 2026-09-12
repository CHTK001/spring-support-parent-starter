package com.chua.starter.common.support.function;

/**
* 配置升级回调。
* @author CH
* @since 4.0.0
 */
@FunctionalInterface
public interface Upgrade<T> {

    /**
    * upgrade。
    * @param t t
     */
    void upgrade(T t);
}

