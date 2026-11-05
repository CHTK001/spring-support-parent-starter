// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.server;

import com.chua.starter.monitor.support.entity.MsServerNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPI 代理协议的后端注入门禁。
 *
 * <p><b>为什么需要</b>：{@code aio-tcp-proxy} / {@code vertx-tcp-proxy} /
 * {@code vertx-http-proxy} 这类协议只有"单参构造"时会把目标解析器初始化成
 * <b>恒返回 null</b>（＝拒绝所有连接）。注册表过去对 SPI 协议一律只传
 * {@code ServerSetting}，于是节点能建、状态 RUNNING、端口也能监听，但每个连接
 * 立刻被关闭或挂死：实测 aio-tcp-proxy 超时、vertx-tcp-proxy
 * {@code Connection reset by peer}、vertx-http-proxy {@code 502 Bad Gateway}。
 * 这类"只有真实流量才暴露"的静默故障，必须在单元层钉住。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("SPI 代理协议的后端注入")
class ServerNodeRegistrySpiProxyBackendTest {

    private static MsServerNode node(String protocol) {
        MsServerNode node = new MsServerNode();
        node.setProtocol(protocol);
        return node;
    }

    private static InetSocketAddress invokeResolve(MsServerNode node, Map<String, Object> config) throws Exception {
        ServerNodeRegistry registry = new ServerNodeRegistry(null, null,
                new com.fasterxml.jackson.databind.ObjectMapper());
        Method m = ServerNodeRegistry.class.getDeclaredMethod("resolveBackendIfPresent",
                MsServerNode.class, Map.class);
        m.setAccessible(true);
        return (InetSocketAddress) m.invoke(registry, node, config);
    }

    private static Map<String, Object> backend(String host, Object port) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("backendHost", host);
        config.put("backendPort", port);
        return config;
    }

    @Test
    @DisplayName("配置了后端时 SPI 代理必须解析出该后端（否则实例会拒绝所有连接）")
    void resolvesConfiguredBackendForSpiProxy() throws Exception {
        InetSocketAddress address = invokeResolve(node("aio-tcp-proxy"), backend("127.0.0.1", 8112));
        assertEquals(8112, address.getPort());
        assertEquals("127.0.0.1", address.getHostString());
    }

    @Test
    @DisplayName("端口以字符串给出也要能解析（表单/JSON 常见形态）")
    void acceptsStringPort() throws Exception {
        InetSocketAddress address = invokeResolve(node("vertx-http-proxy"), backend("127.0.0.1", "18080"));
        assertEquals(18080, address.getPort());
    }

    @Test
    @DisplayName("代理类协议未配置后端 -> 明确报错，而不是静默只起端口")
    void proxyWithoutBackendFailsFast() {
        Exception e = assertThrows(Exception.class,
                () -> invokeResolve(node("vertx-tcp-proxy"), new LinkedHashMap<>()));
        String text = e.getMessage() == null ? "" : e.getMessage();
        assertTrue(text.contains("backendHost") || (e.getCause() != null
                        && String.valueOf(e.getCause().getMessage()).contains("backendHost")),
                "应提示缺少 backendHost/backendPort，实际: " + text);
    }

    @Test
    @DisplayName("非代理类 SPI 协议不要求后端，返回 空")
    void nonProxyProtocolHasNoBackend() throws Exception {
        assertNull(invokeResolve(node("ftp"), new LinkedHashMap<>()));
        assertNull(invokeResolve(node("udp"), new LinkedHashMap<>()));
        assertNull(invokeResolve(node("jdk-websocket"), new LinkedHashMap<>()));
    }

    @Test
    @DisplayName("socks5-proxy 从握手报文逐请求取目标，不要求固定后端")
    void socks5DoesNotRequireFixedBackend() throws Exception {
        assertNull(invokeResolve(node("socks5-proxy"), new LinkedHashMap<>()));
    }
}