package com.chua.starter.sso.support;

import me.zhyd.oauth.cache.AuthStateCache;
import me.zhyd.oauth.request.AuthRequest;

/**
 * SSO 认证请求载体。
 *
 * @param request JustAuth 认证请求
 * @param stateCache 状态缓存
 * @param state 本次认证状态
 */
public record SsoAuthRequest(AuthRequest request, AuthStateCache stateCache, String state) {
}
