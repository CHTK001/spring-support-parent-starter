package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.codec.CodecFactory;
import com.chua.starter.common.support.properties.CodecProperties;
import com.chua.starter.common.support.provider.CodecProvider;
import com.chua.starter.common.support.result.CodecRequestBodyAdvice;
import com.chua.starter.common.support.result.CodecResponseBodyAdvice;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 编解码器配置
 *
 * @author CH
 */
@EnableConfigurationProperties({
        CodecProperties.class
})
@RequiredArgsConstructor
public class CodecConfiguration {

    final CodecProperties codecProperties;
    /**
     * 在满足条件时，创建一个处理响应体的Bean，用于编码相关操作
     *
     * @param codecFactory 编码工厂，用于生产编码器
     * @return 返回一个CodecResponseBodyAdvice实例，用于处理响应体的编码
     */
    @Bean("codeResponseBodyAdvice")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.codec.enable", havingValue = "true", matchIfMissing = false)
    public CodecResponseBodyAdvice codeResponseBodyAdvice(CodecFactory codecFactory) {
        return new CodecResponseBodyAdvice(codecFactory);
    }

    /**
     * 在满足条件时，创建一个处理请求体的Bean，用于编码相关操作
     *
     * @param codecFactory 编码工厂，用于生产编码器
     * @return 返回一个CodecRequestBodyAdvice实例，用于处理请求体的编码
     */
    @Bean("codecRequestBodyAdvice")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.codec.enable", havingValue = "true", matchIfMissing = false)
    public CodecRequestBodyAdvice codecRequestBodyAdvice(CodecFactory codecFactory) {
        return new CodecRequestBodyAdvice(codecFactory);
    }

    /**
     * 在满足条件时，创建一个编码工厂的Bean
     *
     * @return 返回一个CodecFactory实例，用于生产编码器
     */
    @Bean("codecFactory")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.codec.enable", havingValue = "true", matchIfMissing = false)
    public CodecFactory codecFactory() {
        return new CodecFactory(codecProperties);
    }

    /**
     * 在满足条件时，创建一个编码提供者的Bean
     *
     * @return 返回一个CodecProvider实例，用于提供编码服务
     */
    @Bean("codecProvider")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.codec.enable", havingValue = "true", matchIfMissing = false)
    public CodecProvider codecProvider() {
        return new CodecProvider();
    }
}
