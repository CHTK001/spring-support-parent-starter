package com.chua.starter.server.support.ai;

import com.chua.starter.ai.support.registry.ChatClientRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 发布管理 AI 能力装配。
 *
 * <p>{@code @ConditionalOnClass} 用字符串写类名：server-starter 对 ai-starter 是
 * optional 依赖，宿主没装时这个配置类连同 {@link RegistryReleaseAiCapability}
 * 都不会被加载，发布模块照常启动。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Configuration
@ConditionalOnClass(name = "com.chua.starter.ai.support.registry.ChatClientRegistry")
public class ReleaseAiConfiguration {

    /**
     * 注册 AI 能力门面。
     *
     * @param registry ai-starter 的对话客户端注册中心
     * @return 能力门面
     */
    @Bean
    @ConditionalOnMissingBean(ReleaseAiCapability.class)
    public ReleaseAiCapability releaseAiCapability(ChatClientRegistry registry) {
        return new RegistryReleaseAiCapability(registry);
    }
}
