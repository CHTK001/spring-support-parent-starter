package com.chua.starter.channel.web;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.generation.ImageGenerationResult;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.channel.ChannelRegistry;
import com.chua.starter.channel.spi.ImageChannelProvider;
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
 * 文生图渠道（ImageChannelProvider）控制器 —— 渠道管理页的「出图」入口。
 *
 * <p>与 {@code /api/channels} 的账号类接口、{@code /{channel}/chat} 的对话接口并列：
 * 本控制器只处理图生图相关动作，渠道来自 {@link ImageChannelProvider} SPI（{@code kind=IMAGE}）。</p>
 * <ul>
 *   <li>{@code GET /{channel}/image-models}：可选出图模型（免登录渠道多为静态表）；</li>
 *   <li>{@code POST /{channel}/images}：按提示词出图，返回可直接访问的图片地址；</li>
 *   <li>{@code GET /image-channels}：全部文生图渠道元数据。</li>
 * </ul>
 *
 * <p>不落任何凭据：需要登录态的渠道（如 Trae）由请求体临时携带 {@code token}，
 * 本次调用结束即释放客户端，不写入渠道配置。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@ApiReturnFormatIgnore
@Slf4j
@RestController
@RequestMapping("/api/channels")
public class ImageChannelController {

    /**
     * 单次请求允许的最大出图张数。
     *
     * <p>出图是按张计费 / 耗时线性增长的同步调用（Trae 单张约 8.8 积分），必须在 API 边界
     * 收口：请求体里的 {@code n} 由外部可控，若直接透传，一个 {@code n=50} 的请求就能造成
     * 几分钟阻塞和真实费用。前端也只会给 1~4 张的选项。</p>
     */
    private static final int MAX_IMAGES_PER_REQUEST = 4;

    /**
     * 读取文生图渠道的可选模型。
     *
     * <p>模型清单异常（如宿主未引入对应 ChatClient 模块）不返回 500：
     * 与对话渠道一致，降级为空列表 + 原因，前端据此展示占位提示。</p>
     *
     * @param channel 渠道标识（如 {@code image-pollinations}）
     * @return {@code {ok, channel, models, available, reason}}
     */
    @GetMapping("/{channel}/image-models")
    public ApiResult<Map<String, Object>> models(@PathVariable("channel") String channel) {
        ImageChannelProvider impl = require(channel);
        Map<String, Object> payload = new LinkedHashMap<>(8);
        payload.put("ok", true);
        payload.put("channel", impl.channel());
        try {
            List<Map<String, Object>> models = impl.listModels();
            payload.put("models", models == null ? List.of() : models);
            payload.put("available", true);
        } catch (IllegalStateException e) {
            log.warn("[channel-image] 渠道 {} 模型清单不可用: {}", impl.channel(), e.getMessage());
            payload.put("models", List.of());
            payload.put("available", false);
            payload.put("reason", e.getMessage());
        }
        return ApiResult.ok(payload);
    }

