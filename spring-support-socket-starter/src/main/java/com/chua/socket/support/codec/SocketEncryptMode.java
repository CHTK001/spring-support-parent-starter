package com.chua.socket.support.codec;

/**
 * Socket 消息加密模式
 *
 * @author CH
 * @since 2026-04-08
 */
public enum SocketEncryptMode {

    /**
     * 自动模式，默认加密，允许客户端显式降级为明文
     */
    AUTO,

    /**
     * 强制加密
     */
    ENCRYPTED,

    /**
     * 强制明文
     */
    PLAIN
}
