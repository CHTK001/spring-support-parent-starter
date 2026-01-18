package com.chua.starter.tencent.support;

import com.chua.starter.tencent.support.properties.TencentMiniAppProperties;
import com.chua.tencent.support.Tencent;
import com.chua.tencent.support.TencentSetting;
import com.chua.tencent.support.miniapp.TencentMaHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 腾讯配置
 *
 * @author CH
 * @since 2024/12/2
 */
@Slf4j
@EnableConfigurationProperties(TencentMiniAppProperties.class)
@ConditionalOnProperty(prefix = "plugin.tencent.mini-app", name = "enable", havingValue = "true", matchIfMissing = false)
public class TencentConfiguration {

    /**
     * 小程序处理器
     *
     * @param properties 腾讯配置
     * @return 小程序处理器
     */
    @Bean
    @ConditionalOnProperty(name = "plugin.tencent.mini-app.enabled", havingValue = "true")
    public TencentMaHandler tencentMaHandler(TencentMiniAppProperties properties) {
        log.info("[腾讯配置][小程序] 加载配置");
        log.info("[腾讯配置][小程序] appId => {}", properties.getAppId());
        log.info("[腾讯配置][小程序] appSecret => {}", properties.getAppSecret());
        var tencent = new Tencent(TencentSetting.builder()
                .appId(properties.getAppId())
                .appSecret(properties.getAppSecret())
                .build());
        return (TencentMaHandler) tencent.getHandler(Tencent.Type.MINI_APP);
    }
}
