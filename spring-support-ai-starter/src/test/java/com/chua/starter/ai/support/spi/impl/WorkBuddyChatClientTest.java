// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatSyncResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkBuddyChatClient} 的集成测试。
 *
 * <p>用 JDK 内置 {@link HttpServer} 起 mock 上游，模拟 WorkBuddy 的
 * {@code POST /v2/chat/completions} SSE 流（末帧带 {@code usage}）与
 * {@code GET /v3/config} 模型目录，覆盖：</p>
 * <ul>
 *   <li>请求体必须 {@code stream:true} 且带 {@code stream_options.include_usage}；</li>
 *   <li>认证头为 {@code Authorization: Bearer <access_token>}；</li>
 *   <li>同步语义把分片聚合为完整文本，并解析用量；</li>
 *   <li>流式回调携带 reasoning / 工具调用增量；</li>
 *   <li>非 2xx 抛出带提示的错误；</li>
 *   <li>模型目录解析上下文窗口与输出上限。</li>
 * </ul>
 *
 * @since 4.0.0.51
 * @author CH
 */
class WorkBuddyChatClientTest {

    private HttpServer server;

    /**
     * mock 上游地址
     */
    private String origin;

    /**
     * 最近一次对话请求体
     */
    private final AtomicReference<String> lastBody = new AtomicReference<>("");

    /**
     * 最近一次对话请求的 Authorization 头
     */
    private final AtomicReference<String> lastAuth = new AtomicReference<>("");

    /**
     * 对话响应状态码
     */
    private volatile int chatStatus = 200;

