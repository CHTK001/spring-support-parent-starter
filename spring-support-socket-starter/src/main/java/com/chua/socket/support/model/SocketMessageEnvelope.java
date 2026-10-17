package com.chua.socket.support.model;

import java.io.Serializable;

/**
 * Socket 消息传输包装体
 *
 * @param data      传输数据
 * @param encrypted 是否加密
 * @param timestamp 协议附加字段
 * @param uuid      协议附加字段
 * @param dataId    数据 标识，便于前端快速过滤
 * @since 2026-04-08
 * @author CH
 */
public record SocketMessageEnvelope(
        String data,
        boolean encrypted,
        String timestamp,
        String uuid,
        String dataId) implements Serializable {

    /**
     * 串行版本UID
     */
    private static final long serialVersionUID = 1L;
}
