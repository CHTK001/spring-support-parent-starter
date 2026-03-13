package com.chua.starter.ai.support.configuration;

import com.chua.deeplearning.support.api.AiClient;
import com.chua.deeplearning.support.config.AiClientConfig;
import com.chua.deeplearning.support.config.FaceConfig;
import com.chua.deeplearning.support.config.LlmConfig;
import com.chua.deeplearning.support.config.OcrConfig;
import com.chua.starter.ai.support.chat.AiChat;
import com.chua.starter.ai.support.engine.FaceClient;
import com.chua.starter.ai.support.engine.FaceEngine;
import com.chua.starter.ai.support.engine.impl.AiChatFaceEngine;
import com.chua.starter.ai.support.engine.impl.DefaultFaceClient;
import com.chua.starter.ai.support.properties.AiProperties;
import com.chua.starter.ai.support.properties.ProviderProperties;
import com.chua.starter.ai.support.service.AiService;
import com.chua.starter.ai.support.service.AsyncAiService;
import com.chua.starter.ai.support.service.ReactiveAiService;
import com.chua.starter.ai.support.service.impl.DefaultAiService;
import com.chua.starter.ai.support.service.impl.DefaultAsyncAiService;
import com.chua.starter.ai.support.service.impl.DefaultReactiveAiService;
import com.chua.starter.common.support.application.ModuleEnvironmentRegistration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * AI模块自动配置类
 *
 * @author CH
 * @since 2024-01-01
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = AiProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiConfiguration {

    /**
     * 注册 AiClient Bean
     *
     * @param aiProperties AI 配置属性
     * @return AiClient 实例
     */
    @Bean
    @ConditionalOnMissingBean(AiClient.class)
    @ConditionalOnClass(name = "com.chua.deeplearning.support.api.AiClient")
    @Lazy
    public AiClient aiClient(AiProperties aiProperties) {
        AiClientConfig config = buildAiClientConfig(aiProperties);
        return AiClient.builder()
                .config(config)
                .build();
    }

    /**
     * 构建 AiClientConfig
     *
     * @param properties AI 配置属性
     * @return AiClientConfig
     */
    private AiClientConfig buildAiClientConfig(AiProperties properties) {
        // 验证配置
        validateConfiguration(properties);
        
        AiClientConfig config = new AiClientConfig();
        
        // 设置 LLM 配置
        if (properties.getLlm() != null && properties.getLlm().isEnabled()) {
            config.setLlmConfig(buildLlmConfig(properties));
        }
        
        // 设置 OCR 配置
        if (properties.getOcr() != null && properties.getOcr().isEnabled()) {
            config.setOcrConfig(buildOcrConfig(properties));
        }
        
        // 设置 Face 配置
        if (properties.getFace() != null && properties.getFace().isEnabled()) {
            config.setFaceConfig(buildFaceConfig(properties));
        }
        
        return config;
    }

    /**
     * 验证配置
     *
     * @param properties AI 配置属性
     */
    private void validateConfiguration(AiProperties properties) {
        // 验证 LLM 配置
        if (properties.getLlm() != null && properties.getLlm().isEnabled()) {
            validateLlmConfig(properties.getLlm(), properties);
        }
        
        // 验证 OCR 配置
        if (properties.getOcr() != null && properties.getOcr().isEnabled()) {
            validateOcrConfig(properties.getOcr(), properties);
        }
        
        // 验证 Face 配置
        if (properties.getFace() != null && properties.getFace().isEnabled()) {
            validateFaceConfig(properties.getFace(), properties);
        }
    }

    /**
     * 验证 LLM 配置
     *
     * @param llm LLM 配置
     * @param properties AI 配置属性
     */
    private void validateLlmConfig(com.chua.starter.ai.support.properties.LlmProperties llm, AiProperties properties) {
        String provider = llm.getProvider() != null ? llm.getProvider() : properties.getDefaultProvider();
        if (provider == null) {
            throw new IllegalArgumentException("LLM provider must be specified");
        }
        
        // 验证云厂商配置
        if (properties.getProviders().containsKey(provider)) {
            ProviderProperties providerProps = properties.getProviders().get(provider);
            validateProviderConfig(provider, providerProps);
        }
    }

    /**
     * 验证 OCR 配置
     *
     * @param ocr OCR 配置
     * @param properties AI 配置属性
     */
    private void validateOcrConfig(com.chua.starter.ai.support.properties.OcrProperties ocr, AiProperties properties) {
        String provider = ocr.getProvider() != null ? ocr.getProvider() : properties.getDefaultProvider();
        if (provider == null) {
            throw new IllegalArgumentException("OCR provider must be specified");
        }
        
        // 验证云厂商配置
        if (properties.getProviders().containsKey(provider)) {
            ProviderProperties providerProps = properties.getProviders().get(provider);
            validateProviderConfig(provider, providerProps);
        }
    }

    /**
     * 验证 Face 配置
     *
     * @param face Face 配置
     * @param properties AI 配置属性
     */
    private void validateFaceConfig(com.chua.starter.ai.support.properties.FaceProperties face, AiProperties properties) {
        String provider = face.getProvider() != null ? face.getProvider() : properties.getDefaultProvider();
        if (provider == null) {
            throw new IllegalArgumentException("Face provider must be specified");
        }
        
        // 验证阈值范围
        if (face.getConfidenceThreshold() != null && (face.getConfidenceThreshold() < 0 || face.getConfidenceThreshold() > 1)) {
            throw new IllegalArgumentException("Face confidence threshold must be between 0 and 1");
        }
        
        if (face.getNmsThreshold() != null && (face.getNmsThreshold() < 0 || face.getNmsThreshold() > 1)) {
            throw new IllegalArgumentException("Face NMS threshold must be between 0 and 1");
        }
        
        // 验证云厂商配置
        if (properties.getProviders().containsKey(provider)) {
            ProviderProperties providerProps = properties.getProviders().get(provider);
            validateProviderConfig(provider, providerProps);
        }
    }

    /**
     * 验证云厂商配置
     *
     * @param provider 提供商名称
     * @param providerProps 提供商配置
     */
    private void validateProviderConfig(String provider, ProviderProperties providerProps) {
        // 根据不同提供商验证必需的配置项
        switch (provider.toLowerCase()) {
            case "openai":
                if (providerProps.getApiKey() == null) {
                    throw new IllegalArgumentException("OpenAI provider requires apiKey");
                }
                break;
            case "baidu":
                if (providerProps.getApiKey() == null || providerProps.getSecretKey() == null) {
                    throw new IllegalArgumentException("Baidu provider requires apiKey and secretKey");
                }
                break;
            case "aliyun":
                if (providerProps.getAppKey() == null) {
                    throw new IllegalArgumentException("Aliyun provider requires appKey");
                }
                break;
            case "tencent":
                if (providerProps.getAppId() == null || providerProps.getAppSecret() == null) {
                    throw new IllegalArgumentException("Tencent provider requires appId and appSecret");
                }
                break;
            case "local":
                if (providerProps.getModelPath() == null) {
                    throw new IllegalArgumentException("Local provider requires modelPath");
                }
                break;
            // 其他提供商可以添加更多验证规则
        }
    }

    /**
     * 构建 LlmConfig
     *
     * @param properties AI 配置属性
     * @return LlmConfig
     */
    private LlmConfig buildLlmConfig(AiProperties properties) {
        LlmConfig config = new LlmConfig();
        var llm = properties.getLlm();
        
        config.setProvider(llm.getProvider() != null ? llm.getProvider() : properties.getDefaultProvider());
        config.setModel(llm.getModel());
        config.setTemperature(llm.getTemperature());
        config.setMaxTokens(llm.getMaxTokens());
        config.setTimeout(llm.getTimeout());
        
        // 从 providers 中获取云厂商配置
        String provider = config.getProvider();
        if (provider != null && properties.getProviders().containsKey(provider)) {
            ProviderProperties providerProps = properties.getProviders().get(provider);
            applyProviderConfig(config, providerProps);
        }
        
        // MCP 配置
        if (llm.getMcp() != null) {
            config.setMcpEnabled(llm.getMcp().isEnabled());
        }
        
        return config;
    }

    /**
     * 构建 OcrConfig
     *
     * @param properties AI 配置属性
     * @return OcrConfig
     */
    private OcrConfig buildOcrConfig(AiProperties properties) {
        OcrConfig config = new OcrConfig();
        var ocr = properties.getOcr();
        
        config.setProvider(ocr.getProvider() != null ? ocr.getProvider() : properties.getDefaultProvider());
        config.setLanguage(ocr.getLanguage());
        config.setTimeout(ocr.getTimeout());
        
        // 从 providers 中获取云厂商配置
        String provider = config.getProvider();
        if (provider != null && properties.getProviders().containsKey(provider)) {
            ProviderProperties providerProps = properties.getProviders().get(provider);
            applyProviderConfig(config, providerProps);
        }
        
        return config;
    }

    /**
     * 构建 FaceConfig
     *
     * @param properties AI 配置属性
     * @return FaceConfig
     */
    private FaceConfig buildFaceConfig(AiProperties properties) {
        FaceConfig config = new FaceConfig();
        var face = properties.getFace();
        
        config.setProvider(face.getProvider() != null ? face.getProvider() : properties.getDefaultProvider());
        config.setConfidenceThreshold(face.getConfidenceThreshold());
        config.setNmsThreshold(face.getNmsThreshold());
        config.setTimeout(face.getTimeout());
        
        // 从 providers 中获取云厂商配置
        String provider = config.getProvider();
        if (provider != null && properties.getProviders().containsKey(provider)) {
            ProviderProperties providerProps = properties.getProviders().get(provider);
            applyProviderConfig(config, providerProps);
        }
        
        return config;
    }

    /**
     * 应用云厂商配置到目标配置对象
     *
     * @param target 目标配置对象
     * @param provider 云厂商配置
     */
    private void applyProviderConfig(Object target, ProviderProperties provider) {
        if (target instanceof LlmConfig llmConfig) {
            llmConfig.setAppId(provider.getAppId());
            llmConfig.setAppKey(provider.getAppKey());
            llmConfig.setAppSecret(provider.getAppSecret());
            llmConfig.setApiKey(provider.getApiKey());
            llmConfig.setSecretKey(provider.getSecretKey());
            llmConfig.setApiAddress(provider.getApiAddress());
            llmConfig.setBaseUrl(provider.getBaseUrl());
            llmConfig.setHost(provider.getHost());
            llmConfig.setRegion(provider.getRegion());
            llmConfig.setModelPath(provider.getModelPath());
            llmConfig.setDevice(provider.getDevice());
            llmConfig.setThreads(provider.getThreads());
        } else if (target instanceof OcrConfig ocrConfig) {
            ocrConfig.setAppId(provider.getAppId());
            ocrConfig.setAppKey(provider.getAppKey());
            ocrConfig.setAppSecret(provider.getAppSecret());
            ocrConfig.setApiKey(provider.getApiKey());
            ocrConfig.setSecretKey(provider.getSecretKey());
            ocrConfig.setApiAddress(provider.getApiAddress());
            ocrConfig.setBaseUrl(provider.getBaseUrl());
            ocrConfig.setHost(provider.getHost());
        } else if (target instanceof FaceConfig faceConfig) {
            faceConfig.setAppId(provider.getAppId());
            faceConfig.setAppKey(provider.getAppKey());
            faceConfig.setAppSecret(provider.getAppSecret());
            faceConfig.setApiKey(provider.getApiKey());
            faceConfig.setSecretKey(provider.getSecretKey());
            faceConfig.setApiAddress(provider.getApiAddress());
            faceConfig.setBaseUrl(provider.getBaseUrl());
            faceConfig.setHost(provider.getHost());
        }
    }

    /**
     * 注册AI服务
     *
     * @param aiProperties AI配置属性
     * @param aiClient AiClient实例
     * @return AI服务实例
     */
    @Bean
    @ConditionalOnMissingBean(AiService.class)
    @Lazy
    public AiService aiService(AiProperties aiProperties, AiClient aiClient) {
        return new DefaultAiService(aiProperties, aiClient);
    }

    /**
     * 注册异步AI服务
     *
     * @param aiService AI服务实例
     * @return 异步AI服务实例
     */
    @Bean
    @ConditionalOnMissingBean(AsyncAiService.class)
    @Lazy
    public AsyncAiService asyncAiService(AiService aiService) {
        return new DefaultAsyncAiService(aiService);
    }

    /**
     * 注册Reactor响应式AI服务
     *
     * @param aiService AI服务实例
     * @return 响应式AI服务实例
     */
    @Bean
    @ConditionalOnMissingBean(ReactiveAiService.class)
    @ConditionalOnClass(Mono.class)
    @Lazy
    public ReactiveAiService reactiveAiService(AiService aiService) {
        return new DefaultReactiveAiService(aiService);
    }

    /**
     * 注册人脸识别引擎
     *
     * @param aiService AI服务实例
     * @return 人脸识别引擎实例
     */
    @Bean
    @ConditionalOnMissingBean(FaceEngine.class)
    @Lazy
    public FaceEngine faceEngine(AiService aiService) {
        return new AiChatFaceEngine(aiService);
    }

    /**
     * 注册人脸识别客户端
     *
     * @param faceEngine 人脸识别引擎实例
     * @return 人脸识别客户端实例
     */
    @Bean
    @ConditionalOnMissingBean(FaceClient.class)
    @Lazy
    public FaceClient faceClient(FaceEngine faceEngine) {
        return new DefaultFaceClient(faceEngine, Schedulers.boundedElastic());
    }

    /**
     * 注册 AI 配置到全局环境
     *
     * @param aiProperties AI 配置属性
     * @return 模块环境注册器
     */
    @Bean
    public ModuleEnvironmentRegistration aiModuleEnvironment(AiProperties aiProperties) {
        return new ModuleEnvironmentRegistration(AiProperties.PREFIX, aiProperties, aiProperties.isEnabled());
    }

    /**
     * 注册AiChat工厂Bean
     *
     * @param aiService         AI服务实例
     * @param asyncAiService    异步AI服务实例
     * @param reactiveAiService 响应式AI服务实例（可选）
     * @return AiChat工厂实例
     */
    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public AiChatFactory aiChatFactory(AiService aiService, AsyncAiService asyncAiService,
                                       org.springframework.beans.factory.ObjectProvider<ReactiveAiService> reactiveAiService) {
        return new AiChatFactory(aiService, asyncAiService, reactiveAiService.getIfAvailable());
    }

    /**
     * AiChat工厂类
     */
    public static class AiChatFactory {

        private final AiService aiService;
        private final AsyncAiService asyncAiService;
        private final ReactiveAiService reactiveAiService;

        public AiChatFactory(AiService aiService, AsyncAiService asyncAiService, ReactiveAiService reactiveAiService) {
            this.aiService = aiService;
            this.asyncAiService = asyncAiService;
            this.reactiveAiService = reactiveAiService;
        }

        /**
         * 创建AiChat实例（同步+异步模式）
         *
         * @return AiChat实例
         */
        public AiChat create() {
            return AiChat.of(aiService, asyncAiService, reactiveAiService);
        }

        /**
         * 创建AiChat实例（仅同步模式）
         *
         * @return AiChat实例
         */
        public AiChat createSync() {
            return AiChat.of(aiService);
        }

        /**
         * 获取AI服务实例
         *
         * @return AI服务实例
         */
        public AiService getAiService() {
            return aiService;
        }

        /**
         * 获取异步AI服务实例
         *
         * @return 异步AI服务实例
         */
        public AsyncAiService getAsyncAiService() {
            return asyncAiService;
        }

        /**
         * 获取响应式AI服务实例
         *
         * @return 响应式AI服务实例（可能为null）
         */
        public ReactiveAiService getReactiveAiService() {
            return reactiveAiService;
        }
    }
}
