package com.chua.starter.monitor.core.client;

import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * monitor 服务端能力探测器。
 *
 * <p>宿主主机资产在纳管时自动探测目标是否运行 monitor 服务端：向
 * {@code plugin.monitor.client.probe-path}（默认 /ms/monitor/agent/status，
 * monitor 专属端点）发 GET，404 视为「不支持 agent 监控」，连接失败视为
 * 「暂不可达」。两种否定结果都进入静默降级：在重探周期
 * （{@code plugin.monitor.client.reprobe-interval-seconds}，默认 300s）内
 * 不再发起探测与推送，到期自动重探，成功即恢复。</p>
 *
 * <p>探测结果含鉴权失败（401/403）视为能力存在——端点在，只是凭据未配好，
 * 由心跳/推送链路自己去暴露配置问题。探测成功或心跳/推送成功都会刷新
 * 正向缓存，避免每个采集周期都多发一次探测请求。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class MonitorCapabilityDetector {

    /**
     * 探测结果缓存时长（毫秒），正负结果共用，避免每周期探测
     */
    private final long cacheMillis;

    /**
     * 探测目标
     */
    private final MonitorClientProperties properties;

    /**
     * 共享 HTTP 客户端（NO_PROXY 直连）
     */
    private final HttpClient httpClient;

    /**
     * 上次探测时间戳（epoch ms），0 表示从未探测
     */
    private long lastProbeAt;

    /**
     * 上次探测结论
     */
    private boolean lastResult;

    /**
     * 上次对外日志的结论（仅状态翻转时打日志，避免刷屏）
     */
    private boolean lastReported;

    /**
     * 构造探测器。
     *
     * @param properties 客户端配置
     * @param httpClient 共享 HTTP 客户端
     */
    public MonitorCapabilityDetector(MonitorClientProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.cacheMillis = Math.max(5, properties.getReprobeIntervalSeconds()) * 1000L;
    }

    /**
     * 服务端是否支持 agent 监控（带缓存；缓存过期自动重探）。
     *
     * @return true 表示探测到 monitor 能力，可执行心跳与推送
     */
    public synchronized boolean isSupported() {
        long now = System.currentTimeMillis();
        if (lastProbeAt > 0 && now - lastProbeAt < cacheMillis) {
            return lastResult;
        }
        lastProbeAt = now;
        lastResult = doProbe();
        if (lastResult != lastReported) {
            lastReported = lastResult;
            if (lastResult) {
                log.info("[monitor-client] 探测到 monitor 服务端能力，开始心跳与指标推送: {}",
                        properties.getServerUrl());
            } else {
                log.warn("[monitor-client] 未探测到 monitor 能力（404 或不可达），静默降级，{} 秒后重探: path={}",
                        properties.getReprobeIntervalSeconds(), properties.getProbePath());
            }
        }
        return lastResult;
    }

    /**
     * 心跳/推送成功时调用，刷新正向缓存（能力无疑存在）。
     */
    public synchronized void markConfirmed() {
        lastProbeAt = System.currentTimeMillis();
        lastResult = true;
        if (!lastReported) {
            lastReported = true;
            log.info("[monitor-client] monitor 服务端能力已确认: {}", properties.getServerUrl());
        }
    }

    /**
     * 执行一次真实探测。
     *
     * @return 是否支持 agent 监控
     */
    private boolean doProbe() {
        try {
            HttpRequest request = MonitorHttp.get(properties, properties.getProbePath());
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 404) {
                return false;
            }
            // 2xx/401/403/5xx 均说明端点存在；401/403 属鉴权问题，由上报链路暴露
            return true;
        } catch (Exception e) {
            // 连接失败：服务端暂不可达，按不支持缓存，静默降级
            log.debug("[monitor-client] 能力探测失败（暂不可达）: {}", e.getMessage());
            return false;
        }
    }
}
