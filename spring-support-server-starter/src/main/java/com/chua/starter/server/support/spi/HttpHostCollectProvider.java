package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * HTTP 接口拉取采集 Provider（HTTP）
 * <p>
 * 目标主机暴露采集 HTTP 端点（如 /agent/metrics 返回 JSON），
 * 服务端定时 GET 拉取并回写。适合安装了轻量采集端但不开 SSH 的主机。
 * 端点约定：{@code http://{address}:{port}/agent/metrics}。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpHostCollectProvider implements HostCollectProvider {

    /** 采集端点路径 */
    private static final String METRICS_PATH = "/agent/metrics";

    /** 主机 Mapper */
    private final MsServerHostMapper hostMapper;

    @Override
    public HostCollectType type() {
        return HostCollectType.HTTP;
    }

    @Override
    public CollectOutcome collect(HostCollectContext context) {
        MsServerHost host = context == null ? null : context.getHost();
        if (host == null || host.getId() == null) {
            return CollectOutcome.fail("主机数据不完整");
        }
        String address = host.getServerHostAddress();
        if (address == null || address.isBlank()) {
            return CollectOutcome.fail("主机地址为空");
        }
        int port = host.getServerHostPort() == null || host.getServerHostPort() <= 0 ? 8080 : host.getServerHostPort();
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
            try (var in = conn.getInputStream()) {
                body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
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
