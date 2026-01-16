package com.chua.starter.common.support.configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.chua.starter.common.support.api.properties.ApiProperties;
import com.chua.starter.common.support.properties.CaptchaProperties;
import com.chua.starter.common.support.properties.OptionalProperties;
import com.chua.starter.common.support.provider.CaptchaProvider;
import com.chua.starter.common.support.provider.OptionalProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 校验码配置类
 * <p>
 * 用于配置验证码和SPI选项相关的Bean。
 * </p>
 *
 * @author CH
 * @version 1.0.0
@ConditionalOnProperty(prefix = "plugin.captcha", name = "enable", havingValue = "true", matchIfMissing = false)
 */
@EnableConfigurationProperties({CaptchaProperties.class, OptionalProperties.class})
public class CaptchaConfiguration {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CaptchaConfiguration.class);


    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = CaptchaProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = false)
    public CaptchaProvider captchaProvider() {
        log.info(">>>>>>> 开启检验码接口");
        return new CaptchaProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = OptionalProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = false)
    public OptionalProvider optionalProvider(ApiProperties apiProperties) {
        log.info(">>>>>>> 开启SPI选项接口");
        return new OptionalProvider(apiProperties);
    }
}

