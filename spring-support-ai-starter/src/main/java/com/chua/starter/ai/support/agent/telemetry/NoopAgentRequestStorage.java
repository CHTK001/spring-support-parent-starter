package com.chua.starter.ai.support.agent.telemetry;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.core.annotation.SpiDefault;

/**
 * 默认空实现，请求遥测存储 SPI 占位实现。
 *
 * @author CH
 * @since 2026/04/03
 */
@Spi("noop")
@SpiDefault
public class NoopAgentRequestStorage implements AgentRequestStorage {

    @Override
    public void store(AgentRequestTelemetry telemetry) {
    }
}
