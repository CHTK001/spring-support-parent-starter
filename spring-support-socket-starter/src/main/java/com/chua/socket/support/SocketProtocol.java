package com.chua.socket.support;

/**
 * Socket 协议类型枚举
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
public enum SocketProtocol {

    /**
     * Socket.IO 协议
     */
    SOCKETIO("socketio"),

    /**
     * RSocket 协议
     */
    RSOCKET("rsocket");

    private final String value;

    SocketProtocol(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 根据值获取协议类型
     *
     * @param value 值
     * @return 协议类型
     */
    public static SocketProtocol of(String value) {
        for (SocketProtocol protocol : values()) {
            if (protocol.value.equalsIgnoreCase(value)) {
                return protocol;
            }
        }
        return SOCKETIO;
    }
}
