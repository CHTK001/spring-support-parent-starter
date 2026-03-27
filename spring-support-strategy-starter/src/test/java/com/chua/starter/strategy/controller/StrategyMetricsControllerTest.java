package com.chua.starter.strategy.controller;

import com.chua.starter.strategy.actuator.StrategyMetricsEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrategyMetricsControllerTest {

    @Test
    void shouldReturnEmptyMetricsWhenActuatorEndpointIsUnavailable() {
        StrategyMetricsController controller = new StrategyMetricsController();

        Map<String, Object> data = cast(controller.getAllMetrics().getData());

        assertThat(cast(data.get("summary")))
                .containsEntry("totalRequests", 0)
                .containsEntry("totalSuccesses", 0)
                .containsEntry("totalFailures", 0)
                .containsEntry("successRate", 0D);
        assertThat(cast(data.get("cache"))).containsEntry("enabled", false);
        assertThat(cast(data.get("rateLimiter"))).isEmpty();
    }

    @Test
    void shouldTransformActuatorMetricsIntoConsoleView() {
        StrategyMetricsEndpoint endpoint = new StrategyMetricsEndpoint(null);
        endpoint.record(StrategyMetricsEndpoint.StrategyType.RATE_LIMITER, "payment-notify", true, 20);
        endpoint.record(StrategyMetricsEndpoint.StrategyType.RATE_LIMITER, "payment-notify", false, 40);
        endpoint.record(StrategyMetricsEndpoint.StrategyType.RETRY, "payment-retry", true, 10);

        StrategyMetricsController controller = new StrategyMetricsController();
        ReflectionTestUtils.setField(controller, "strategyMetricsEndpoint", endpoint);

        Map<String, Object> data = cast(controller.getAllMetrics().getData());
        Map<String, Object> summary = cast(data.get("summary"));
        Map<String, Object> rateLimiter = cast(data.get("rateLimiter"));
        Map<String, Object> paymentNotify = cast(rateLimiter.get("payment-notify"));
        Map<String, Object> retry = cast(data.get("retry"));

        assertThat(summary)
                .containsEntry("totalRequests", 3L)
                .containsEntry("totalSuccesses", 2L)
                .containsEntry("totalFailures", 1L);
        assertThat(paymentNotify)
                .containsEntry("totalRequests", 2L)
                .containsEntry("successCount", 1L)
                .containsEntry("failureCount", 1L)
                .containsEntry("avgDurationMs", 30L);
        assertThat(retry).containsKey("payment-retry");
    }

    @Test
    void shouldFallbackToEmptySummaryWhenEndpointThrows() {
        StrategyMetricsEndpoint endpoint = mock(StrategyMetricsEndpoint.class);
        when(endpoint.getMetricsByType("summary")).thenThrow(new IllegalStateException("boom"));

        StrategyMetricsController controller = new StrategyMetricsController();
        ReflectionTestUtils.setField(controller, "strategyMetricsEndpoint", endpoint);

        Map<String, Object> data = cast(controller.getMetricsByType("summary").getData());

        assertThat(data)
                .containsEntry("totalRequests", 0)
                .containsEntry("totalSuccesses", 0)
                .containsEntry("totalFailures", 0)
                .containsEntry("successRate", 0D);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }
}
