package com.chua.starter.account.web;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.generation.VideoGenerationResult;
import com.chua.starter.account.ChannelRegistry;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.common.ApiResult;
import com.chua.starter.account.spi.VideoChannelProvider;
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
 * 文生视频渠道（VideoChannelProvider）控制器 —— 渠道管理页的「出视频」入口。
 *
 * <ul>
 *   <li>{@code GET /{channel}/video-models}：可选的视频模型；</li>
 *   <li>{@code POST /{channel}/videos}：按提示词生成视频；</li>
 *   <li>{@code GET /video-channels}：全部文生视频渠道元数据。</li>
 * </ul>
 *
 * <p>需要登录态的渠道（豆包 / 通义）由请求体临时携带 {@code token}（站点 Cookie），
 * 本次调用结束即释放客户端，不写入渠道配置。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@ApiReturnFormatIgnore
@Slf4j
@RestController
@RequestMapping("/api/channels")
public class VideoChannelController {

    /**
     * 读取文生视频渠道的可选模型。
     *
     * @param channel 渠道标识（如 {@code video-doubao}）
     * @return {@code {ok, channel, models, available, reason}}
     */
    @GetMapping("/{channel}/video-models")
    public ApiResult<Map<String, Object>> models(@PathVariable("channel") String channel) {
        VideoChannelProvider impl = require(channel);
        Map<String, Object> payload = new LinkedHashMap<>(6);
        payload.put("ok", true);
        payload.put("channel", impl.channel());
        try {
            List<Map<String, Object>> models = impl.listModels();
            payload.put("models", models == null ? List.of() : models);
            payload.put("available", true);
        } catch (IllegalStateException e) {
            log.warn("[channel-video] 渠道 {} 模型清单不可用: {}", impl.channel(), e.getMessage());
            payload.put("models", List.of());
            payload.put("available", false);
            payload.put("reason", e.getMessage());
        }
        return ApiResult.ok(payload);
    }

    /**
     * 生成视频。
     *
     * <p>请求体：{@code {prompt, ratio?, cameraMovement?, refImageKey?, timeoutSeconds?, model?, token?}}；
     * 返回体：{@code {ok, channel, prompt, videos:[{videoUrl, coverUrl, width, height, duration}], elapsedMs}}。</p>
     *
     * @param channel 渠道标识
     * @param body    生成参数
     * @return 生成结果
     */
    @PostMapping("/{channel}/videos")
    public ApiResult<Map<String, Object>> generate(@PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        VideoChannelProvider impl = require(channel);
        String prompt = text(body, "prompt");
        if (prompt.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "prompt 不能为空");
        }
        String ratio = text(body, "ratio");
        String cameraMovement = text(body, "cameraMovement");
        String refImageKey = text(body, "refImageKey");
        String model = text(body, "model");
        String token = text(body, "token");
        int timeout = intOf(body, "timeoutSeconds", VideoChannelProvider.DEFAULT_TIMEOUT_SECONDS);
        long startedAt = System.currentTimeMillis();
        try {
            VideoGenerationResult result = token.isBlank()
                    ? impl.generate(prompt, ratio, cameraMovement, refImageKey, timeout, model)
                    : generateWithToken(impl, token, prompt, ratio, cameraMovement, refImageKey, timeout, model);
            Map<String, Object> payload = new LinkedHashMap<>(6);
            payload.put("ok", true);
            payload.put("channel", impl.channel());
            payload.put("prompt", result.prompt());
            payload.put("videos", describe(result));
            payload.put("elapsedMs", System.currentTimeMillis() - startedAt);
            return ApiResult.ok(payload);
        } catch (Exception e) {
            log.warn("[channel-video] 渠道 {} 出视频失败: {}", impl.channel(), e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "文生视频失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /**
     * 全部文生视频渠道元数据。
     *
     * @return {@code {ok, channels:[...]}}
     */
    @GetMapping("/video-channels")
    public ApiResult<Map<String, Object>> listVideoChannels() {
        Map<String, Object> payload = new LinkedHashMap<>(4);
        payload.put("ok", true);
        List<Map<String, Object>> channels = new ArrayList<>();
        for (VideoChannelProvider impl : ChannelRegistry.videoChannels().values()) {
            if (impl == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(6);
            item.put("channel", impl.channel());
            item.put("displayName", impl.displayName());
            item.put("extension", impl.extension());
            item.put("priority", impl.priority());
            try {
                List<Map<String, Object>> models = impl.listModels();
                item.put("models", models == null ? List.of() : models);
            } catch (RuntimeException e) {
                item.put("models", List.of());
            }
            channels.add(item);
        }
        payload.put("channels", channels);
        return ApiResult.ok(payload);
    }

    /**
     * 使用请求体临时携带的 Cookie / Token 生成视频。
     *
     * @param impl           渠道
     * @param token          临时凭据
     * @param prompt         提示词
     * @param ratio          宽高比
     * @param cameraMovement 镜头运动
     * @param refImageKey    参考图标识
     * @param timeout        超时秒数
     * @param model          视频模型
     * @return 生成结果
     */
    private VideoGenerationResult generateWithToken(VideoChannelProvider impl, String token,
            String prompt, String ratio, String cameraMovement, String refImageKey,
            int timeout, String model) {
        String extension = impl.extension();
        if (extension == null || extension.isBlank()) {
            return impl.generate(prompt, ratio, cameraMovement, refImageKey, timeout, model);
        }
        ChatClient client = impl.chatClient(ChatClientSetting.builder()
                .provider(extension)
                .appKey(token)
                .build());
        try {
            if (model != null && !model.isBlank()) {
                client.model(model.trim());
            }
            return client.generateVideo(prompt, ratio, cameraMovement, refImageKey, timeout);
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
     * @param result 生成结果
     * @return 视频列表
     */
    private static List<Map<String, Object>> describe(VideoGenerationResult result) {
        List<Map<String, Object>> videos = new ArrayList<>();
        for (VideoGenerationResult.GeneratedVideo video : result.videos()) {
            if (video == null || video.videoUrl() == null || video.videoUrl().isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(5);
            item.put("videoUrl", video.videoUrl());
            item.put("coverUrl", video.coverUrl());
            item.put("width", video.width());
            item.put("height", video.height());
            item.put("duration", video.duration());
            videos.add(item);
        }
        return videos;
    }

    /**
     * 解析渠道实现，不存在时抛 404。
     *
     * @param channel 渠道标识
     * @return 渠道实现
     */
    private VideoChannelProvider require(String channel) {
        VideoChannelProvider impl = ChannelRegistry.videoOf(channel);
        if (impl == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "未注册的文生视频渠道: " + channel);
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
