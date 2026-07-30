package com.chua.starter.sync.support.spi;

import com.chua.starter.sync.support.pojo.ClientInfo;

/**
 * Sync 客户端信息定制器。
 * <p>
 * 用于在客户端注册前补充业务元数据，便于服务端按租户、节点角色等维度定向投递。
 * </p>
 *
 * @author CH
 * @since 2026/03/24
 */
public interface SyncClientInfoCustomizer {

    /**
     * 定制顺序，值越小越先执行。
     *
     * @return 顺序
     */
    default int getOrder() {
        return 0;
    }

    /**
     * 定制客户端信息。
     *
     * @param clientInfo 客户端信息
     */
    void customize(ClientInfo clientInfo);
}
