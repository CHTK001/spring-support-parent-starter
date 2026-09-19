package com.chua.starter.monitor.support.service.alarm;

import com.chua.starter.monitor.support.service.MonitorConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Webhook 通知发送器
 * <p>
 * 从全局配置读取 {@code monitor.alert.webhook.url}，存在时以 POST JSON 投递；
 * 未配置时静默跳过。超时 5 秒，失败仅记录日志不影响主链路。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookNotifySender implements NotifySender {

    /**
     * webhook 地址配置键
    */
    public static final String CONFIG_KEY = "monitor.alert.webhook.url";

    /**
     * 全局配置服务
    */
    private final MonitorConfigService monitorConfigService;

    /**
     * HTTP 客户端（短超时）
    */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 渠道名称
     *
     * @return 渠道标识
     */
    @Override
    public String channel() {
        return "webhook";
    }

    /**
     * 发送告警 JSON 到配置的 webhook 地址
     *
     * @param title    标题
     * @param content  内容
     * @param eventUrl 事件详情地址
     */
    @Override
    public void send(String title, String content, String eventUrl) {
        String url = monitorConfigService.getString(CONFIG_KEY, "");
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            var payload = Map.of("title", title == null ? "" : title,
                    "content", content == null ? "" : content,
                    "eventUrl", eventUrl == null ? "" : eventUrl);
            var request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("[alarm] webhook 响应异常 status={} body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("[alarm] webhook 发送失败: {}", e.getMessage());
        }
    }
}
