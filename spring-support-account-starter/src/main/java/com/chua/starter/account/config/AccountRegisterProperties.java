package com.chua.starter.account.config;

import com.chua.starter.account.CaptchaSolver;
import com.chua.starter.account.impl.YesCaptchaSolver;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 账号注册配置。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "plugin.account.register")
public class AccountRegisterProperties {

    /**
     * 是否启用自动注册
     */
    private boolean enable = false;

    /**
     * YesCaptcha API Key
     */
    private String yescaptchaKey;

    /**
     * 默认邮箱提供商（fce/mail-tm/luckmail）
     */
    private String emailProvider = "fce";

    /**
     * 代理地址
     */
    private String proxy;

    /**
     * 启动时预注册数量
     */
    private int autoRegisterCount = 0;

    /**
     * 补号间隔（秒）
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
