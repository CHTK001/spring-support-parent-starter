// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.spi;

import com.chua.starter.common.support.http.ChannelHttp;
import com.chua.starter.common.support.http.ChannelHttpException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 账号模块对共享 {@link ChannelHttp} 的契约测试。
 *
 * <p>{@code ChannelHttp} 已从 AI 模块下沉到 common：账号模块与 AI 模块都要发同样的
 * 渠道上游请求，放在任一业务模块都会造成反向依赖。本测试从账号模块一侧锁死两点：</p>
 * <ol>
 *   <li>账号模块能直接编译、链接并使用 common 的 {@link ChannelHttp}（下沉后的可见性）；</li>
 *   <li>非 2xx 抛的是 {@link ChannelHttpException}，并带上游状态码与错误码，
 *       账号侧据此翻成自己的 {@code ApiException}，不需要匹配中文措辞。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.51
 */
class ChannelHttpSharedTest {

    /**
     * 非 2xx 必须抛 {@link ChannelHttpException}，且带状态码与错误码。
     *
     * @throws IOException 端口绑定失败
     */
    @Test
    void upstreamFailureCarriesStatusAndCode() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bad", exchange -> reply(exchange, 502, "upstream down"));
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/bad";
            ChannelHttpException error = assertThrows(ChannelHttpException.class,
                    () -> ChannelHttp.getJson(url, null));

            assertEquals(ChannelHttp.CODE_UPSTREAM_ERROR, error.getCode());
            assertEquals(502, error.getHttpStatus());
            assertTrue(error.getMessage().contains("渠道上游返回非 2xx"), error.getMessage());
            assertTrue(error.getMessage().contains("upstream down"), error.getMessage());
        } finally {
            server.stop(0);
        }
    }

    /**
     * 2xx 原样返回响应体。
     *
     * @throws IOException 端口绑定失败
     */
    @Test
    void okBodyPassesThrough() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> reply(exchange, 200, "{\"ok\":1}"));
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/ok";
            assertEquals("{\"ok\":1}", ChannelHttp.getJson(url, null));
        } finally {
            server.stop(0);
        }
    }

    /**
     * 写一个固定响应。
     *
     * @param exchange 请求
     * @param status   状态码
     * @param body     响应体
     * @throws IOException 写响应失败
     */
    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
