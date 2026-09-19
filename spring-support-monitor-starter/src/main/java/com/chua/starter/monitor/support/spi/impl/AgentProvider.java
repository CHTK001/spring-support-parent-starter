package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.server.support.entity.MsServerHost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Agent HTTP 系统信息 Provider。
 *
 * <p>面向安装了 monitor-agent 的主机（osType=AGENT），通过 Agent 的 19876 HTTP 端口获取信息。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class AgentProvider implements SystemInfoProvider {

    /**
     * HTTP 客户端（连接超时 5s）
    */
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /**
     * 拼接 Agent 基础地址。
     *
     * @param host 目标主机
     * @return Agent 地址
     */
    private String agentUrl(MsServerHost host) {
        return "http://" + host.getServerHostAddress() + ":19876";
    }

    /**
     * 主机类型是否为 AGENT。
     *
     * @param host 目标主机
     * @return 类型为 AGENT 时返回 true
     */
    @Override
    public boolean supports(MsServerHost host) {
        return "AGENT".equalsIgnoreCase(host.getServerHostOsType());
    }

    /**
     * 发起 GET 请求并把响应反序列化为指定类型。
     *
     * @param url  请求地址
     * @param type 目标类型
     * @param <T>  返回类型
     * @return 反序列化结果；请求失败返回 null
     */
    @SuppressWarnings("unchecked")
    private <T> T get(String url, Class<T> type) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                if (type == String.class) {
                    return (T) r.body();
                }
                return (T) new com.fasterxml.jackson.databind.ObjectMapper().readValue(r.body(), type);
            }
        } catch (Exception e) {
            // 请求或反序列化失败时返回 null，由调用方兜底处理
            log.warn("Agent 接口调用失败: {}", url, e);
        }
        return null;
    }

    @Override
    public Map<String, Object> hostInfo(MsServerHost host) {
        return get(agentUrl(host) + "/info", Map.class);
    }

    @Override
    public List<Map<String, Object>> processes(MsServerHost h, int p, int ps) {
        return get(agentUrl(h) + "/processes?page=" + p + "&pageSize=" + ps, List.class);
    }

    @Override
    public List<Map<String, Object>> services(MsServerHost h, int p, int ps) {
        return get(agentUrl(h) + "/services?page=" + p + "&pageSize=" + ps, List.class);
    }

    @Override
    public List<Map<String, Object>> disks(MsServerHost h) {
        return get(agentUrl(h) + "/disks", List.class);
    }

    @Override
    public List<Map<String, Object>> networkInterfaces(MsServerHost h) {
        return get(agentUrl(h) + "/network", List.class);
    }

    @Override
    public List<Map<String, Object>> netProcesses(MsServerHost h) {
        return get(agentUrl(h) + "/netproc", List.class);
    }

    @Override
    public List<Map<String, Object>> files(MsServerHost h, String path) {
        return get(agentUrl(h) + "/files?path=" + path, List.class);
    }

    @Override
    public boolean uploadFile(MsServerHost h, String remotePath, InputStream d, long s) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(agentUrl(h) + "/upload?path=" + remotePath))
                    .PUT(HttpRequest.BodyPublishers.ofInputStream(() -> d))
                    .build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200;
        } catch (Exception e) {
            // 上传失败统一返回 false，由调用方决定是否重试
            return false;
        }
    }

    @Override
    public InputStream downloadFile(MsServerHost h, String remotePath) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(agentUrl(h) + "/download?path=" + remotePath))
                    .GET().build();
            HttpResponse<java.io.InputStream> r = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            return r.statusCode() == 200 ? r.body() : null;
        } catch (Exception e) {
            // 下载失败返回 null，由调用方判断流是否可用
            return null;
        }
    }

    @Override
    public String exec(MsServerHost h, String cmd) {
        return get(agentUrl(h) + "/exec?cmd=" + cmd, String.class);
    }

    @Override
    public List<Map<String, Object>> env(MsServerHost h) {
        return get(agentUrl(h) + "/env", List.class);
    }
}
