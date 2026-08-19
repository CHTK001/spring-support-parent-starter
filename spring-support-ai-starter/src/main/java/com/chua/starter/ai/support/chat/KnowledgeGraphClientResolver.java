package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;

/**
 * 知识图谱 LLM 客户端配置解析器。
 * <p>
 * 优先从 knowledge_graph 组读取专有配置，LLM 连接信息（host/apiKey 等）
 * 回退到 runtime 组。
 *
 */
public final class KnowledgeGraphClientResolver {

    /** 分组 */
    private static final String GROUP = "knowledge_graph";
    /** Runtime_group */
    private static final String RUNTIME_GROUP = "runtime";
    /** Default_timeout_millis */
    private static final long DEFAULT_TIMEOUT_MILLIS = 60_000L;

    /** 创建 KnowledgeGraphClientResolver 实例 */
    private KnowledgeGraphClientResolver() {}

    /** 解析 */
    public static KnowledgeGraphSettings resolve(SysAiGlobalSettingService service) {
        String provider = firstNonBlank(
                getValue(service, GROUP, "llm_provider"),
                getValue(service, RUNTIME_GROUP, "provider"),
                "openai"
        );
        String model = firstNonBlank(
                getValue(service, GROUP, "llm_model"),
                getValue(service, RUNTIME_GROUP, "default_model")
        );

        ChatClientSetting baseSetting = buildRuntimeSetting(service, provider);

        return KnowledgeGraphSettings.builder()
                .provider(provider)
                .model(model)
                .temperature(getDouble(service, GROUP, "temperature"))
                .maxTokens(getInteger(service, GROUP, "max_tokens"))
                .extractionPromptTemplate(getValue(service, GROUP, "extraction_prompt_template"))
                .relationExtractionEnabled(getBoolean(service, GROUP, "relation_extraction_enabled", true))
                .maxEntitiesPerDoc(getInteger(service, GROUP, "max_entities_per_doc", 50))
                .baseSetting(baseSetting)
                .build();
    }

    /** 构建RuntimeSetting */
    private static ChatClientSetting buildRuntimeSetting(SysAiGlobalSettingService service, String provider) {
        ChatClientSetting setting = ChatClientSetting.builder()
                .provider(provider)
                .baseUrl(firstNonBlank(
                        getValue(service, RUNTIME_GROUP, "provider_base_url"),
                        getValue(service, RUNTIME_GROUP, "provider_api_address")
                ))
                .appKey(firstNonBlank(
                        getValue(service, RUNTIME_GROUP, "provider_api_key"),
                        getValue(service, RUNTIME_GROUP, "provider_app_key")
                ))
                .appSecret(firstNonBlank(
                        getValue(service, RUNTIME_GROUP, "provider_app_secret"),
                        getValue(service, RUNTIME_GROUP, "provider_secret_key")
                ))
                .build();
        return setting;
    }

    /** 获取Value */
    private static String getValue(SysAiGlobalSettingService service, String group, String field) {
        return service.getValue(group, field).orElse(null);
    }

    /** 获取Boolean */
    private static boolean getBoolean(SysAiGlobalSettingService service, String group, String field, boolean defaultValue) {
        return service.getBoolean(group, field, defaultValue);
    }

    /** 获取Integer */
    private static Integer getInteger(SysAiGlobalSettingService service, String group, String field) {
        return getInteger(service, group, field, null);
    }

    /** 获取Integer */
    private static Integer getInteger(SysAiGlobalSettingService service, String group, String field, Integer defaultValue) {
        String value = getValue(service, group, field);
        if (StringUtils.isBlank(value)) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /** 获取Double */
    private static Double getDouble(SysAiGlobalSettingService service, String group, String field) {
        String value = getValue(service, group, field);
        if (StringUtils.isBlank(value)) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** FirstNonBlank */
    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) return value;
        }
        return null;
    }
}
