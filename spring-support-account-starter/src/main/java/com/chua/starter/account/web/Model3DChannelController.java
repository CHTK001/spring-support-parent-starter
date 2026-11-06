package com.chua.starter.account.web;

import com.chua.common.support.ai.generation.Model3DGenerationResult;
import com.chua.starter.account.ChannelRegistry;
import com.chua.starter.account.common.ApiErrorCode;
import com.chua.starter.account.common.ApiException;
import com.chua.starter.account.common.ApiResult;
import com.chua.starter.account.spi.Model3DChannelProvider;
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
 * 图生 3D 渠道（Model3DChannelProvider）控制器 —— 渠道管理页的「3D 模型」入口。
 *
 * <ul>
 *   <li>{@code GET /{channel}/3d-models}：可选的 3D 格式 / 模型；</li>
 *   <li>{@code POST /{channel}/3d-models}：按参考图生成 3D 模型；</li>
 *   <li>{@code GET /3d-channels}：全部图生 3D 渠道元数据。</li>
 * </ul>
 *
 * <p>请求体里的 {@code endpoint} / {@code token} 用于直连型渠道（如通用 Forge 接口），
 * 只在本次调用内生效，不写入渠道配置。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@ApiReturnFormatIgnore
@Slf4j
@RestController
@RequestMapping("/api/channels")
public class Model3DChannelController {

    /**
     * 读取图生 3D 渠道的可选模型 / 格式。
     *
     * @param channel 渠道标识（如 {@code model3d-forge}）
     * @return {@code {ok, channel, models, available, reason}}
     */
    @GetMapping("/{channel}/3d-models")
    public ApiResult<Map<String, Object>> models(@PathVariable("channel") String channel) {
        Model3DChannelProvider impl = require(channel);
        Map<String, Object> payload = new LinkedHashMap<>(6);
        payload.put("ok", true);
        payload.put("channel", impl.channel());
        try {
            List<Map<String, Object>> models = impl.listModels();
            payload.put("models", models == null ? List.of() : models);
            payload.put("available", true);
        } catch (IllegalStateException e) {
            log.warn("[channel-3d] 渠道 {} 模型清单不可用: {}", impl.channel(), e.getMessage());
            payload.put("models", List.of());
            payload.put("available", false);
            payload.put("reason", e.getMessage());
        }
        return ApiResult.ok(payload);
    }

    /**
     * 生成 3D 模型。
     *
     * <p>请求体：{@code {refImageUrl, prompt?, format?, timeoutSeconds?, model?, endpoint?, token?}}；
     * 返回体：{@code {ok, channel, prompt, models:[{key, modelUrl, previewUrl, format, taskId}], elapsedMs}}。</p>
     *
     * @param channel 渠道标识
     * @param body    生成参数
     * @return 生成结果
     */
    @PostMapping("/{channel}/3d-models")
    public ApiResult<Map<String, Object>> generate(@PathVariable("channel") String channel,
            @RequestBody Map<String, Object> body) {
        Model3DChannelProvider impl = require(channel);
        String refImageUrl = text(body, "refImageUrl");
        if (refImageUrl.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "refImageUrl 不能为空");
        }
        String prompt = text(body, "prompt");
        String format = text(body, "format");
        String model = text(body, "model");
        String endpoint = text(body, "endpoint");
        String token = text(body, "token");
        int timeout = intOf(body, "timeoutSeconds", Model3DChannelProvider.DEFAULT_TIMEOUT_SECONDS);
        long startedAt = System.currentTimeMillis();
        try {
            Model3DGenerationResult result = impl.generate(prompt, refImageUrl, format,
                    timeout, model, endpoint, token);
            Map<String, Object> payload = new LinkedHashMap<>(6);
            payload.put("ok", true);
            payload.put("channel", impl.channel());
            payload.put("prompt", result.prompt());
            payload.put("models", describe(result));
            payload.put("elapsedMs", System.currentTimeMillis() - startedAt);
            return ApiResult.ok(payload);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, e.getMessage());
        } catch (Exception e) {
            log.warn("[channel-3d] 渠道 {} 生成失败: {}", impl.channel(), e.getMessage());
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "图生 3D 失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /**
     * 全部图生 3D 渠道元数据。
     *
     * @return {@code {ok, channels:[...]}}
     */
    @GetMapping("/3d-channels")
    public ApiResult<Map<String, Object>> listModel3DChannels() {
        Map<String, Object> payload = new LinkedHashMap<>(4);
        payload.put("ok", true);
        List<Map<String, Object>> channels = new ArrayList<>();
        for (Model3DChannelProvider impl : ChannelRegistry.model3dChannels().values()) {
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
     * 把生成结果压平成前端可直接渲染的列表。
     *
     * @param result 生成结果
     * @return 模型列表
     */
    private static List<Map<String, Object>> describe(Model3DGenerationResult result) {
        List<Map<String, Object>> models = new ArrayList<>();
        for (Model3DGenerationResult.GeneratedModel3D model : result.models()) {
            if (model == null || model.modelUrl() == null || model.modelUrl().isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(5);
            item.put("key", model.key());
            item.put("modelUrl", model.modelUrl());
            item.put("previewUrl", model.previewUrl());
            item.put("format", model.format());
            item.put("taskId", model.taskId());
            models.add(item);
        }
        return models;
    }

    /**
     * 解析渠道实现，不存在时抛 404。
     *
     * @param channel 渠道标识
     * @return 渠道实现
     */
    private Model3DChannelProvider require(String channel) {
        Model3DChannelProvider impl = ChannelRegistry.model3dOf(channel);
        if (impl == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "未注册的图生 3D 渠道: " + channel);
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
