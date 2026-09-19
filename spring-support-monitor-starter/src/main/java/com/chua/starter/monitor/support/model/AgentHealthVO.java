package com.chua.starter.monitor.support.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Agent {@code /health} 应答
 *
 * @param status    运行状态，正常为 up
 * @param version   Agent 版本号
 * @param buildTime 构建时间（ISO-8601 文本）
 * @param port      Agent 实际监听端口
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentHealthVO(String status, String version, String buildTime, Integer port) {

    /**
     * 判断是否为可用的健康应答。
     *
     * @return 携带状态字段时为真
     */
    public boolean available() {
        return status != null || version != null;
    }
}
