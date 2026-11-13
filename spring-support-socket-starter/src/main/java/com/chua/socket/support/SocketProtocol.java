package com.chua.socket.support;

/**
 * Socket 协议类型枚举
 *
 * @since 2024-12-04
 * @author CH
 */
public enum SocketProtocol {

    /**
     * Socket.IO 协议
     * @param "socketio" "Socket.IO"
     * @return Socket.IO的结果
     */
    SOCKETIO("socketio"),

    /**
     * rSocket 协议
     * @param "rsocket" "rsocket"
     * @return RSOCKET的结果
     */
    RSOCKET("rsocket"),

    /**
     * SSE (服务端-Sent 事件) 协议
     * @param "sse" "sse"
     * @return SSE的结果
     */
    SSE("sse"),

    /**
     * 独立端口 SSE：事件流由本进程单独监听（不占主应用 Tomcat）
     * @param "sse-server" "sse-server"
     * @return 独立 SSE 的结果
     */
    SSE_SERVER("sse-server");

    /**
     * 值
    */
    private final String value;

    /**
     * Socket协议。
     * @param value 值
     * @return 返回值
    */
    SocketProtocol(String value) {
        this.value = value;
    }

    /**
     * 获取值
     *
     * @return 获取值的结果
     */
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