    /**
     * 生成图片。
     *
     * <p>请求体：{@code {prompt, ratio?, n?, width?, height?, model?, token?}}；
     * 返回体：{@code {ok, channel, prompt, images:[{key,url,thumbUrl,width,height,format}], elapsedMs}}。</p>
     *
     * @param channel 渠道标识
     * @param body    出图参数
     * @return 生成结果
     */
    @PostMapping("/{channel}/images")
    public ApiResult<Map<String, Object>> generate(@PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        ImageChannelProvider impl = require(channel);
        String prompt = text(body, "prompt");
        if (prompt.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "prompt 不能为空");
        }
        String ratio = text(body, "ratio");
        String model = text(body, "model");
        String token = text(body, "token");
        int n = clampImages(intOf(body, "n", 1));
        int width = intOf(body, "width", 0);
        int height = intOf(body, "height", 0);
        long startedAt = System.currentTimeMillis();
        try {
            ImageGenerationResult result = token.isBlank()
                    ? impl.generate(prompt, ratio, n, width, height, model)
                    : generateWithToken(impl, token, prompt, ratio, n, width, height, model);
            Map<String, Object> payload = new LinkedHashMap<>(5);
            payload.put("ok", true);
            payload.put("channel", impl.channel());
            payload.put("prompt", result.prompt());
            payload.put("images", describe(result));
            payload.put("elapsedMs", System.currentTimeMillis() - startedAt);
            return ApiResult.ok(payload);
        } catch (Exception e) {
            log.warn("[channel-image] 渠道 {} 出图失败: {}", impl.channel(), e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "文生图失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /**
     * 全部文生图渠道元数据（前端渠道卡片兜底来源）。
     *
     * @return {@code {ok, channels:[...]}}
     */
    @GetMapping("/image-channels")
    public ApiResult<Map<String, Object>> listImageChannels() {
        Map<String, Object> payload = new LinkedHashMap<>(4);
        payload.put("ok", true);
        List<Map<String, Object>> channels = new ArrayList<>();
        for (ImageChannelProvider impl : ChannelRegistry.imageChannels().values()) {
            if (impl == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(5);
            item.put("channel", impl.channel());
            item.put("displayName", impl.displayName());
            item.put("extension", impl.extension());
            item.put("priority", impl.priority());
            // 单个渠道的模型清单异常（如底层 ChatClient 未引入）不应拖垮整个列表接口
            try {
                List<Map<String, Object>> models = impl.listModels();
                item.put("models", models == null ? List.of() : models);
            } catch (RuntimeException e) {
                log.warn("[channel-image] 渠道 {} 模型清单不可用: {}", impl.channel(), e.getMessage());
                item.put("models", List.of());
            }
            channels.add(item);
        }
        payload.put("channels", channels);
        return ApiResult.ok(payload);
    }

    /**
     * 使用请求体临时携带的 token 出图（仅对有 {@code ChatClient} 扩展的渠道有效）。
     *
     * @param impl    渠道
     * @param token   临时 JWT
     * @param prompt  提示词
     * @param ratio   宽高比
     * @param n       张数
     * @param width   宽度
     * @param height  高度
     * @param model   出图模型，可为空
     * @return 生成结果
     */
    private ImageGenerationResult generateWithToken(ImageChannelProvider impl, String token,
            String prompt, String ratio, int n, int width, int height, String model) {
        String extension = impl.extension();
        if (extension == null || extension.isBlank()) {
            // 不依赖 ChatClient 的渠道（如直连图床），token 无意义，回落默认实现
            return impl.generate(prompt, ratio, n, width, height, model);
        }
        ChatClient client = impl.chatClient(ChatClientSetting.builder()
                .provider(extension)
                .appKey(token)
                .build());
        try {
            // 模型通过 client.model(...) 注入（generateImage 本身没有 model 形参）
            if (model != null && !model.isBlank()) {
                client.model(model.trim());
            }
            return client.generateImage(prompt, ratio, n, width, height, null, null);
        } finally {
            try {
                client.close();
            } catch (Exception ignored) {
                // 释放资源失败不改变调用结果
            }
        }
    }

    /**
     * 把生成结果压平成前端可直接渲染的列表。
     *
     * <p>各实现给的 URL 字段并不一致（例如 Trae 只回一个签名地址、部分实现只给缩略图），
     * 这里做一次兜底：原图 → 预览图 → 缩略图依次取第一个非空值；三者都为空的条目直接跳过，
     * 避免前端渲染出一堆坏图。</p>
     *
     * @param result 生成结果
     * @return 图片列表
     */
    private static List<Map<String, Object>> describe(ImageGenerationResult result) {
        List<Map<String, Object>> images = new ArrayList<>();
        for (ImageGenerationResult.GeneratedImage image : result.images()) {
            if (image == null) {
                continue;
            }
            String url = firstNonBlank(image.oriUrl(), image.rawUrl(), image.thumbUrl());
            if (url.isEmpty()) {
                continue;
            }
            String thumb = firstNonBlank(image.thumbUrl(), image.oriUrl(), image.rawUrl());
            Map<String, Object> item = new LinkedHashMap<>(6);
            item.put("key", image.key());
            item.put("url", url);
            item.put("thumbUrl", thumb.isEmpty() ? url : thumb);
            item.put("width", image.width());
            item.put("height", image.height());
            item.put("format", image.format());
            images.add(item);
        }
        return images;
    }

    /**
     * 取第一个非空白字符串。
     *
     * @param values 候选值
     * @return 第一个非空值；全为空时返回空串
     */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /**
     * 解析渠道实现，不存在时抛 404。
     *
     * @param channel 渠道标识
     * @return 渠道实现
     */
    private ImageChannelProvider require(String channel) {
        ImageChannelProvider impl = ChannelRegistry.imageOf(channel);
        if (impl == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "未注册的文生图渠道: " + channel);
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

    /**
     * 把张数收敛到 {@code [1, MAX_IMAGES_PER_REQUEST]}。
     *
     * <p>非正数（含负数、0）按 1 处理，超上限按上限处理：出图是同步计费调用，
     * 必须保证「任何请求都不会被放大成几十张」。</p>
     *
     * @param n 请求里的张数
     * @return 合法张数
     */
    private static int clampImages(int n) {
        return Math.max(1, Math.min(MAX_IMAGES_PER_REQUEST, n));
    }

    /**
     * 从请求体读取整数字段。
     *
     * @param body         请求体
     * @param key          字段名
     * @param defaultValue 缺省值
     * @return 整数值
     */
    private static int intOf(Map<String, Object> body, String key, int defaultValue) {
        String value = text(body, key);
        if (value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
