package com.chua.starter.account.web;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.common.ApiResult;
import com.chua.starter.account.ChannelRegistry;
import com.chua.starter.account.channel.free.FreeChannelChatClient;
import com.chua.starter.account.spi.ChatChannelProvider;
import com.chua.starter.account.support.ChannelSiteLinks;
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
     * <p>请求体：{@code {prompt: string, model?: string, system?: string, token?: string,
     * baseUrl?: string}}。返回体：{@code {ok, channel, model, answer, elapsedMs}}。</p>
     *
     * <p>{@code token} 用于需要登录态的渠道（如 Trae）：只在本请求内生效，
     * 不写入渠道配置；缺省时由渠道自己从本机登录态里解析。
     * {@code baseUrl} 仅对声明支持自定义上游地址的渠道（{@code chat-custom}）生效：
     * 指向任意 OpenAI 兼容网关，缺省落渠道自己的缺省地址。</p>
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
        String baseUrl = impl.supportsCustomEndpoint() ? text(body, "baseUrl") : "";
        long startedAt = System.currentTimeMillis();
        try {
            String answer = token.isBlank() && baseUrl.isBlank()
                    ? impl.chat(prompt, model, system)
                    : chatWithOverrides(impl, token, baseUrl, prompt, model, system);
            Map<String, Object> payload = new LinkedHashMap<>(6);
            payload.put("ok", true);
            payload.put("channel", impl.channel());
            payload.put("model", model == null || model.isBlank() ? null : model);
            payload.put("answer", answer);
            payload.put("elapsedMs", System.currentTimeMillis() - startedAt);
            return ApiResult.ok(payload);
        } catch (Exception e) {
            log.warn("[channel-chat] 渠道 {} 试对话失败: {}", impl.channel(), e.getMessage(), e);
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道试对话失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /**
     * 按请求级覆盖参数发起对话（临时令牌 / 临时上游地址，至少一项非空）。
     *
     * <p>与无覆盖参数的路径一致，仍经免费渠道客户端执行；{@code token} 作为本次路由凭据，
     * {@code baseUrl} 作为本次路由上游地址（OpenAI 兼容自定义网关），均不落任何存储。</p>
     *
     * @param impl    渠道实现
     * @param token   临时令牌（如 Trae 的 JWT）；可为空
     * @param baseUrl 临时上游地址；可为空
     * @param prompt  用户输入
     * @param model   模型标识
     * @param system  系统提示词
     * @return 完整回答
     */
    private String chatWithOverrides(ChatChannelProvider impl, String token, String baseUrl,
            String prompt, String model, String system) {
        ChatClient client = FreeChannelChatClient.forChannel(impl.extension(),
                token.isBlank() ? null : token, baseUrl.isBlank() ? null : baseUrl);
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
            Map<String, Object> item = new LinkedHashMap<>(6);
            item.put("channel", impl.channel());
            item.put("displayName", impl.displayName());
            item.put("extension", impl.extension());
            item.put("priority", impl.priority());
            item.put("supportsImage", impl.supportsImage());
            // 官网 / 官方文档：页头「官网」入口点它，口径与 AI 账号渠道同一份（ChannelSiteLinks）。
            ChannelSiteLinks.put(item, impl.channel());
            // 内置模型清单：渠道管理页要能在「免登录对话渠道」下直接看到模型，
            // 免费渠道的模型目录也用同一份（经 KeylessChatChannel 契约汇总）。
            try {
                List<Map<String, Object>> models = impl.listModels();
                item.put("models", models == null ? List.of() : models);
            } catch (Exception e) {
                // 登录态缺失等情况下模型表可能读不到：给空清单，不让整页 500
                item.put("models", List.of());
                item.put("modelsError", e.getMessage());
            }
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
