package com.chua.starter.unified.server.support.store;

/**
 * 存储解释器
 *
 * @author CH
 */
public interface StoreResolver {
    /**
     * Resovle
     *
     * @param message         消息
     * @param applicationName 应用程序名称
     */
    void resolve(String message, String applicationName);
}
