package com.chua.starter.ai.support.configuration;

import com.chua.starter.ai.support.chat.AiChat;
import com.chua.starter.ai.support.properties.AiProperties;
import com.chua.starter.ai.support.service.AiService;
import com.chua.starter.ai.support.service.AsyncAiService;
import com.chua.starter.ai.support.service.ReactiveAiService;
import com.chua.starter.ai.support.service.impl.DefaultAiService;
import com.chua.starter.ai.support.service.impl.DefaultAsyncAiService;
import com.chua.starter.ai.support.service.impl.DefaultReactiveAiService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import reactor.core.publisher.Mono;

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
     * 注册AI服务
     *
     * @param aiProperties AI配置属性
     * @return AI服务实例
     */
    @Bean
    @ConditionalOnMissingBean(AiService.class)
    @Lazy
    public AiService aiService(AiProperties aiProperties) {
        return new DefaultAiService(aiProperties);
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
     * 注册AiChat工厂Bean
     * <p>
     * 用于创建链式调用的AI聊天对象
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
