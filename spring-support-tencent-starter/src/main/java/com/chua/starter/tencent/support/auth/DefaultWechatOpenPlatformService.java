package com.chua.starter.tencent.support.auth;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认微信开放平台服务
 *
 * @since 2024/04/08
 * @author CH
 */
@Slf4j
public class DefaultWechatOpenPlatformService implements WechatOpenPlatformService {

    /**
     * 创建 DefaultWechatOpenPlatformService 实例
     * @param componentAppId componentAppId
     * @param String String
     * @param String String
     */
    public DefaultWechatOpenPlatformService(String componentAppId, String componentAppSecret, String storagePath) {
        log.info("初始化默认微信开放平台服务: componentAppId={}, storagePath={}", componentAppId, storagePath);
    }
}