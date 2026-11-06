// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.notify.impl;

import com.chua.common.support.task.message.MessageResponse;
import com.chua.starter.collect.dto.CollectNotifyMessage;
import com.chua.starter.collect.notify.CollectNotifyChannel;
import com.chua.starter.collect.support.CollectJson;
import com.chua.starter.collect.vo.CollectNotifyPayloadVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Webhook 通知渠道：把渲染好的消息以 JSON POST 到配置的地址。
 *
 * <p>HTTP 2xx 视为投递成功；被调方返回什么内容不做业务判定，原文前 200 字符会留在最近结果里，
 * 方便对接钉钉/企业微信这类"200 但 body 里带 errcode"的网关。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "plugin.collect.notify", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectWebhookNotifyChannel implements CollectNotifyChannel {

    /**
     * 回调地址前缀白名单，避免配成 file:// 之类的本地读取。
     */
    private static final String HTTP_SCHEME = "http";

    @Override
    public String channel() {
        return "webhook";
    }

    @Override
    public String label() {
        return "Webhook";
    }

    @Override
    public String targetHint() {
        return "HTTP 回调地址";
    }

    @Override
    public MessageResponse send(CollectNotifyMessage message) {
        long start = System.currentTimeMillis();
        String target = message.target() == null ? "" : message.target().trim();
        URI uri;
        try {
            uri = URI.create(target);
        } catch (IllegalArgumentException ex) {
            return MessageResponse.failure("回调地址不合法: " + target);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.startsWith(HTTP_SCHEME) || uri.getHost() == null) {
            return MessageResponse.failure("回调地址必须是 http/https，当前: " + target);
        }
        CollectNotifyPayloadVO payload = new CollectNotifyPayloadVO(
                message.subject(), message.content(), message.contentType());
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(Math.max(1000L, message.timeoutMs())))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(CollectJson.toJson(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Math.max(1000L, message.timeoutMs())))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long cost = System.currentTimeMillis() - start;
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return MessageResponse.builder()
                        .success(true)
                        .messageId(status + " " + brief(response.body()))
                        .durationMillis(cost)
                        .build();
            }
            return MessageResponse.builder()
                    .success(false)
                    .errorMessage("HTTP " + status + " " + brief(response.body()))
                    .durationMillis(cost)
                    .build();
        } catch (Exception ex) {
            log.warn("[collect] 通知回调失败 url={} err={}", target, ex.getMessage());
            return MessageResponse.builder()
                    .success(false)
                    .errorMessage(ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .durationMillis(System.currentTimeMillis() - start)
                    .build();
        }
    }

    /**
     * 响应体截断，避免整页 HTML 写进最近结果。
     *
     * @param body 响应体
     * @return 截断后的单行文本
     */
    private String brief(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replaceAll("\\s+", " ").trim();
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }
}