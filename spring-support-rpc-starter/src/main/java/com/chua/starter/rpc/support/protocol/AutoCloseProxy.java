package com.chua.starter.rpc.support.protocol;

/**
 * 可被关闭协议
 *
 * @author CH
 */
public interface AutoCloseProxy<T> extends AutoCloseable {
    /**
     * 实体
     *
     * @return 实体
     */
    T doRefer();
}
