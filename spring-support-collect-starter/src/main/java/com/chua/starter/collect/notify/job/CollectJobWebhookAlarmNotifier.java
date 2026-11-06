// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.notify.job;

import com.chua.starter.collect.support.CollectJson;
import com.chua.starter.job.support.notify.JobAlarmMessage;
import com.chua.starter.job.support.notify.JobAlarmNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调度任务的 Webhook 告警通道（类型 WEBHOOK）。
 *
 * <p>把告警消息以 JSON POST 到台账配置的回调地址；HTTP 2xx 视为投递成功，
 * 非 2xx 抛出带状态码与响应摘要的异常，由执行器写进任务日志的告警段。
 * 只允许 http/https，避免把目标配成 file:// 之类的本地读取。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(JobAlarmNotifier.class)
public class CollectJobWebhookAlarmNotifier implements JobAlarmNotifier {

    /**
     * 单次回调的连接与读取超时毫秒。
     */
    private static final long TIMEOUT_MS = 5000L;

    @Override
    public String type() {
        return JobAlarmMessage.TYPE_WEBHOOK;
    }

    @Override
    public void notify(JobAlarmMessage message) throws Exception {
        String target = message.target() == null ? "" : message.target().trim();
        URI uri;
        try {
            uri = URI.create(target);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("回调地址不合法: " + target);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (!(scheme.startsWith("http")) || uri.getHost() == null) {
            throw new IllegalStateException("回调地址必须是 http/https，当前: " + target);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", message.alarmType());
        payload.put("jobName", message.jobName());
        payload.put("costMs", message.costMs());
        payload.put("exception", message.exception());
        payload.put("nodeId", message.nodeId());
        payload.put("occurredAt", message.occurredAtMs());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(TIMEOUT_MS))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(CollectJson.toJson(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(TIMEOUT_MS))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Webhook HTTP " + status + " " + brief(response.body()));
        }
        log.info("[collect] 调度任务 Webhook 告警已投递 job={} url={} status={}",
                message.jobName(), target, status);
    }

    /**
     * 响应体截断，避免整页 HTML 写进任务日志。
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