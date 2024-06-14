package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.properties.CaptchaProperties;
import com.chua.starter.common.support.properties.OptionalProperties;
import com.chua.starter.common.support.provider.CaptchaProvider;
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
@ConditionalOnProperty(prefix = CaptchaProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({CaptchaProperties.class, OptionalProperties.class})
public class CaptchaConfiguration {


    @Bean
    @ConditionalOnMissingBean
    public CaptchaProvider captchaProvider() {
        log.info(">>>>>>> 开启检验码接口");
        return new CaptchaProvider();
    }
}
