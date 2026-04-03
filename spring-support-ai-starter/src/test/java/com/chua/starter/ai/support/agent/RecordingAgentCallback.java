package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.callback.AgentCallback;
import com.chua.common.support.ai.callback.AgentEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用回调记录器。
 */
final class RecordingAgentCallback implements AgentCallback {

    private final List<AgentEvent> events = new ArrayList<>();

    @Override
    public void onEvent(AgentEvent event) {
        events.add(event);
    }

    List<AgentEvent> getEvents() {
        return List.copyOf(events);
    }
}
