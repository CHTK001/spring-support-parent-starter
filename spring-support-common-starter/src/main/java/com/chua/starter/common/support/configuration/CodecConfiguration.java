package com.chua.starter.common.support.configuration;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CodecKeyPair;
import com.chua.common.support.utils.Hex;
import com.chua.starter.common.support.properties.CodecProperties;
import com.chua.starter.common.support.result.CodeResponseBodyAdvice;
import org.springframework.beans.factory.InitializingBean;
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
public class CodecConfiguration implements InitializingBean {

    @Resource
    private CodecProperties codecProperties;

    @Override
    public void afterPropertiesSet() throws Exception {
        if(codecProperties.isEnable()) {
            Codec codec = Codec.build(codecProperties.getCodecType());
            if(codec instanceof CodecKeyPair) {
                CodecKeyPair codecKeyPair = (CodecKeyPair) codec;
                codecProperties.setPrivateKey(Hex.encodeHexString(codecKeyPair.getPrivateKey().getEncoded()));
                codecProperties.setPublicKey(Hex.encodeHexString(codecKeyPair.getPublicKey().getEncoded()));
            }
        }
    }

//    @Bean("codecRequestHandler")
//    @ConditionalOnMissingBean()
//    public FilterRegistrationBean<CodecRequestFilter> codecRequestHandler() {
//        FilterRegistrationBean<CodecRequestFilter> registration = new FilterRegistrationBean<>();
//        registration.setFilter(new CodecRequestFilter(codecProperties));
//        registration.addUrlPatterns("/*");
//        registration.setName("codecRequestHandler");
//        //设置优先级别
//        registration.setOrder(1);
//        return registration;
//    }
    @Bean("codeResponseBodyAdvice")
    @ConditionalOnMissingBean()
    public CodeResponseBodyAdvice codeResponseBodyAdvice() {
        return new CodeResponseBodyAdvice(codecProperties);
    }
}
