package com.chua.starter.account.web;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.common.ApiResult;
import com.chua.starter.account.ChannelRegistry;
import com.chua.starter.account.spi.ChatChannelProvider;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 免登录对话渠道（ChatClient）控制器 —— 渠道管理页的「模型」与「试对话」入口。
 *
 * <p>与 {@code /api/channels} 的账号类接口并列：本控制器的渠道来自
 * {@link ChatChannelProvider} SPI（{@code kind=CHAT}），没有绑定 / 刷新 / 签到语义，
 * 只提供两类动作：</p>
 * <ul>
 *   <li>{@code GET /{channel}/chat-models}：实时读取客户端内置模型表；</li>
 *   <li>{@code POST /{channel}/chat}：按选中的模型发起一次同步对话（用于连通性验证）。</li>
 * </ul>
 *
 * <p>每次对话都会新建客户端、结束即关闭，不在渠道侧保存任何凭据或会话。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@ApiReturnFormatIgnore
@Slf4j
@RestController
@RequestMapping("/api/channels")
public class ChatChannelController {

    /**
     * 读取免登录对话渠道的可用模型。
     *
     * <p>底层 {@code ChatClient} 扩展未引入（如宿主工程没有依赖 duckduckgo 模块）时，
     * 不返回 500，而是给出 {@code available=false} 与原因，前端据此提示「渠道未就绪」，
     * 渠道卡片本身仍可正常展示。</p>
     *
     * @param channel 渠道标识（如 {@code chat-duckduckgo}）
     * @return {@code {ok, available, channel, models, reason?}}
     */
    @GetMapping("/{channel}/chat-models")
    public ApiResult<Map<String, Object>> models(@PathVariable("channel") String channel) {
        ChatChannelProvider impl = require(channel);
        Map<String, Object> payload = new LinkedHashMap<>(5);
        payload.put("ok", true);
        payload.put("available", true);
        payload.put("channel", impl.channel());
        try {
            payload.put("models", impl.listModels());
        } catch (IllegalStateException e) {
            // 未注册的 ChatClient 扩展：模型表为空，前端提示原因而不是弹 500
            log.warn("[channel-chat] 渠道 {} 不可用: {}", impl.channel(), e.getMessage());
            payload.put("available", false);
            payload.put("models", new ArrayList<>());
            payload.put("reason", e.getMessage());
        }
        return ApiResult.ok(payload);
    }

    /**
     * 使用该渠道的某个模型发起一次同步对话。
     *
     * <p>请求体：{@code {prompt: string, model?: string, system?: string, token?: string}}。
     * 返回体：{@code {ok, channel, model, answer}}。</p>
     *
     * <p>{@code token} 用于需要登录态的渠道（如 Trae）：只在本请求内生效，
     * 不写入渠道配置；缺省时由渠道自己从本机登录态里解析。</p>
     *
     * @param channel 渠道标识
     * @param body    对话参数
     * @return 对话结果
     */
    @PostMapping("/{channel}/chat")
    public ApiResult<Map<String, Object>> chat(@PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        ChatChannelProvider impl = require(channel);
        String prompt = text(body, "prompt");
        if (prompt.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "prompt 不能为空");
        }
        String model = text(body, "model");
        String system = text(body, "system");
        String token = text(body, "token");
        long startedAt = System.currentTimeMillis();
        try {
            String answer = token.isBlank()
                    ? impl.chat(prompt, model, system)
                    : chatWithToken(impl, token, prompt, model, system);
            Map<String, Object> payload = new LinkedHashMap<>(6);
            payload.put("ok", true);
            payload.put("channel", impl.channel());
            payload.put("model", model == null || model.isBlank() ? null : model);
            payload.put("answer", answer);
            payload.put("elapsedMs", System.currentTimeMillis() - startedAt);
            return ApiResult.ok(payload);
        } catch (Exception e) {
            log.warn("[channel-chat] 渠道 {} 试对话失败: {}", impl.channel(), e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道试对话失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /**
     * 使用请求体临时携带的 token 发起对话。
     *
     * @param impl    渠道实现
     * @param token   临时令牌（如 Trae 的 JWT）
     * @param prompt  用户输入
     * @param model   模型标识
     * @param system  系统提示词
     * @return 完整回答
     */
    private String chatWithToken(ChatChannelProvider impl, String token,
            String prompt, String model, String system) {
        ChatClient client = impl.chatClient(ChatClientSetting.builder()
                .provider(impl.extension())
                .appKey(token)
                .build());
        try {
            if (model != null && !model.isBlank()) {
                client.model(model);
            }
            if (system != null && !system.isBlank()) {
                client.system(system);
            }
            return client.chatSync(prompt);
        } finally {
            try {
                client.close();
            } catch (Exception ignored) {
                // 释放资源失败不改变调用结果
            }
        }
    }

    /**
     * 可选模型清单（前端不需要额外过滤时的便捷入口）。
     *
     * @return {@code {ok, models}}；模型项含 channel 便于合并展示
     */
    @GetMapping("/chat-channels")
    public ApiResult<Map<String, Object>> listChatChannels() {
        Map<String, Object> payload = new LinkedHashMap<>(4);
        payload.put("ok", true);
        List<Map<String, Object>> channels = new ArrayList<>();
        for (ChatChannelProvider impl : ChannelRegistry.chatChannels().values()) {
            if (impl == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(5);
            item.put("channel", impl.channel());
            item.put("displayName", impl.displayName());
            item.put("extension", impl.extension());
            item.put("priority", impl.priority());
            item.put("supportsImage", impl.supportsImage());
            channels.add(item);
        }
        payload.put("channels", channels);
        return ApiResult.ok(payload);
    }

    /**
     * 解析渠道实现，不存在时抛 404。
     *
     * @param channel 渠道标识
     * @return 渠道实现
     */
    private ChatChannelProvider require(String channel) {
        ChatChannelProvider impl = ChannelRegistry.chatOf(channel);
        if (impl == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "未注册的免登录对话渠道: " + channel);
        }
        return impl;
    }

    /**
     * 从请求体读取字符串字段。
     *
     * @param body 请求体
     * @param key  字段名
     * @return 字段值；缺失返回空串
     */
    private static String text(Map<String, Object> body, String key) {
        if (body == null) {
            return "";
        }
        Object value = body.get(key);
        return value == null ? "" : value.toString();
    }
}
