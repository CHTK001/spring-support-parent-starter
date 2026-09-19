package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.api.properties.ApiProperties;
import com.chua.starter.common.support.properties.CaptchaProperties;
import com.chua.starter.common.support.provider.CaptchaProvider;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 校验码配置类
 * <p>
 * 用于配置验证码相关的Bean。
 * </p>
 *
 * @版本 1.0.0
 * @author CH
 * @since 4.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "plugin.captcha", name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({CaptchaProperties.class})
public class CaptchaConfiguration {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(CaptchaConfiguration.class);


    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = CaptchaProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
    /**
     * captcha提供者
     *
     * @return captcha提供者的结果
     */
    public CaptchaProvider captchaProvider() {
        ServiceInfoRegistry.getInstance().registerFlag(
                "captcha", "校验码", true, "图形验证码接口", null);
        /**
         * captcha提供者。
         * @return captcha提供者的结果
         */
        return new CaptchaProvider();
    }
}
