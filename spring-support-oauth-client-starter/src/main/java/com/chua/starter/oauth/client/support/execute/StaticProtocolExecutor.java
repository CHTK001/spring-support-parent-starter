package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.utils.ResponseUtils;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.protocol.AbstractProtocol;
import com.chua.starter.oauth.client.support.protocol.StaticProtocol;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.StaticTempLogin;
import com.chua.starter.oauth.client.support.user.UserResult;
import jakarta.servlet.http.Cookie;

import java.util.Map;

/**
 * 实现了协议执行器接口的静态协议执行器类。
 * <p>
 * 该类旨在处理特定的协议执行逻辑，通过静态方法提供协议处理能力。
 * 作为协议执行器，它负责解析和执行特定格式的协议数据。
 * </p>
 *
 * @since 2024/6/12
 * @author CH
 */
@Spi("static")
public class StaticProtocolExecutor implements ProtocolExecutor {

    /**
     * 认证客户端属性（最近一次从运行时上下文取到的快照）
    */
    private AuthClientProperties authClientProperties;

    /**
     * 创建 静态协议执行器 实例
    */
    public StaticProtocolExecutor() {
    }

    /**
     * 认证客户端属性。
     * <p>本实例可能早于 Spring 绑定完成而构造，故每次取用与运行时上下文对齐。</p>
     *
     * @return 认证客户端属性
     */
    private AuthClientProperties getAuthClientProperties() {
        AuthClientProperties current = OauthClientRuntimeContext.getAuthClientProperties();
        if (current != null && current != authClientProperties) {
            authClientProperties = current;
        }
        return authClientProperties;
    }

    @Override
    /**
     * 获取access令牌
    */
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        AuthClientProperties.TempUser temp = getAuthClientProperties().getTemp();
        String rawConfig = temp != null ? temp.getUser() : null;
        // 读可变凭据存储：改密后登录鉴权使用新口令（首次按配置初始化）
        String rawUsers = StringUtils.isBlank(rawConfig) ? rawConfig
                : com.chua.starter.oauth.client.support.user.StaticUserStore.get().effective(rawConfig);
        if (StringUtils.isBlank(rawUsers)) {
            LoginAuthResult empty = new LoginAuthResult();
            empty.setCode(403);
            empty.setMessage("静态临时账号未配置");
            return empty;
        }
        // 短信 / 邮箱登录：验证码归属已在业务层校验，令牌签发免口令，仅按用户名匹配
        if (authType == AuthType.SMS || authType == AuthType.EMAIL) {
            LoginAuthResult codeResult = StaticTempLogin.loginByVerifiedCode(
                    rawUsers, username, authType.getCode(),
                    getAuthClientProperties().getClampedStaticTokenExpireSeconds());
            if (codeResult == null) {
                LoginAuthResult denied = new LoginAuthResult();
                denied.setCode(403);
                denied.setMessage("账号未配置，无法登录");
                return denied;
            }
            return codeResult;
        }
        LoginAuthResult result = StaticTempLogin.login(rawUsers, username, password,
                authType != null ? authType.getCode() : "STATIC",
                getAuthClientProperties().getClampedStaticTokenExpireSeconds());
        if (result == null) {
            LoginAuthResult denied = new LoginAuthResult();
            denied.setCode(403);
            denied.setMessage("账号或密码错误");
            return denied;
        }
        return result;
    }

    @Override
    /**
     * Logout
    */
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        Cookie cookie = CookieUtil.get(RequestUtils.getRequest(), "x-oauth-cookie");
        if (null != cookie) {
            CookieUtil.remove(RequestUtils.getRequest(), ResponseUtils.getResponse(), "x-oauth-cookie");
        }
 // 强制下线：uid 是被踢用户标识，此时 userResult 是操作人自己的会话，不能吊销错令牌
        if (logoutType == LogoutType.KICK) {
            return StaticProtocol.kick(uid);
        }
 // 静态令牌自身无状态，必须登记进吊销名单，否则登出后旧令牌仍然可用
        String token = null != userResult && StringUtils.isNotBlank(userResult.getToken())
                ? userResult.getToken() : uid;
        if (StringUtils.isNotBlank(token) && !"null".equals(token)) {
            AbstractProtocol.revokeToken(token, null != userResult ? userResult.getExpireTime() : null);
        }
        if (logoutType == LogoutType.LOGOUT_ALL) {
            AbstractProtocol.invalidateAllCache();
        }
        /**
         * login认证结果。
         * @param 200 200
         * @param "" ""
         * @return login认证结果的结果
         */
        return new LoginAuthResult(200, "");
    }
}
