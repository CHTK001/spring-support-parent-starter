package com.chua.starter.sync.support;

/**
 * 连接事件监听器。
 *
 * @author CH
 * @since 4.0.0.41
 */
public interface SyncConnectionListener {

    void onConnect(SyncSession session);

    default void onDisconnect(SyncSession session) {
    }
}