package com.chua.starter.sync.support.data;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据同步消息信封。
 *
 * @author CH
 * @since 2026/03/23
 */
@Data
@Builder
public class SyncDataEnvelope implements Serializable {

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 逻辑通道
     */
    private String channel;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 源客户端ID
     */
    private String sourceClientId;

    /**
     * 目标应用名
     */
    private String targetAppName;

    /**
     * 负载数据
     */
    private Object payload;

    /**
     * 扩展元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 发送时间戳
     */
    private long timestamp;
}
