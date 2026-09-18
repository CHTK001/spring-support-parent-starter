package com.chua.starter.sync.support;

import java.util.HashMap;
import java.util.Map;

/**
* 同步会话，封装单个客户端连接的会话标识与网络信息。
*
* @since 4.0.0.42
* @author CH
 */
public class SyncSession {

    /**
    * 会话标识。
    */
    private final String id;

    /**
    * 客户端 IP 地址。
    */
    private String clientIp;

    /**
    * 客户端端口号。
    */
    private int clientPort;

    /**
    * 构造同步会话。
    *
    * @param id 会话标识
    */
    public SyncSession(String id) {
        this.id = id;
    }

    /**
    * 获取会话标识。
    *
    * @return 会话标识
    */
    public String getId() {
        return id;
    }

    /**
    * 获取客户端 IP 地址。
    *
    * @return 客户端 IP 地址
    */
    public String getClientIp() {
        return clientIp;
    }

    /**
    * 设置客户端 IP 地址。
    *
    * @param clientIp 客户端 IP 地址
    */
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    /**
    * 获取客户端端口号。
    *
    * @return 客户端端口号
    */
    public int getClientPort() {
        return clientPort;
    }

    /**
    * 设置客户端端口号。
    *
    * @param clientPort 客户端端口号
    */
    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    /**
    * 转换为元数据映射，供 {@link com.chua.common.support.network.server.SyncServerListener} 使用。
    *
    * @return 元数据映射
    */
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("clientIp", clientIp);
        metadata.put("clientPort", clientPort);
        return metadata;
    }
}
