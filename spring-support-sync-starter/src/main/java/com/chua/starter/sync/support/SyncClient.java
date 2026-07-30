package com.chua.starter.sync.support;

/**
 * 同步客户端接口，与 {@link SyncServer} 成对使用。
 *
 * <p>由 {@link SyncProtocol} 创建，协议实现必须同时提供客户端和服务端。</p>
 *
 * @author CH
 * @since 4.0.0.41
 */
public interface SyncClient extends AutoCloseable {

    void connect();

    void disconnect();

    boolean isConnected();

    String getClientId();

    void send(String topic, Object message);

    default void publish(String topic, Object message) {
        send(topic, message);
    }

    void subscribe(String[] topics);

    ListenerManager getListenerManager();

    /**
     * 创建同步客户端。
     *
     * @param protocol 协议类型
     * @param setting  客户端配置
     * @return 同步客户端实例
     */
    static SyncClient create(String protocol, Object setting) {
        return create(protocol, setting, protocol);
    }

    /**
     * 创建同步客户端。
     *
     * @param protocol  协议类型
     * @param setting   客户端配置
     * @param protocolName 协议名称（用于 SPI 查找）
     * @return 同步客户端实例
     */
    static SyncClient create(String protocol, Object setting, String protocolName) {
        com.chua.common.support.spi.ServiceProvider<SyncProtocol> provider =
                com.chua.common.support.spi.ServiceProvider.of(SyncProtocol.class);
        SyncProtocol syncProtocol = provider.getNewExtension(protocolName, protocolName);
        if (syncProtocol == null) {
            throw new IllegalArgumentException("Unsupported sync protocol: " + protocol);
        }
        return syncProtocol.createClient(setting);
    }

    @Override
    void close();
}