// [P3C A20 豁免] [P3C MJ-方法注释 豁免] 端到端测试的超时/断言参数。
package com.chua.starter.sse.support.server;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.socket.support.properties.SocketProperties;
import com.chua.socket.support.session.SocketSessionTemplate;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 独立 SSE 服务端到端测试：POST 建连 → 收到 connected → 业务广播 → 收到 message。
 *
 * <p>只起独立的 NioHttpServer，不启动 Spring 主应用，验证“独立端口不占主干”这条链路。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
class SseStandaloneSessionTemplateTest {

    @Test
    void postConnectThenBroadcastDeliversMessage() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(port);
        setting.setMaxConcurrency(0);
        setting.setShutdownQuietPeriod(0);

        SseStandaloneSessionTemplate template = new SseStandaloneSessionTemplate(setting);
        template.start();
        try {
            List<String> lines = new ArrayList<>();
            CompletableFuture<Void> reading = CompletableFuture.runAsync(() -> {
                try {
                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .build();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + SseStandaloneSessionTemplate.CONNECT_PATH))
                            .header("Accept", "text/event-stream")
                            .header("x-oauth-token", "test-token")
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();
                    HttpResponse<InputStream> response = client.send(
                            request, HttpResponse.BodyHandlers.ofInputStream());
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            synchronized (lines) {
                                lines.add(line);
                            }
                            synchronized (lines) {
                                if (lines.contains("event: message")
                                        && lines.stream().anyMatch(it -> it.contains("\"module\":\"monitor\""))) {
                                    return;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });

            waitFor(() -> {
                synchronized (lines) {
                    return lines.contains("event: connected");
                }
            }, 10000, "等待 connected 事件超时");
            assertTrue(template.getOnlineCount() > 0, "连接应已登记");

            template.broadcast("message",
                    "{\"module\":\"monitor\",\"event\":\"alarm\",\"data\":{\"id\":7,\"level\":\"P1\"}}");

            waitFor(() -> {
                synchronized (lines) {
                    return lines.contains("event: message")
                            && lines.contains("data: {\"module\":\"monitor\",\"event\":\"alarm\",\"data\":{\"id\":7,\"level\":\"P1\"}}");
                }
            }, 10000, "等待 message 事件超时");

            reading.complete(null);
        } finally {
            template.stop();
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier condition,
                                long timeoutMs,
                                String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError(message);
    }
}
