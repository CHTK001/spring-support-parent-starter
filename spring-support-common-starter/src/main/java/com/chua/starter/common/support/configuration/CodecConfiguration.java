package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.codec.CodecFactory;
import com.chua.starter.common.support.properties.CodecProperties;
import com.chua.starter.common.support.result.CodeResponseBodyAdvice;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
    @ConditionalOnMissingBean()
    public CodeResponseBodyAdvice codeResponseBodyAdvice(CodecFactory codecFactory) {
        return new CodeResponseBodyAdvice(codecFactory);
    }
    @Bean("codecProvider")
    @ConditionalOnMissingBean()
    public CodecFactory codecProvider() {
        return new CodecFactory(codecProperties);
    }
}
