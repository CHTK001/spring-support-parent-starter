package com.chua.starter.tencent.support.auth;

import lombok.extern.slf4j.Slf4j;

/**
 * 微信openid转换服务
 *
 * @since 2024/04/08
 * @author CH
 */
@Slf4j
public class WechatOpenIdConvertService implements OpenIdConvertService {

    /**
     * 创建 wechat打开标识转换服务 实例
     * @param componentAppId 组件appid
     * @param componentAppId 字符串
     * @param componentAppId 字符串
     * @param componentAppSecret 组件appsecret
     * @param defaultMiniAppId 默认miniappid
     */
    public WechatOpenIdConvertService(String componentAppId, String componentAppSecret, String defaultMiniAppId) {
        log.info("初始化微信openid转换服务: componentAppId={}", componentAppId);
    }
}
