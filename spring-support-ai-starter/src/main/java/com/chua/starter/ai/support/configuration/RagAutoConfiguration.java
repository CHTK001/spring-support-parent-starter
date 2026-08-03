package com.chua.starter.ai.support.configuration;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.rag.RagClient;
import com.chua.common.support.ai.rag.RagClientSetting;
import com.chua.common.support.ai.rag.VectorService;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.controller.RagController;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import java.util.Set;

/**
 * RAG 模块自动装配——使用 common-starter 的 RagClient + RagDocumentLifeCycle 真实实现。
 *
 * @author CH
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = RagProperties.PRE, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RagProperties.class)
public class RagAutoConfiguration {

    /**
     * 创建 RagClient——通过 SPI 加载 MemoryRagClient 等实现。
     * <p>依赖 common-starter 中通过 META-INF/extensions 注册的 SPI 提供者。
     */
    @Bean
    @ConditionalOnMissingBean
    public RagClient ragClient(RagProperties properties, SysAiGlobalSettingService globalSettingService) {
        // 从 sys_ai_global_setting 读取 LLM 配置
        ChatClient chatClient = createChatClient(globalSettingService);
        if (chatClient == null) {
            log.warn("[RagAutoConfiguration] 未配置 ChatClient，跳过 RagClient 创建");
            return null;
        }

        // 构建 RagClient 配置
        RagClientSetting setting = RagClientSetting.builder()
                .topK(properties.getDefaultTopK())
                .similarityThreshold(properties.getDefaultSimilarityThreshold())
                .uploadDir(properties.getUploadDir())
                .chatClient(chatClient)
                .build();

        // SPI 加载 RagClient（如 MemoryRagClient）
        RagClient client = RagClient.create("memory", setting);
        if (client != null) {
            log.info("[RagAutoConfiguration] RagClient 初始化完成: provider=memory");
            return client;
        }

        // 尝试其他 SPI 实现
        Set<String> providers = ServiceProvider.of(RagClient.class).getExtensions();
        for (String provider : providers) {
            if ("memory".equals(provider)) {
                continue; // 已经尝试过
            }
            try {
                client = RagClient.create(provider, setting);
                if (client != null) {
                    log.info("[RagAutoConfiguration] RagClient 初始化完成: provider={}", provider);
                    return client;
                }
            } catch (Exception e) {
                log.debug("[RagAutoConfiguration] 尝试 RagClient provider={} 失败: {}", provider, e.getMessage());
            }
        }

        log.error("[RagAutoConfiguration] 无可用 RagClient SPI 实现，RAG 功能不可用");
        return null;
    }

    /**
     * 创建 RagController。
     */
    @Bean
    @ConditionalOnMissingBean
    public RagController ragController(RagClient ragClient,
                                        RagProperties properties) {
        return new RagController(ragClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalSettingInitializer globalSettingInitializer(SysAiGlobalSettingService globalSettingService) {
        return new GlobalSettingInitializer(globalSettingService);
    }

    /**
     * 从 sys_ai_global_setting runtime 组读取 LLM 配置，创建 ChatClient。
     */
    private ChatClient createChatClient(SysAiGlobalSettingService settingService) {
        String provider = settingService.getValue("runtime", "provider").orElse("");
        String baseUrl = settingService.getValue("runtime", "provider_base_url").orElse("");
        String apiKey = settingService.getValue("runtime", "provider_api_key").orElse("");
        String defaultModel = settingService.getValue("runtime", "default_model").orElse("");

        if (provider.isBlank()) {
            log.warn("[RagAutoConfiguration] 全局设置中未配置 LLM provider，跳过 ChatClient 注入");
            return null;
        }

        try {
            ChatClientSetting setting = ChatClientSetting.builder()
                    .provider(provider)
                    .baseUrl(baseUrl)
                    .appKey(apiKey)
                    .build();
            ChatClient client = ChatClient.create(setting);
            if (!defaultModel.isBlank()) {
                client.model(defaultModel);
            }
            return client;
        } catch (Exception e) {
            log.warn("[RagAutoConfiguration] 创建 ChatClient 失败: provider={}, error={}", provider, e.getMessage());
            return null;
        }
    }
}
