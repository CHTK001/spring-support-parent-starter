package com.chua.starter.ai.support.security;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GatewayRequestTimeoutFilter} 的用例。
 *
 * <p>锁住的是三件事：</p>
 * <ol>
 *   <li>首字节迟迟不来 → 写 504 并结束，不能让请求永久挂住；</li>
 *   <li>响应已提交（SSE 正在流式输出）→ <b>绝不</b>打断，否则一次正常的长对话会被网关掐断；</li>
 *   <li>开关关闭 / 路径未命中 → 完全不介入，过滤器不能成为新的故障点。</li>
 * </ol>
 *
 * <p>模块测试依赖里既没有 Mockito 也没有 spring-test，所以请求/响应用动态代理手写，
 * 只填过滤器真正用到的方法。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
class GatewayRequestTimeoutFilterTest {

    /**
     * 命中的网关路径（OpenAI 兼容聊天入口）
     */
    private static final String GATEWAY_PATH = "/v2/v1/chat/completions";

    /**
     * 未命中的路径
     */
    private static final String OTHER_PATH = "/actuator/health";

    /**
     * HTTP 200
     */
    private static final int SC_OK = 200;

    /**
     * HTTP 504
     */
    private static final int SC_GATEWAY_TIMEOUT = 504;

    @Test
    @DisplayName("首字节超时 → 写 504，且只写一次")
    void writes504WhenFirstByteNeverArrives() throws Exception {
        GatewayRequestTimeoutFilter filter = new GatewayRequestTimeoutFilter(settings("50", null));
        try {
            FakeResponse response = new FakeResponse();
            filter.doFilter(request(GATEWAY_PATH), response.proxy(), waitForReject(response));
            assertEquals(SC_GATEWAY_TIMEOUT, response.status);
            assertTrue(response.body().contains("Gateway Timeout"), "504 响应体: " + response.body());
        } finally {
            filter.destroy();
        }
    }

    @Test
    @DisplayName("响应已提交（流式输出中）→ 不打断，状态码保持原样")
    void keepsCommittedResponseUntouched() throws Exception {
        GatewayRequestTimeoutFilter filter = new GatewayRequestTimeoutFilter(settings("20", null));
        try {
            FakeResponse response = new FakeResponse();
            response.committed = true;
            filter.doFilter(request(GATEWAY_PATH), response.proxy(), sleepChain(200L));
            assertEquals(SC_OK, response.status);
            assertTrue(response.body().isEmpty(), "已提交的响应不应被追加内容: " + response.body());
        } finally {
            filter.destroy();
        }
    }

    @Test
    @DisplayName("security_request_timeout_enabled=false → 过滤器不介入")
    void disabledSwitchPassesThrough() throws Exception {
        GatewayRequestTimeoutFilter filter = new GatewayRequestTimeoutFilter(settings("20", "false"));
        try {
            FakeResponse response = new FakeResponse();
            filter.doFilter(request(GATEWAY_PATH), response.proxy(), sleepChain(150L));
            assertEquals(SC_OK, response.status);
            assertTrue(response.body().isEmpty(), response.body());
        } finally {
            filter.destroy();
        }
    }

    @Test
    @DisplayName("路径未命中网关前缀 → 过滤器不介入")
    void unmatchedPathPassesThrough() throws Exception {
        GatewayRequestTimeoutFilter filter = new GatewayRequestTimeoutFilter(settings("20", null));
        try {
            FakeResponse response = new FakeResponse();
            filter.doFilter(request(OTHER_PATH), response.proxy(), sleepChain(150L));
            assertEquals(SC_OK, response.status);
            assertTrue(response.body().isEmpty(), response.body());
        } finally {
            filter.destroy();
        }
    }

