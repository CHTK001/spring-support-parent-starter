package com.chua.socket.support;

/**
* 套接字 协议类型枚举
*
* @版本 1.0.0
* @since 2024-12-04
* @author CH
 */
public enum SocketProtocol {

    /**
    * 套接字.IO 协议
    * @param "socketio" "套接字.IO"
    * @return Socket.IO的结果
    */
    SOCKETIO("socketio"),

    /**
    * r套接字 协议
    * @param "rsocket" "rsocket"
    * @return RSOCKET的结果
    */
    RSOCKET("rsocket"),

    /**
    * SSE (服务端-Sent 事件) 协议
    * @param "sse" "sse"
    * @return SSE的结果
    */
    SSE("sse");

    /** 值 */
    private final String value;

    /**
    * 套接字协议。
    * @param value 价值观
    */
    SocketProtocol(String value) {
        this.value = value;
    }

    /**
    * 获取价值观
    *
    * @return 获取价值观的结果
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
