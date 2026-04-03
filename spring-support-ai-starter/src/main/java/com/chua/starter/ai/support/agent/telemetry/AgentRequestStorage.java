package com.chua.starter.ai.support.agent.telemetry;

/**
 * Agent 请求遥测存储 SPI。
 *
 * @author CH
 * @since 2026/04/03
 */
public interface AgentRequestStorage {

    /**
     * 存储一次请求遥测。
     *
     * @param telemetry 请求遥测
     */
    void store(AgentRequestTelemetry telemetry);
}