    @Test
    @DisplayName("超时值 ≤ 0 → 视为不限制")
    void nonPositiveTimeoutDisablesControl() throws Exception {
        GatewayRequestTimeoutFilter filter = new GatewayRequestTimeoutFilter(settings("0", null));
        try {
            FakeResponse response = new FakeResponse();
            filter.doFilter(request(GATEWAY_PATH), response.proxy(), sleepChain(150L));
            assertEquals(SC_OK, response.status);
            assertFalse(response.committed, "不应写出任何响应");
        } finally {
            filter.destroy();
        }
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /**
     * 构造一个只认 security 分组两项配置的读取器。
     *
     * @param millis    超时毫秒数
     * @param enabledIn 开关值；null 表示不配置（走默认 true）
     * @return 读取器
     */
    private static GlobalSettingReader settings(String millis, String enabledIn) {
        Map<String, String> values = new HashMap<>(4);
        values.put("security_request_timeout_millis", millis);
        if (enabledIn != null) {
            values.put("security_request_timeout_enabled", enabledIn);
        }
        return new GlobalSettingReader() {
            @Override
            public String getStr(String group, String field) {
                return values.get(field);
            }

            @Override
            public String getStrAs(String userId, String group, String field) {
                return values.get(field);
            }

            @Override
            public String getStrDirect(String group, String field) {
                return values.get(field);
            }

            @Override
            public boolean getBool(String group, String field, boolean defaultValue) {
                String raw = values.get(field);
                return raw == null ? defaultValue : Boolean.parseBoolean(raw);
            }

            @Override
            public int getInt(String group, String field, int defaultValue) {
                String raw = values.get(field);
                try {
                    return raw == null ? defaultValue : Integer.parseInt(raw.trim());
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }

            @Override
            public Integer getIntOrNull(String group, String field) {
                String raw = values.get(field);
                return raw == null ? null : Integer.valueOf(raw);
            }

            @Override
            public Long getLongOrNull(String group, String field) {
                return null;
            }

            @Override
            public BigDecimal getDecimalOrNull(String group, String field) {
                return null;
            }

            @Override
            public double getDouble(String group, String field, double defaultValue) {
                return defaultValue;
            }
        };
    }

    /**
     * 构造一个 HTTP 请求代理：只填摘要里那些方法。
     *
     * @param uri 请求路径
     * @return 请求代理
     */
    private static HttpServletRequest request(String uri) {
        Map<String, Object> attributes = new HashMap<>(4);
        return (HttpServletRequest) Proxy.newProxyInstance(
                GatewayRequestTimeoutFilterTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRequestURI":
                            return uri;
                        case "getMethod":
                            return "POST";
                        case "getAttribute":
                            return attributes.get((String) args[0]);
                        case "setAttribute":
                            attributes.put((String) args[0], args[1]);
                            return null;
                        case "removeAttribute":
                            attributes.remove((String) args[0]);
                            return null;
                        case "getDispatcherType":
                            return DispatcherType.REQUEST;
                        case "toString":
                            return "FakeRequest(" + uri + ")";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            return defaultValueOf(method.getReturnType());
                    }
                });
    }

    /**
     * 过滤器链：一直等到看门狗写出 504（最多 2s），用来验证「超时会被真正触发」。
     *
     * <p>用等待状态位而不是固定 sleep，避免慢机器上超时任务还没轮到就断言。</p>
     *
     * @param response 响应夹具
     * @return 过滤器链
     */
    private static FilterChain waitForReject(FakeResponse response) {
        return (req, res) -> {
            long deadline = System.currentTimeMillis() + 2_000L;
            while (response.status != SC_GATEWAY_TIMEOUT && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        };
    }

    /**
     * 过滤器链：模拟下游处理耗时。
     *
     * @param millis 耗时毫秒数
     * @return 过滤器链
     */
    private static FilterChain sleepChain(long millis) {
        return (req, res) -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    /**
     * 按返回类型给代理一个安全默认值（未被过滤器使用的方法）。
     *
     * @param type 返回类型
     * @return 默认值
     */
    private static Object defaultValueOf(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }

    /**
     * 响应夹具：记录状态码、提交标记与响应体。
     */
    private static final class FakeResponse {

        /**
         * 当前状态码
         */
        private int status = SC_OK;

        /**
         * 是否已提交（模拟首字节已发出）
         */
        private boolean committed;

        /**
         * 响应体
         */
        private final StringWriter body = new StringWriter();

        /**
         * 响应体写出器
         */
        private final PrintWriter writer = new PrintWriter(body, true);

        /**
         * 取响应体内容。
         *
         * @return 已写出的响应体
         */
        private String body() {
            writer.flush();
            return body.toString();
        }

        /**
         * 构造响应代理。
         *
         * @return 响应代理
         */
        private HttpServletResponse proxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    GatewayRequestTimeoutFilterTest.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "isCommitted":
                                return committed;
                            case "setStatus":
                                status = (Integer) args[0];
                                return null;
                            case "getStatus":
                                return status;
                            case "setContentType":
                            case "setCharacterEncoding":
                                return null;
                            case "getWriter":
                                return writer;
                            case "flushBuffer":
                                committed = true;
                                writer.flush();
                                return null;
                            case "toString":
                                return "FakeResponse(" + status + ")";
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            default:
                                return defaultValueOf(method.getReturnType());
                        }
                    });
        }
    }
}
