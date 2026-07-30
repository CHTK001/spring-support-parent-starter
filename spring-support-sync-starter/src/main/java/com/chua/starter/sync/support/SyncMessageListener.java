package com.chua.starter.sync.support;

/**
 * 消息监听器接口，服务端收到消息时回调。
 *
 * @author CH
 * @since 4.0.0.41
 */
@FunctionalInterface
public interface SyncMessageListener {

    void onMessage(SyncSession session, SyncMessage message);
}