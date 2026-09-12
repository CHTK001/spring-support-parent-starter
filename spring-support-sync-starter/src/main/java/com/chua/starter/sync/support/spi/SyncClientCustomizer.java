package com.chua.starter.sync.support.spi;

import com.chua.starter.sync.support.client.SyncClient;

/**
* 同步客户端 连接前自定义扩展点。
*
* @since 2026/03/25
* @author CH
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
    * 自定义 同步客户端。
    * <p>
    * 调用时机早于底层网络连接建立，可用于提前注册主题和处理器。
    * </p>
    *
    * @param syncClient 同步客户端
     */
    void customize(SyncClient syncClient);
}
