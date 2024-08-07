package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.codec.CodecFactory;
import com.chua.starter.common.support.properties.CodecProperties;
import com.chua.starter.common.support.provider.CodecProvider;
import com.chua.starter.common.support.result.CodeResponseBodyAdvice;
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

    @Bean("codeResponseBodyAdvice")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.codec.enable", havingValue = "true", matchIfMissing = false)
    public CodeResponseBodyAdvice codeResponseBodyAdvice(CodecFactory codecFactory) {
        return new CodeResponseBodyAdvice(codecFactory);
    }

    @Bean("codecFactory")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.codec.enable", havingValue = "true", matchIfMissing = false)
    public CodecFactory codecFactory() {
        return new CodecFactory(codecProperties);
    }
    @Bean("codecProvider")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.codec.enable", havingValue = "true", matchIfMissing = false)
    public CodecProvider codecProvider() {
        return new CodecProvider();
    }
}
