package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.agent.AgentMessage;
import com.chua.common.support.ai.agent.AgentOptions;
import com.chua.common.support.ai.agent.AgentResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentTest {

    @Test
    void shouldManageSessionHistoryAndSnapshots() {
        StubAgentChatClient chatClient = new StubAgentChatClient();
        AgentOptions options = AgentOptions.builder()
                .provider("mock")
                .model("model-a")
                .snapshotEnabled(true)
                .executionRecordEnabled(true)
                .build();
        DefaultAgent agent = new DefaultAgent(chatClient, options, "default");

        AgentResponse first = agent.execute(AgentRequest.builder()
                .sessionId("editor-1")
                .input("task-1")
                .build());

        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getModel()).isEqualTo("model-a");
        assertThat(agent.session("editor-1").getHistoryMessages()).hasSize(2);
        assertThat(agent.snapshots("editor-1")).hasSize(1);

        agent.session("editor-1").useModel("model-b");
        AgentResponse second = agent.execute(AgentRequest.builder()
                .sessionId("editor-1")
                .input("task-2")
                .messages(List.of(AgentMessage.builder().role("user").text("extra").build()))
                .build());

        assertThat(second.getModel()).isEqualTo("model-b");
        assertThat(agent.session("editor-1").getHistoryMessages()).hasSize(5);
        assertThat(agent.snapshots("editor-1")).hasSize(2);
    }

    @Test
    void shouldRejectModelOutsideCurrentProviderCatalog() {
        StubAgentChatClient chatClient = new StubAgentChatClient();
        DefaultAgent agent = new DefaultAgent(chatClient, AgentOptions.builder()
                .provider("mock")
                .model("model-a")
                .build(), "default");

        AgentResponse response = agent.execute(AgentRequest.builder()
                .sessionId("editor-2")
                .input("task")
                .model("model-x")
                .build());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("模型不属于当前 provider");
    }

    @Test
    void shouldPublishTaskEventsAndPersistTelemetry() {
        RecordingAgentRequestStorage.reset();
        StubAgentChatClient chatClient = new StubAgentChatClient();
        RecordingAgentCallback callback = new RecordingAgentCallback();
        DefaultAgent agent = new DefaultAgent(chatClient, AgentOptions.builder()
                .provider("mock")
                .model("model-a")
                .executionRecordEnabled(true)
                .callbacks(List.of(callback))
                .build(), "default");

        AgentResponse response = agent.execute(AgentRequest.builder()
                .sessionId("editor-3")
                .input("task")
                .reasoningEffort("high")
                .userAgent("editor-test")
                .taskMode(AgentTaskMode.TASK)
                .build());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getUsage()).isNotNull();
        assertThat(callback.getEvents()).extracting(event -> event.getType().name())
                .contains("REQUEST_RECEIVED", "MODEL_CALL_STARTED", "MODEL_CALL_COMPLETED", "USAGE_RECORDED", "REQUEST_COMPLETED");
        assertThat(agent.records("editor-3")).singleElement()
                .satisfies(record -> assertThat(record.getSteps()).extracting(step -> step.getEventType().name())
                        .contains("MODEL_CALL_STARTED", "USAGE_RECORDED", "REQUEST_COMPLETED"));
        assertThat(RecordingAgentRequestStorage.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getModel()).isEqualTo("model-a");
                    assertThat(item.getReasoningEffort()).isEqualTo("high");
                    assertThat(item.getEndpoint()).isEqualTo("https://mock.example.com/v1/chat");
                    assertThat(item.getUserAgent()).isEqualTo("editor-test");
                    assertThat(item.getTokens().getInputTokens()).isEqualTo(12);
                    assertThat(item.getTokens().getOutputTokens()).isEqualTo(8);
                    assertThat(item.getCosts().getInputCost()).isEqualByComparingTo("0.12");
                    assertThat(item.getCosts().getOutputCost()).isEqualByComparingTo("0.08");
                    assertThat(item.getCosts().getInputUnitPrice()).isNotNull();
                    assertThat(item.getFirstTokenLatencyMillis()).isEqualTo(15L);
                });
    }

    @Test
    void shouldHideInternalTaskEventsInDefaultMode() {
        RecordingAgentRequestStorage.reset();
        StubAgentChatClient chatClient = new StubAgentChatClient();
        RecordingAgentCallback callback = new RecordingAgentCallback();
        DefaultAgent agent = new DefaultAgent(chatClient, AgentOptions.builder()
                .provider("mock")
                .model("model-a")
                .executionRecordEnabled(true)
                .callbacks(List.of(callback))
                .build(), "default");

        agent.execute(AgentRequest.builder()
                .sessionId("editor-4")
                .input("task")
                .taskMode(AgentTaskMode.DEFAULT)
                .build());

        assertThat(callback.getEvents()).extracting(event -> event.getType().name())
                .contains("REQUEST_RECEIVED", "REQUEST_COMPLETED")
                .doesNotContain("MODEL_CALL_STARTED", "USAGE_RECORDED");
        assertThat(agent.records("editor-4")).singleElement()
                .satisfies(record -> assertThat(record.getSteps()).extracting(step -> step.getEventType().name())
                        .contains("REQUEST_RECEIVED", "REQUEST_COMPLETED")
                        .doesNotContain("MODEL_CALL_STARTED", "USAGE_RECORDED"));
    }
}
