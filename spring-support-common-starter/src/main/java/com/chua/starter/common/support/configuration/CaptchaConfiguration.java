package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.properties.CaptchaProperties;
import com.chua.starter.common.support.provider.CaptchaProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 校验码配置
 *
 * @author CH
 */
@ConditionalOnProperty(prefix = CaptchaProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaConfiguration {


    @Bean
    @ConditionalOnMissingBean
    public CaptchaProvider captchaProvider() {
        return new CaptchaProvider();
    }
}
