package com.chua.starter.ai.support.agent;

import com.chua.starter.ai.support.agent.telemetry.AgentRequestStorage;
import com.chua.starter.ai.support.agent.telemetry.AgentRequestTelemetry;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用请求遥测存储器。
 */
final class RecordingAgentRequestStorage implements AgentRequestStorage {

    private static final List<AgentRequestTelemetry> ITEMS = new ArrayList<>();

    @Override
    public void store(AgentRequestTelemetry telemetry) {
        ITEMS.add(telemetry);
    }

    static void reset() {
        ITEMS.clear();
    }

    static List<AgentRequestTelemetry> getItems() {
        return List.copyOf(ITEMS);
    }
}
