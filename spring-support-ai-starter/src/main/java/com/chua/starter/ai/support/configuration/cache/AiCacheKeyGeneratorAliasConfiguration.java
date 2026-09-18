package com.chua.starter.ai.support.configuration.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;

/**
* AI 缓存 钥匙 生成器别名配置。
*
* <p>为 AI 模块提供专用的缓存 Key 生成器，支持按模型、provider、用户等维度
* 生成缓存 钥匙，避免不同调用场景的缓存冲突。
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@AutoConfiguration
public class AiCacheKeyGeneratorAliasConfiguration {

    /**
    * AI 缓存 钥匙 生成器。
    * <p>生成格式：{@code ai:cache:{model}:{provider}:{hash}}</p>
    *
    * @return KeyGenerator 实例
    */
    @Bean("aiCacheKeyGenerator")
    public KeyGenerator aiCacheKeyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder("ai:cache:");
            if (params != null && params.length > 0) {
                for (Object param : params) {
                    if (param != null) {
                        sb.append(param.hashCode()).append(":");
                    }
                }
            }
            sb.append(method.getName());
            return sb.toString();
        };
    }
}