    @BeforeEach
    void startMockServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", this::handle);
        server.start();
        lastBody.set("");
        lastAuth.set("");
        chatStatus = 200;
    }

    @AfterEach
    void stopMockServer() {
        server.stop(0);
    }

    /**
     * mock 路由：对话 SSE 与模型目录。
     *
     * @param exchange 请求
     * @throws IOException 写出失败
     */
    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        if ("POST".equals(method) && path.endsWith("/v2/chat/completions")) {
            lastBody.set(body);
            lastAuth.set(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            if (chatStatus != 200) {
                exchange.sendResponseHeaders(chatStatus, -1);
                exchange.close();
                return;
            }
            StringBuilder sse = new StringBuilder();
            sse.append("data: {\"id\":\"req-1\",\"model\":\"deepseek-v4.1-flash\",")
                    .append("\"choices\":[{\"delta\":{\"reasoning_content\":\"思考\"}}]}\n\n");
            sse.append("data: {\"id\":\"req-1\",\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n");
            sse.append("data: {\"id\":\"req-1\",\"choices\":[{\"delta\":{\"content\":\"，世界\"}}]}\n\n");
            sse.append("data: {\"id\":\"req-1\",")
                    .append("\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\",")
                    .append("\"function\":{\"name\":\"get_weather\",\"arguments\":\"{}\"}}]}}]}\n\n");
            sse.append("data: {\"id\":\"req-1\",\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],")
                    .append("\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":7,\"total_tokens\":19,")
                    .append("\"prompt_tokens_details\":{\"cached_tokens\":4}}}\n\n");
            sse.append("data: [DONE]\n\n");
            byte[] bytes = sse.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            return;
        }
        if ("GET".equals(method) && path.endsWith("/v3/config")) {
            String response = "{\"code\":0,\"data\":{\"models\":["
                    + "{\"id\":\"deepseek-v4.1-flash\",\"maxInputTokens\":1000000,"
                    + "\"maxOutputTokens\":384000,\"descriptionZh\":\"深度求索\"}]}}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            return;
        }
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
    }

    /**
     * 构造指向 mock 的客户端。
     *
     * @return 客户端
     */
    private WorkBuddyChatClient client() {
        return new WorkBuddyChatClient(ChatClientSetting.builder()
                .provider("workbuddy")
                .baseUrl(origin)
                .appKey("at-mock")
                .model("deepseek-v4.1-flash")
                .build());
    }

    @Test
    @DisplayName("chatSync：上游恒流式，分片聚合为完整文本并解析用量")
    void chatSyncAggregatesStream() {
        ChatSyncResponse response = client().chatSyncWithResponse("你好");

        assertNotNull(response);
        assertEquals("你好，世界", response.getText());
        assertNotNull(response.getUsage(), "末帧 usage 应被解析");
        assertEquals(12, response.getUsage().getInputTokens());
        assertEquals(7, response.getUsage().getOutputTokens());
        assertEquals(19, response.getUsage().getTotalTokens());
        assertEquals(4, response.getUsage().getCacheTokens(), "缓存命中应取 prompt_tokens_details.cached_tokens");
        assertEquals("stop", response.getUsage().getFinishReason());
        assertEquals("workbuddy", response.getUsage().getProvider());
        assertEquals("deepseek-v4.1-flash", response.getUsage().getModel());
    }

    @Test
    @DisplayName("chatSync：请求体强制 stream=true 并携带 include_usage")
    void requestBodyForcesStream() {
        client().chatSync("hi");
        String body = lastBody.get();
        assertTrue(body.contains("\"stream\":true"), "上游只接受流式，必须显式 stream=true: " + body);
        assertTrue(body.contains("\"include_usage\":true"), "必须要求末帧带 usage: " + body);
        assertTrue(body.contains("\"model\":\"deepseek-v4.1-flash\""), "应带上模型: " + body);
    }

    @Test
    @DisplayName("chatSync：以 Bearer 发送 access_token")
    void sendsBearerToken() {
        client().chatSync("hi");
        assertEquals("Bearer at-mock", lastAuth.get());
    }

    @Test
    @DisplayName("chat：流式回调携带正文、思考与工具调用增量，并以 STOP 收尾")
    void streamCallbacksCarryReasoningAndTools() {
        List<String> contents = new ArrayList<>();
        List<String> reasonings = new ArrayList<>();
        List<String> toolNames = new ArrayList<>();
        String[] full = new String[1];

        client().chat("你好", response -> {
            switch (response.getState()) {
                case STREAMING -> {
                    if (response.getContent() != null) {
                        contents.add(response.getContent());
                    }
                    if (response.getReasoningContent() != null) {
                        reasonings.add(response.getReasoningContent());
                    }
                    if (response.getToolCalls() != null) {
                        response.getToolCalls().forEach(call -> toolNames.add(call.getName()));
                    }
                }
                case STOP -> full[0] = response.getFullContent();
                default -> {
                }
            }
        });

        assertEquals(List.of("你好", "，世界"), contents, "正文分片应逐帧回调");
        assertEquals(List.of("思考"), reasonings, "思考内容应单独回调");
        assertEquals(List.of("get_weather"), toolNames, "工具调用增量应被翻译");
        assertEquals("你好，世界", full[0], "STOP 帧应带完整文本");
    }

    @Test
    @DisplayName("chatSync：上游非 2xx 抛出带提示的错误")
    void non2xxThrows() {
        chatStatus = 401;
        // chatSync 经默认 onError 包装为 RuntimeException（保留上游原因）
        RuntimeException error = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class, () -> client().chatSync("hi"));
        assertTrue(String.valueOf(error.getMessage()).contains("401"), "错误信息应含状态码: " + error.getMessage());
        assertTrue(String.valueOf(error.getMessage()).contains("access_token"),
                "401 应提示重新登录: " + error.getMessage());
    }

    @Test
    @DisplayName("models：/v3/config 解析上下文窗口与输出上限")
    void modelsFromConfig() {
        List<com.chua.common.support.ai.chat.ModelDefinition> models = client().models();
        assertEquals(1, models.size());
        assertEquals("deepseek-v4.1-flash", models.get(0).getId());
        assertEquals(1000000L, models.get(0).getContextWindowTokens());
        assertEquals(384000L, models.get(0).getMaxOutputTokens());
        assertEquals("workbuddy", models.get(0).getProvider());
        assertEquals("深度求索", models.get(0).getDescription());
    }

    @Test
    @DisplayName("SPI 元数据：provider/model 可读，getSetting 返回原配置")
    void metadata() {
        WorkBuddyChatClient client = client();
        assertEquals("workbuddy", client.getSetting().getProvider());
        assertEquals("deepseek-v4.1-flash", client.getModel());
        assertEquals("at-mock", client.getApiKey());
        assertEquals(origin, client.getBaseUrl());
        assertEquals(com.chua.common.support.ai.chat.ChatClientType.OPENAI_COMPATIBLE,
                client.type(), "WorkBuddy 走 OpenAI 兼容 Chat Completions 协议");
    }
}
