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
 * tencent配置
 * @author CH
 * @since 2024/12/2
@ConditionalOnProperty(prefix = "plugin.tencent.mini-app", name = "enable", havingValue = "true", matchIfMissing = false)
 */
@Slf4j
@EnableConfigurationProperties(TencentMiniAppProperties.class)
public class TencentConfiguration {


    /**
     * 小程序
     * @param properties tencent
     * @return TencentMaHandler
     */
    @Bean
    @ConditionalOnProperty(name = "plugin.tencent.mini-app.enabled", havingValue = "true")
    public TencentMaHandler tencentMaHandler(TencentMiniAppProperties properties) {
        log.info("加载tencent配置");
        log.info("appId => {}", properties.getAppId());
        log.info("appSecret => {}", properties.getAppSecret());
        Tencent tencent = new Tencent(TencentSetting.builder()
                .appId(properties.getAppId())
                .appSecret(properties.getAppSecret())
                .build());
        return (TencentMaHandler) tencent.getHandler(Tencent.Type.MINI_APP);
    }
}
