package com.chua.starter.server.support.spi;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

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
@Spi(value = "http", order = 100)
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
    @Autowired
    private MsServerHostMapper hostMapper;

    @Override
    public HostCollectType type() {
        return HostCollectType.HTTP;
    }

    @Override
    public Mono<CollectOutcome> collect(HostCollectContext context) {
        return Mono.fromCallable(() -> doCollect(context))
                .subscribeOn(VirtualThreadSchedulers.virtual());
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
            try (var in = conn.getInputStream()) {
                body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            LambdaUpdateWrapper<MsServerHost> write = new LambdaUpdateWrapper<>();
            applyPayload(host, body, write);
            hostMapper.update(null, write.eq(MsServerHost::getId, host.getId()));
            return CollectOutcome.ok("HTTP 采集成功");
        } catch (Exception e) {
            log.warn("[collect] HTTP 采集失败 hostId={} url={}: {}", host.getId(), url, e.getMessage());
            return CollectOutcome.fail(e.getMessage());
        }
    }

    /**
     * 解析 JSON 负载并回写主机字段（容错：解析失败不阻断本次采集）
     *
     * @param host  主机（内存态同步刷新，供采集钩子读取）
     * @param body  响应体
     * @param write 收集实际采到的列，只更新这些列
     */
    private void applyPayload(MsServerHost host, String body, LambdaUpdateWrapper<MsServerHost> write) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(body);
            if (root.has("cpuCores")) {
                host.setServerHostCpuCores(root.get("cpuCores").asInt());
                write.set(MsServerHost::getServerHostCpuCores, host.getServerHostCpuCores());
            }
            if (root.has("memoryMb")) {
                host.setServerHostMemoryMb(root.get("memoryMb").asLong());
                write.set(MsServerHost::getServerHostMemoryMb, host.getServerHostMemoryMb());
            }
            if (root.has("diskGb")) {
                host.setServerHostDiskGb(root.get("diskGb").asLong());
                write.set(MsServerHost::getServerHostDiskGb, host.getServerHostDiskGb());
            }
            if (root.has("osVersion")) {
                host.setServerHostOsVersion(root.get("osVersion").asText());
                write.set(MsServerHost::getServerHostOsVersion, host.getServerHostOsVersion());
            }
            if (root.has("osArch")) {
                host.setServerHostOsArch(root.get("osArch").asText());
                write.set(MsServerHost::getServerHostOsArch, host.getServerHostOsArch());
            }
            if (root.has("publicIp")) {
                host.setServerHostPublicIp(root.get("publicIp").asText());
                write.set(MsServerHost::getServerHostPublicIp, host.getServerHostPublicIp());
            }
            if (root.has("privateIp")) {
                host.setServerHostPrivateIp(root.get("privateIp").asText());
                write.set(MsServerHost::getServerHostPrivateIp, host.getServerHostPrivateIp());
            }
        } catch (Exception e) {
            log.debug("[collect] HTTP 负载解析失败(忽略): {}", e.getMessage());
        }
    }
}
