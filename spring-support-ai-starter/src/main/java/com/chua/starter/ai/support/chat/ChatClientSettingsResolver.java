package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
* 聊天客户端配置解析器。
*
* 当前版本仅从 sys_AI_全局_setting(runtime) 读取 LLM 主链路配置。
*
* @since 2026/04/03
* @author CH
 */
public final class ChatClientSettingsResolver {

    /** 分组 */
    private static final String GROUP = "runtime";
    /** 默认_超时_millis */
    private static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    /** 创建 对话客户端settings解析器 实例 */
    private ChatClientSettingsResolver() {
    }

    /**
    * 解析
    *
    * @param sysAiGlobalSettingService sysAI全局setting服务
    * @return resolve的结果
    */
    public static ChatClientSettings resolve(SysAiGlobalSettingService sysAiGlobalSettingService) {
        boolean enabled = getBoolean(sysAiGlobalSettingService, "enabled", true);
        if (!enabled) {
            /**
            * illegal状态异常。
            * @param 已禁用" 已禁用"
            * @return illegal状态异常的结果
            */
            throw new IllegalStateException("sys_ai_global_setting.runtime.enabled=false，ChatClient 已禁用");
        }

        String provider = firstNonBlank(
                /**
                * 获取价值观。
                * @param sysAiGlobalSettingService sysAI全局setting服务
                * @param "provider" "提供者"
                */
                getValue(sysAiGlobalSettingService, "provider"),
                "openai"
        );
        String factory = firstNonBlank(
                /**
                * 获取价值观。
                * @param sysAiGlobalSettingService sysAI全局setting服务
                * @param "factory" "工厂"
                */
                getValue(sysAiGlobalSettingService, "factory"),
                "default"
        );

        Map<String, String> extraHeaders = parseExtraHeaders(getValue(sysAiGlobalSettingService, "extra_headers"));

        ChatClientSetting setting = ChatClientSetting.builder()
                .provider(provider)
                .baseUrl(firstNonBlank(
                        /**
                        * 获取价值观。
                        * @param sysAiGlobalSettingService sysAI全局setting服务
                        * @param "provider_base_url" "提供者_基础_url"
                        */
                        getValue(sysAiGlobalSettingService, "provider_base_url"),
                        /**
                        * 获取价值观。
                        * @param sysAiGlobalSettingService sysAI全局setting服务
                        * @param "provider_api_address" "提供者_api_地址"
                        */
                        getValue(sysAiGlobalSettingService, "provider_api_address")
                ))
                .appKey(firstNonBlank(
                        /**
                        * 获取价值观。
                        * @param sysAiGlobalSettingService sysAI全局setting服务
                        * @param "provider_api_key" "提供者_api_键"
                        */
                        getValue(sysAiGlobalSettingService, "provider_api_key"),
                        /**
                        * 获取价值观。
                        * @param sysAiGlobalSettingService sysAI全局setting服务
                        * @param "provider_app_key" "提供者_app_键"
                        */
                        getValue(sysAiGlobalSettingService, "provider_app_key")
                ))
                .appSecret(firstNonBlank(
                        /**
                        * 获取价值观。
                        * @param sysAiGlobalSettingService sysAI全局setting服务
                        * @param "provider_app_secret" "提供者_app_secret"
                        */
                        getValue(sysAiGlobalSettingService, "provider_app_secret"),
                        /**
                        * 获取价值观。
                        * @param sysAiGlobalSettingService sysAI全局setting服务
                        * @param "provider_secret_key" "提供者_secret_键"
                        */
                        getValue(sysAiGlobalSettingService, "provider_secret_key")
                ))
                .proxy(getValue(sysAiGlobalSettingService, "provider_proxy"))
                .extraHeaders(extraHeaders)
                .build();

        return ChatClientSettings.builder()
                .factory(factory)
                .provider(provider)
                .defaultModel(getValue(sysAiGlobalSettingService, "default_model"))
                .compressionModel(getValue(sysAiGlobalSettingService, "compression_model"))
                .systemPrompt(getValue(sysAiGlobalSettingService, "system_prompt"))
                .temperature(getDouble(sysAiGlobalSettingService, "temperature"))
                .maxTokens(getInteger(sysAiGlobalSettingService, "max_tokens"))
                .timeoutMillis(getLong(sysAiGlobalSettingService, "timeout_millis", DEFAULT_TIMEOUT_MILLIS))
                .inputOptimizationEnabled(getBoolean(sysAiGlobalSettingService, "input_optimization_enabled", false))
                .contextCompressionEnabled(getBoolean(sysAiGlobalSettingService, "context_compression_enabled", false))
                .contextCompressionThreshold(getInteger(sysAiGlobalSettingService, "context_compression_threshold", 12))
                .contextCompressionRetainMessages(getInteger(sysAiGlobalSettingService, "context_compression_retain_messages", 6))
                .baseSetting(setting)
                .build();
    }

    /**
    * 获取价值观
    *
    * @param service 服务
    * @param field 字段
    * @return 获取价值观的结果
    */
    private static String getValue(SysAiGlobalSettingService service, String field) {
        return service.getValue(GROUP, field).orElse(null);
    }

    /**
    * 获取布尔值
    *
    * @param service 服务
    * @param field 字段
    * @param defaultValue 默认价值观
    * @return 获取布尔值的结果
    */
    private static boolean getBoolean(SysAiGlobalSettingService service, String field, boolean defaultValue) {
        return service.getBoolean(GROUP, field, defaultValue);
    }

    /**
    * 获取Integer
    *
    * @param service 服务
    * @param field 字段
    * @return 获取integer的结果
    */
    private static Integer getInteger(SysAiGlobalSettingService service, String field) {
        return getInteger(service, field, null);
    }

    /**
    * 获取Integer
    *
    * @param service 服务
    * @param field 字段
    * @param defaultValue 默认价值观
    * @return 获取integer的结果
    */
    private static Integer getInteger(SysAiGlobalSettingService service, String field, Integer defaultValue) {
        String value = getValue(service, field);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /**
    * 获取Double
    *
    * @param service 服务
    * @param field 字段
    * @return 获取double的结果
    */
    private static Double getDouble(SysAiGlobalSettingService service, String field) {
        String value = getValue(service, field);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
    * 获取Long
    *
    * @param service 服务
    * @param field 字段
    * @param defaultValue 默认价值观
    * @return 获取long的结果
    */
    private static Long getLong(SysAiGlobalSettingService service, String field, long defaultValue) {
        String value = getValue(service, field);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /**
    * 第一个nonblank
    *
    * @param values 价值观
    * @return 第一个nonblank的结果
    */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    /**
    * 解析 extra_头部 JSON 字符串为 映射
    *
    * @param json json
    * @return 解析extra头部的结果
    */
    private static Map<String, String> parseExtraHeaders(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            /**
            * 对象映射器。
            * @return 对象映射器的结果
            */
            return new ObjectMapper().readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception ignored) {
            return null;
        }
    }
}
