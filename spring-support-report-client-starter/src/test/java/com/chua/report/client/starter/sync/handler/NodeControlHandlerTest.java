package com.chua.report.client.starter.sync.handler;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NodeControlHandlerTest {

    @Test
    void shouldGenerateWatchdogScriptUsingLocalhostPathAndPort() throws Exception {
        NodeControlHandler handler = new NodeControlHandler();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.setEnvironment(new MockEnvironment()
                    .withProperty("server.port", "18080")
                    .withProperty("server.servlet.context-path", "/cluster-a")
                    .withProperty("management.endpoints.web.base-path", "/actuator-a")
                    .withProperty("spring.application.name", "node-a"));
            context.refresh();
            handler.setApplicationContext(context);

            Path scriptPath = ReflectionTestUtils.invokeMethod(handler, "generateWatchdogScript", 45);

            assertThat(scriptPath).isNotNull();
            assertThat(scriptPath).exists();
            assertThat(Files.readString(scriptPath, StandardCharsets.UTF_8))
                    .contains("http://localhost:18080/cluster-a/actuator-a/health")
                    .contains("45");

            Files.deleteIfExists(scriptPath);
        }
    }

    @Test
    void shouldGenerateWindowsAndLinuxWatchdogTemplates() {
        NodeControlHandler handler = new NodeControlHandler();
        String healthUrl = "http://localhost:18081/cluster-b/manage/health";

        String windowsScript = ReflectionTestUtils.invokeMethod(
                handler, "generateWindowsScript", healthUrl, 90, "node-b");
        String linuxScript = ReflectionTestUtils.invokeMethod(
                handler, "generateLinuxScript", healthUrl, 90, "node-b");

        assertThat(windowsScript)
                .contains("curl -s -o nul -w")
                .contains("TIMEOUT=90")
                .contains(healthUrl)
                .contains("应用启动超时");
        assertThat(linuxScript)
                .contains("curl -s -o /dev/null -w")
                .contains("TIMEOUT=90")
                .contains(healthUrl)
                .contains("应用启动超时");
    }

    @Test
    void shouldRejectUnsupportedControlTopic() {
        NodeControlHandler handler = new NodeControlHandler();

        Map<String, Object> result = cast(handler.handle("monitor/control/unknown", "session", Map.of()));

        assertThat(result).containsEntry("code", 400);
        assertThat(result.get("message")).asString().contains("未知操作");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }
}
