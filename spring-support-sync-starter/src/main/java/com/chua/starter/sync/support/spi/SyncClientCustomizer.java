package com.chua.starter.sync.support.spi;

import com.chua.starter.sync.support.client.SyncClient;

/**
 * SyncClient 连接前自定义扩展点。
 *
 * @author CH
 * @since 2026/03/25
 */
public interface SyncClientCustomizer {

    /**
     * 执行顺序，值越小越先执行。
     *
     * @return 顺序
     */
    default int getOrder() {
        return 0;
    }

    /**
     * 自定义 SyncClient。
     * <p>
     * 调用时机早于底层网络连接建立，可用于提前注册主题和处理器。
     * </p>
     *
     * @param syncClient SyncClient
     */
    void customize(SyncClient syncClient);
}
