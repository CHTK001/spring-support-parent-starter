package com.chua.starter.tencent.support;

import com.chua.starter.tencent.support.properties.TencentMiniAppProperties;
import com.chua.tencent.support.Tencent;
import com.chua.tencent.support.TencentSetting;
import com.chua.tencent.support.miniapp.TencentMaHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * tencent配置
 * @author CH
 * @since 2024/12/2
 */
@EnableConfigurationProperties(TencentMiniAppProperties.class)
public class TencentConfiguration {


    /**
     * 小程序
     * @param tencent tencent
     * @return TencentMaHandler
     */
    @Bean
    @ConditionalOnProperty(name = "plugin.tencent.mini-app.enabled", havingValue = "true")
    public TencentMaHandler tencentMaHandler(TencentMiniAppProperties properties) {
        Tencent tencent = new Tencent(TencentSetting.builder()
                .appId(properties.getAppId())
                .appSecret(properties.getAppSecret())
                .build());
        return (TencentMaHandler) tencent.getHandler(Tencent.Type.MINI_APP);
    }
}
