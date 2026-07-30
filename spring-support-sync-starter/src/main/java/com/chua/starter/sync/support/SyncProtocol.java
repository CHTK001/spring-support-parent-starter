package com.chua.starter.sync.support;

/**
 * 同步协议工厂，创建成对的 {@link SyncServer} 和 {@link SyncClient}。
 *
 * <p>同步协议一定是成对的，每个协议实现必须同时提供客户端和服务端。</p>
 *
 * @author CH
 * @since 4.0.0.41
 */
public interface SyncProtocol {

    /**
     * 获取协议类型标识。
     *
     * @return 协议类型，如 websocket、rsocket、socketio
     */
    String getProtocol();

    /**
     * 创建同步服务端。
     *
     * @param setting 服务端配置
     * @return 同步服务端实例
     */
    SyncServer createServer(Object setting);

    /**
     * 创建同步客户端。
     *
     * @param setting 客户端配置
     * @return 同步客户端实例
     */
    SyncClient createClient(Object setting);
}