package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook 控制器，替代 nomicore 的 {@code /api/webhooks} 与 {@code /api/tags/*} 契约。
 *
 * <p>维护 Webhook 端点（{@code webhook_id} 主键）与标签通知设置。签名密钥仅入参、永不出参
 * （以 {@code has_secret} 标记）。当前为进程内内存实现。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiWebhookController {

    /**
     * 主键生成与基础存储工具
     */
    private final SysAiConversationStore conversationStore;

    /**
     * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
     * Webhook 端点：webhook_标识 → 端点
     */
    private final Map<String, Map<String, Object>> webhooks = new ConcurrentHashMap<>();

    /**
     * 标签通知设置：标签 → 设置
     */
    private final Map<String, Map<String, Object>> tagSettings = new ConcurrentHashMap<>();

    @GetMapping("/webhooks")
    /**
     * 列表。
     * @return 列表的结果
     */
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(new ArrayList<>(webhooks.values()));
    }

    @GetMapping("/webhooks/{webhookId}")
    /**
     * 获取。
     * @param webhookId webhookid
     * @return 获取的结果
     */
    public ApiResult<Map<String, Object>> get(@PathVariable String webhookId) {
        return ApiResult.ok(requireWebhook(webhookId));
    }

    @PostMapping("/webhooks")
    /**
     * 创建。
     * @param body 主体
     * @return 创建的结果
     */
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String webhookId = conversationStore.newId();
        long now = System.currentTimeMillis();
        Map<String, Object> wh = new LinkedHashMap<>();
        wh.put("webhook_id", webhookId);
        wh.put("name", body.getOrDefault("name", "未命名 Webhook"));
        wh.put("platform", body.getOrDefault("platform", "http"));
        wh.put("url", body.getOrDefault("url", ""));
        wh.put("description", body.getOrDefault("description", ""));
        wh.put("has_secret", body.get("secret") != null);
        wh.put("enabled", body.getOrDefault("enabled", true));
        wh.put("created_at", now);
        wh.put("updated_at", now);
        webhooks.put(webhookId, wh);
        pushService.push("webhook.updated", wh);
        return ApiResult.ok(wh);
    }

    @PutMapping("/webhooks/{webhookId}")
    public ApiResult<Map<String, Object>> update(@PathVariable String webhookId,
                                                 @RequestBody Map<String, Object> body) {
        Map<String, Object> wh = requireWebhook(webhookId);
        if (body.containsKey("name")) {
            wh.put("name", body.get("name"));
        }
        if (body.containsKey("url")) {
            wh.put("url", body.get("url"));
        }
        if (body.containsKey("platform")) {
            wh.put("platform", body.get("platform"));
        }
        if (body.containsKey("description")) {
            wh.put("description", body.get("description"));
        }
        if (body.containsKey("enabled")) {
            wh.put("enabled", body.get("enabled"));
        }
        if (body.containsKey("secret")) {
            wh.put("has_secret", body.get("secret") != null);
        }
        wh.put("updated_at", System.currentTimeMillis());
        pushService.push("webhook.updated", wh);
        return ApiResult.ok(wh);
    }

    @DeleteMapping("/webhooks/{webhookId}")
    /**
     * 移除。
     * @param webhookId webhookid
     * @return 移除的结果
     */
    public ApiResult<Void> remove(@PathVariable String webhookId) {
        /**
         * requirewebhook。
         * @param webhookId webhookid
         */
        requireWebhook(webhookId);
        webhooks.remove(webhookId);
        pushService.push("webhook.removed", Map.of("webhook_id", webhookId));
        return ApiResult.ok();
    }

    @PostMapping("/webhooks/{webhookId}/test")
    /**
     * 测试。
     * @param webhookId webhookid
     * @return 测试的结果
     */
    public ApiResult<Void> test(@PathVariable String webhookId) {
        /**
         * requirewebhook。
         * @param webhookId webhookid
         */
        requireWebhook(webhookId);
        return ApiResult.ok();
    }

    @GetMapping("/tags/{tag}/settings")
    /**
     * 获取标签setting。
     * @param tag 标签
     * @return 获取标签setting的结果
     */
    public ApiResult<Map<String, Object>> getTagSetting(@PathVariable String tag) {
        return ApiResult.ok(tagSettings.computeIfAbsent(tag, k -> {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("tag", k);
            s.put("webhook_id", null);
            s.put("description", "");
            s.put("notify_events", new ArrayList<>());
            return s;
        }));
    }

    @PutMapping("/tags/{tag}/settings")
    public ApiResult<Map<String, Object>> setTagSetting(@PathVariable String tag,
                                                        @RequestBody Map<String, Object> body) {
        Map<String, Object> s = tagSettings.computeIfAbsent(tag, k -> new LinkedHashMap<>());
        s.put("tag", tag);
        if (body.containsKey("webhook_id")) {
            s.put("webhook_id", body.get("webhook_id"));
        }
        if (body.containsKey("description")) {
            s.put("description", body.get("description"));
        }
        if (body.containsKey("notify_events")) {
            s.put("notify_events", body.get("notify_events"));
        }
        return ApiResult.ok(s);
    }

    /**
     * requirewebhook。
     * @param webhookId webhookid
     * @return requireWebhook的结果
     */
    private Map<String, Object> requireWebhook(String webhookId) {
        Map<String, Object> wh = webhooks.get(webhookId);
        if (wh == null) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param webhookId webhookid
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "Webhook 不存在: " + webhookId);
        }
        return wh;
    }
}
