package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * HTTP 接口拉取采集 提供者（HTTP）
 * <p>
 * 目标主机暴露采集 HTTP 端点（如 /Agent/指标 返回 JSON），
 * 服务端定时 获取 拉取并回写。适合安装了轻量采集端但不开 SSH 的主机。
 * 端点约定：{@code http://{address}:{端口}/Agent/指标}。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpHostCollectProvider implements HostCollectProvider {

    /**
     * 采集端点路径（与 AgentMain 注册的路由一致）
    */
    private static final String METRICS_PATH = "/metrics";

    /**
     * agent 默认 HTTP 端口
    */
    private static final int DEFAULT_AGENT_PORT = 19876;

    /**
     * 主机 映射器
    */
    private final MsServerHostMapper hostMapper;

    @Override
    public HostCollectType type() {
        return HostCollectType.HTTP;
    }

    @Override
    public Mono<CollectOutcome> collect(HostCollectContext context) {
        return Mono.fromCallable(() -> doCollect(context))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 同步执行 HTTP 拉取
     *
     * @param context 上下文
     * @return 结果
     */
    private CollectOutcome doCollect(HostCollectContext context) {
        MsServerHost host = context == null ? null : context.getHost();
        if (host == null || host.getId() == null) {
            return CollectOutcome.fail("主机数据不完整");
        }
        String address = host.getServerHostAddress();
        if (address == null || address.isBlank()) {
            return CollectOutcome.fail("主机地址为空");
        }
        // serverHostPort 是 SSH/WinRM 的连接端口，采集要打到 agent 自己的 HTTP 端口
        int port = host.getServerHostAgentPort() == null || host.getServerHostAgentPort() <= 0
                ? DEFAULT_AGENT_PORT : host.getServerHostAgentPort();
        String url = "http://" + address.trim() + ":" + port + METRICS_PATH;
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code != 200) {
                return CollectOutcome.fail("HTTP 端点返回 " + code);
            }
            String body;
            /**
             * 尝试。
             * @param conn.getInputStream( conn.获取输入流(
             */
            try (var in = conn.getInputStream()) {
                body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            /**
             * applypayload。
             * @param host 主机
             * @param body 主体
             */
            applyPayload(host, body);
            hostMapper.updateById(host);
            return CollectOutcome.ok("HTTP 采集成功");
        } catch (Exception e) {
            log.warn("[collect] HTTP 采集失败 hostId={} url={}: {}", host.getId(), url, e.getMessage());
            return CollectOutcome.fail(e.getMessage());
        }
    }

    /**
     * 解析 JSON 负载并回写主机字段（容错：解析失败不阻断在线标记）
     *
     * @param host 主机
     * @param body 响应体
     */
    private void applyPayload(MsServerHost host, String body) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(body);
            if (root.has("cpuCores")) {
                host.setServerHostCpuCores(root.get("cpuCores").asInt());
            }
            if (root.has("memoryMb")) {
                host.setServerHostMemoryMb(root.get("memoryMb").asLong());
            }
            if (root.has("diskGb")) {
                host.setServerHostDiskGb(root.get("diskGb").asLong());
            }
            if (root.has("osVersion")) {
                host.setServerHostOsVersion(root.get("osVersion").asText());
            }
            if (root.has("osArch")) {
                host.setServerHostOsArch(root.get("osArch").asText());
            }
            if (root.has("publicIp")) {
                host.setServerHostPublicIp(root.get("publicIp").asText());
            }
            if (root.has("privateIp")) {
                host.setServerHostPrivateIp(root.get("privateIp").asText());
            }
        } catch (Exception e) {
            log.debug("[collect] HTTP 负载解析失败(忽略): {}", e.getMessage());
        }
    }
}
