package com.chua.starter.tencent.support;

import com.chua.starter.tencent.support.factory.TencentWechatPayClientFactory;
import com.chua.starter.tencent.support.properties.TencentMiniAppProperties;
import com.chua.starter.tencent.support.properties.TencentWechatOpenPlatformProperties;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.tencent.support.auth.DefaultWechatOpenPlatformService;
import com.chua.starter.tencent.support.auth.OpenIdConvertService;
import com.chua.starter.tencent.support.auth.WechatOpenIdConvertService;
import com.chua.starter.tencent.support.auth.WechatOpenPlatformService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
* 腾讯配置
*
* @since 2024/12/2
* @author CH
 */
@Slf4j
@EnableConfigurationProperties({TencentMiniAppProperties.class, TencentWechatPayProperties.class, TencentWechatOpenPlatformProperties.class})
public class TencentConfiguration {

    @Bean
    @ConditionalOnMissingBean
    /**
    * tencentwechat薪酬客户端工厂
    *
    * @return tencentwechat薪酬客户端工厂的结果
    */
    public TencentWechatPayClientFactory tencentWechatPayClientFactory() {
        /**
        * tencentwechat薪酬客户端工厂。
        * @return tencentwechat薪酬客户端工厂的结果
        */
        return new TencentWechatPayClientFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = TencentWechatOpenPlatformProperties.PREFIX, name = "enable", havingValue = "true")
    /**
    * Wechat打开platform服务
    *
    * @param properties 属性
    * @return wechat打开platform服务的结果
    */
    public WechatOpenPlatformService wechatOpenPlatformService(TencentWechatOpenPlatformProperties properties) {
        log.info("初始化微信开放平台服务, componentAppId={}, storagePath={}", properties.getComponentAppId(), properties.getStoragePath());
        return new DefaultWechatOpenPlatformService(
                properties.getComponentAppId(),
                properties.getComponentAppSecret(),
                properties.getStoragePath()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = TencentWechatOpenPlatformProperties.PREFIX, name = "enable", havingValue = "true")
    /**
    * 打开标识转换服务
    *
    * @param properties 属性
    * @return 打开id转换服务的结果
    */
    public OpenIdConvertService openIdConvertService(TencentWechatOpenPlatformProperties properties) {
        log.info("初始化微信openid转换服务, componentAppId={}", properties.getComponentAppId());
        return new WechatOpenIdConvertService(
                properties.getComponentAppId(),
                properties.getComponentAppSecret(),
                properties.getDefaultMiniAppId()
        );
    }

    @Bean
    /**
    * 腾讯云服务信息contributor
    *
    * @return tencent服务信息contributor的结果
    */
    public ServiceInfoContributor tencentServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("tencent", "腾讯", true, "腾讯服务模块");
        };
    }
}
