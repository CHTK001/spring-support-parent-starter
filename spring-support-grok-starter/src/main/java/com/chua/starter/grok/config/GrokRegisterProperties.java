package com.chua.starter.grok.config;

import com.chua.starter.grok.CaptchaSolver;
import com.chua.starter.grok.impl.YesCaptchaSolver;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Grok 注册配置。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "grok.register")
public class GrokRegisterProperties {

    /**
     * 是否启用自动注册
     */
    private boolean enable = false;

    /**
     * YesCaptcha API Key
     */
    private String yescaptchaKey;

    /**
     * 邮箱提供商（fce/mail-tm/luckmail）
     */
    private String emailProvider = "fce";

    /**
     * 代理地址
     */
    private String proxy;

    /**
     * 自动注册账号数量
     */
    private int autoRegisterCount = 0;

    /**
     * 自动补号间隔（秒）
     */
    private long replenishIntervalSeconds = 3600;

    @Bean
    public CaptchaSolver captchaSolver() {
        if (!enable || yescaptchaKey == null || yescaptchaKey.isBlank()) {
            return null;
        }
        return new YesCaptchaSolver(yescaptchaKey);
    }
}
