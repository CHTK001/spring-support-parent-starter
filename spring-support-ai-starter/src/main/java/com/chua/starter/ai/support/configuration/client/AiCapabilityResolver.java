package com.chua.starter.ai.support.configuration.client;

import com.chua.common.support.ai.audio.AudioClient;
import com.chua.common.support.ai.audio.AudioClientSetting;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.image.ImageClient;
import com.chua.common.support.ai.image.ImageClientSetting;
import com.chua.common.support.ai.video.VideoClient;
import com.chua.common.support.ai.video.VideoClientSetting;
import com.chua.common.support.exception.AuthenticationException;
import com.chua.common.support.utils.StringUtils;
import com.chua.deeplearning.support.capability.ModelCapabilities;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * AI 统一能力解析器。
 *
 * <p>将「能力标签 → 配置分组 → 客户端构建」统一收口，调用层仅需按能力标签
 * （如 {@link ModelCapabilities#TEXT_TO_IMAGE} / {@link ModelCapabilities#TEXT_TO_VIDEO} /
 * {@link ModelCapabilities#VISION}）解析配置并创建客户端，无需硬编码分组名
 * （image / video / vision / colorize）。</p>
 *
 * <p>能力标签与配置分组的映射集中维护在本类，分组仍使用 {@code sys_ai_global_setting}
 * 表既有分组（image / video / vision / colorize），保证前端配置面板与存量数据兼容。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCapabilityResolver {

    /**
     * 上色能力标签（图生图增强，走独立 colorize 分组）
     */
    public static final String COLORIZE = "colorize";

    /**
     * 能力标签 → 配置分组 映射
     */
    private static final Map<String, String> CAPABILITY_GROUP = Map.of(
            ModelCapabilities.TEXT_TO_IMAGE, "image",
            ModelCapabilities.TEXT_TO_VIDEO, "video",
            ModelCapabilities.VISION, "vision",
            ModelCapabilities.ASR, "asr",
            COLORIZE, "colorize"
    );

    /**
     * 全局设置服务
     */
    private final SysAiGlobalSettingService settingService;

    /**
     * 能力标签对应的配置分组名。
     *
     * <p>未在映射表中的能力标签直接作为分组名回退。</p>
     *
     * @param capability 能力标签
     * @return 配置分组名
     */
    public String groupOf(String capability) {
        if (capability == null) {
            return "";
        }
        return CAPABILITY_GROUP.getOrDefault(capability, capability);
    }

    /**
     * 读取指定能力分组的字符串配置值。
     *
     * @param capability 能力标签
     * @param field      字段名
     * @return 配置值，未配置时返回 {@link Optional#empty()}
     */
    public Optional<String> getValue(String capability, String field) {
        return settingService.getValue(groupOf(capability), field);
    }

    /**
     * 读取指定能力分组的字符串配置值。
     *
     * @param capability 能力标签
     * @param field      字段名
     * @param defaultVal 默认值
     * @return 配置值，未配置时返回默认值
     */
    public String getStr(String capability, String field, String defaultVal) {
        return getValue(capability, field).orElse(defaultVal);
    }

    /**
     * 读取指定能力分组的整型配置值。
     *
     * @param capability 能力标签
     * @param field      字段名
     * @param defaultVal 默认值
     * @return 配置值，未配置或非法时返回默认值
     */
    public int getInt(String capability, String field, int defaultVal) {
        String value = getStr(capability, field, null);
        if (StringUtils.isBlank(value)) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.debug("[AiCapabilityResolver] 字段 {} 非整数: {}", field, value);
            return defaultVal;
        }
    }

    /**
     * 解析能力配置为可读对象。
     *
     * @param capability 能力标签
     * @return 能力配置（provider / apiKey / baseUrl / model）
     */
    public CapabilityConfig resolve(String capability) {
        return new CapabilityConfig(
                getStr(capability, "provider", "openai"),
                getStr(capability, "provider_api_key", ""),
                getStr(capability, "provider_base_url", ""),
                getStr(capability, "default_model", "")
        );
    }

    /**
     * 校验能力分组已配置 API Key，缺失时抛出认证异常。
     *
     * @param capability 能力标签
     * @param displayName 能力显示名（用于错误提示）
     * @throws AuthenticationException API Key 未配置
     */
    public void requireApiKey(String capability, String displayName) {
        String apiKey = getStr(capability, "provider_api_key", "");
        if (StringUtils.isBlank(apiKey)) {
            throw new AuthenticationException(displayName + "未配置 API Key，请在 sys_ai_global_setting 中设置 "
                    + groupOf(capability) + ".provider_api_key");
        }
    }

    /**
     * 基于能力配置创建图片生成客户端。
     *
     * @param config 能力配置
     * @return 图片生成客户端
     */
    public ImageClient createImageClient(CapabilityConfig config) {
        return ImageClient.create(ImageClientSetting.builder()
                .provider(config.provider())
                .appKey(config.apiKey())
                .baseUrl(config.baseUrl())
                .build());
    }

    /**
     * 基于能力配置创建视频生成客户端。
     *
     * @param config 能力配置
     * @return 视频生成客户端
     */
    public VideoClient createVideoClient(CapabilityConfig config) {
        return VideoClient.create(VideoClientSetting.builder()
                .provider(config.provider())
                .appKey(config.apiKey())
                .baseUrl(config.baseUrl())
                .build());
    }

    /**
     * 基于能力配置创建对话客户端。
     *
     * @param config 能力配置
     * @return 对话客户端
     */
    public ChatClient createChatClient(CapabilityConfig config) {
        return ChatClient.create(ChatClientSetting.builder()
                .provider(config.provider())
                .appKey(config.apiKey())
                .baseUrl(config.baseUrl())
                .build());
    }

    /**
     * 基于能力配置创建语音识别（ASR）客户端。
     *
     * @param config 能力配置
     * @return 语音识别客户端
     */
    public AudioClient createAudioClient(CapabilityConfig config) {
        return AudioClient.create(AudioClientSetting.builder()
                .provider(config.provider())
                .appKey(config.apiKey())
                .baseUrl(config.baseUrl())
                .model(config.model())
                .build());
    }

    /**
     * 能力配置对象。
     *
     * @param provider 服务商
     * @param apiKey   API Key
     * @param baseUrl  基础地址
     * @param model    默认模型
     */
    public record CapabilityConfig(String provider, String apiKey, String baseUrl, String model) {
    }
}
