package com.chua.socket.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Socket 消息传输包装体
 *
 * @since 2026-04-08
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocketMessageEnvelope implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
    * 传输数据
    */
    private String data;

    /**
     * 是否加密
     */
    private boolean encrypted;

    /**
     * 协议附加字段
     */
    private String timestamp;

    /**
     * 协议附加字段
     */
    private String uuid;

    /**
     * 数据 标识，便于前端快速过滤
     */
    private String dataId;
}
