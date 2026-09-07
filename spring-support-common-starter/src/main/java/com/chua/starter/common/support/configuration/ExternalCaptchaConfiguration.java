package com.chua.starter.common.support.configuration;

import com.chua.captcha.support.CaptchaParser;
import com.chua.captcha.support.CaptchaRunClient;
import com.chua.captcha.support.CaptchaSetting;
import com.chua.captcha.support.YesCaptchaClient;
import com.chua.common.support.spi.ServiceProvider;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 外部验证码解析服务配置
 * <p>
 * 对接 YesCaptcha（yescaptcha.com）和 CaptchaRun（captcha-run.com）等第三方验证码解析服务。
 * 通过 {@code plugin.captcha.external.*} 配置项设置 API Token 和服务地址。
 * </p>
 *
 * @author CH
 * @since 2026-09-07
 */
@Data
@Configuration
@ConditionalOnClass(CaptchaParser.class)
@ConditionalOnProperty(prefix = "plugin.captcha.external", name = "enable", havingValue = "true", matchIfMissing = false)
@ConfigurationProperties(prefix = "plugin.captcha.external")
public class ExternalCaptchaConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ExternalCaptchaConfiguration.class);

    private boolean enable;
    private String apiToken;
    private String apiUrl = CaptchaSetting.YESCAPTCHA_API_URL;
    private long connectTimeout = 30000;
    private long readTimeout = 30000;
    private String provider = "yescaptcha";

    @Bean
    @ConditionalOnProperty(prefix = "plugin.captcha.external", name = "api-token")
    public CaptchaSetting captchaSetting() {
        return CaptchaSetting.builder()
                .apiToken(apiToken)
                .apiUrl(apiUrl)
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "plugin.captcha.external", name = "provider", havingValue = "yescaptcha", matchIfMissing = true)
    public CaptchaParser yesCaptchaParser(CaptchaSetting setting) {
        log.info("[ExternalCaptcha] 初始化 YesCaptcha 客户端, apiUrl={}, apiToken={}",
                setting.getApiUrl(),
                setting.getApiToken() != null ? setting.getApiToken().substring(0, Math.min(8, setting.getApiToken().length())) + "..." : "null");
        return new YesCaptchaClient(setting);
    }

    @Bean
    @ConditionalOnProperty(prefix = "plugin.captcha.external", name = "provider", havingValue = "captcha-run")
    public CaptchaParser captchaRunParser(CaptchaSetting setting) {
        log.info("[ExternalCaptcha] 初始化 CaptchaRun 客户端, apiUrl={}, apiToken={}",
                setting.getApiUrl(),
                setting.getApiToken() != null ? setting.getApiToken().substring(0, Math.min(8, setting.getApiToken().length())) + "..." : "null");
        return new CaptchaRunClient(setting);
    }

    public static CaptchaParser resolveCaptchaParser() {
        try {
            return ServiceProvider.of(CaptchaParser.class).getDefault();
        } catch (Exception e) {
            log.warn("[ExternalCaptcha] 无法解析验证码解析器: {}", e.getMessage());
            return null;
        }
    }
}
