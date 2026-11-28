package com.chua.starter.sso.service;

import com.chua.starter.oauth.client.support.provider.ThirdLoginData;
import com.chua.starter.sso.support.SsoAuthRequest;

/**
 * SSO 设置读取契约。
 *
 * <p>SSO 核心只依赖本窄接口，不依赖系统设置表实体、Mapper 或 API 层实现；
 * 宿主提供实现后即可复用第三方登录编排。</p>
 */
public interface SsoSettingService {

    /**
     * 读取第三方登录配置并创建认证请求。
     *
     * @param loginData 第三方登录请求
     * @return 认证请求与状态缓存；未配置或未启用时返回 {@code null}
     */
    SsoAuthRequest getAuthRequest(ThirdLoginData loginData);

    /**
     * 判断指定平台的三方登录开关是否开启。
     *
     * @param platform 平台标识
     * @return 开启返回 {@code true}；缺失、停用或异常均返回 {@code false}
     */
    boolean isThirdPartyEnabled(String platform);

    /**
     * 判断指定平台的自动注册开关是否开启。
     *
     * @param platform 平台标识
     * @return 开启返回 {@code true}；缺失、停用或异常均返回 {@code false}
     */
    boolean isAutoRegisterEnabled(String platform);
}
