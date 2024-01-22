package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.properties.CodecProperties;
import com.chua.starter.common.support.provider.CodecProvider;
import com.chua.starter.common.support.result.CodeResponseBodyAdvice;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.annotation.Resource;

/**
 * 编解码器配置
 *
 * @author CH
 */
@EnableConfigurationProperties({
        CodecProperties.class
})
public class CodecConfiguration {

    @Resource
    private CodecProperties codecProperties;

    @Bean("codeResponseBodyAdvice")
    @ConditionalOnMissingBean()
    public CodeResponseBodyAdvice codeResponseBodyAdvice(CodecProvider codecProvider) {
        return new CodeResponseBodyAdvice(codecProvider);
    }
    @Bean("codecProvider")
    @ConditionalOnMissingBean()
    public CodecProvider codecProvider() {
        return new CodecProvider(codecProperties);
    }
}
