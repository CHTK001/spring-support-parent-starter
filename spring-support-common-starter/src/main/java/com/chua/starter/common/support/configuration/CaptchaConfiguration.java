package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.properties.CaptchaProperties;
import com.chua.starter.common.support.properties.OptionalProperties;
import com.chua.starter.common.support.provider.CaptchaProvider;
import com.chua.starter.common.support.provider.OptionalProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 校验码配置
 *
 * @author CH
 */
@Slf4j
@EnableConfigurationProperties({CaptchaProperties.class, OptionalProperties.class})
public class CaptchaConfiguration {


    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = CaptchaProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
    public CaptchaProvider captchaProvider() {
        log.info(">>>>>>> 开启检验码接口");
        return new CaptchaProvider();
    }
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = OptionalProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
    public OptionalProvider optionalProvider() {
        log.info(">>>>>>> 开启选项接口");
        return new OptionalProvider();
    }
}
