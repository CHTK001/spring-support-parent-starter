package com.chua.starter.tencent.support;

import com.chua.starter.tencent.support.factory.TencentWechatPayClientFactory;
import com.chua.starter.tencent.support.properties.TencentMiniAppProperties;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 腾讯配置
 *
 * @author CH
 * @since 2024/12/2
 */
@Slf4j
@EnableConfigurationProperties({TencentMiniAppProperties.class, TencentWechatPayProperties.class})
public class TencentConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TencentWechatPayClientFactory tencentWechatPayClientFactory() {
        return new TencentWechatPayClientFactory();
    }
}
