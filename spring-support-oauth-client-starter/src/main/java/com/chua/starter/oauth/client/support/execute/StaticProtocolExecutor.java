package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.utils.ResponseUtils;
import com.chua.starter.oauth.client.support.algorithm.TokenCipher;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.protocol.AbstractProtocol;
import com.chua.starter.oauth.client.support.protocol.StaticProtocol;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.google.common.collect.Sets;
import com.google.common.base.Splitter;
import jakarta.servlet.http.Cookie;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** 认证客户端属性 */
    private final AuthClientProperties authClientProperties;

    /** 创建 静态协议执行器 实例 */
    public StaticProtocolExecutor() {
        this.authClientProperties = OauthClientRuntimeContext.getAuthClientProperties();
    }

    @Override
    /** 获取access令牌 */
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        return newLoginAuthResult(username, password);
    }

    @Override
    /** Logout */
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

    /**
     * 新登录身份验证结果
     *
     * @param username 用户名
     * @param password 暗语
     * @return {@link login认证结果}
     */
    private LoginAuthResult newLoginAuthResult(String username, String password) {
        AuthClientProperties.TempUser temp = authClientProperties.getTemp();
        LoginAuthResult loginAuthResult = new LoginAuthResult();
        String user = temp.getUser();
        if (StringUtils.isNotEmpty(user)) {
            Set<String> strings = new HashSet<>(Splitter.on(";").omitEmptyStrings().trimResults().splitToList(user));
            for (String string : strings) {
                List<String> userAndPassword = Splitter.on(":").omitEmptyStrings().limit(2).trimResults().splitToList(string);
                if (isMatch(userAndPassword, username, password)) {
                    loginAuthResult.setCode(200);
                    UserResult userResult = new UserResult();
                    userResult.setUserId("1");
                    userResult.setLoginType("STATIC");
                    userResult.setUsername(username);
                    userResult.setNickName(username);
                    userResult.setPermission(Collections.emptySet());
                    userResult.setExpireTime(System.currentTimeMillis() / 1000 + 7L * 24 * 60 * 60);
                    if ("admin".equalsIgnoreCase(username)) {
                        userResult.setRoles(Sets.newHashSet("SUPER_ADMIN", "ADMIN"));
                    } else {
                        userResult.setRoles(Sets.newHashSet("OPS"));
                    }

                    userResult.setUid(DigestUtils.md5(userResult.getUserId()));
                    userResult.setExt(Map.of(
                            "sysUserId", 1,
                            "sysUserUsername", userResult.getUsername(),
                            "sysUserNickname", userResult.getNickName(),
                            "sysUserAvatar", "",
                            "avatar", ""
                    ));
                    userResult.setOpenId(userResult.getUid());
                    userResult.setUnionId(userResult.getUid());

                    String token = TokenCipher.encrypt(userResult);
                    if (StringUtils.isBlank(token)) {
                        loginAuthResult.setCode(500);
                        loginAuthResult.setMessage("静态登录令牌生成失败");
                        return loginAuthResult;
                    }

                    userResult.setToken(token);
                    userResult.setRefreshToken(token);
                    loginAuthResult.setUserResume(userResult);
                    loginAuthResult.setToken(token);
                    loginAuthResult.setRefreshToken(token);
                    return loginAuthResult;
                }
            }
        }

        loginAuthResult.setCode(403);
        loginAuthResult.setMessage("账号或密码错误");
        return loginAuthResult;

    }

    /**
     * 匹配用户与密码
     *
     * @param userAndPassword 用户和密码列表
     * @param username        用户名
     * @param password        密码
     * @return 是否匹配
     */
    private boolean isMatch(List<String> userAndPassword, String username, String password) {
        if (userAndPassword.isEmpty()) {
            return false;
        }

        String user = CollectionUtils.find(userAndPassword, 0);
        if (userAndPassword.size() == 1) {
            return user.equals(username) && user.equals(password);
        }

        String passwd = CollectionUtils.find(userAndPassword, 1);
        // 兼容明文与 md5 两种密码提交方式（前端登录页通常提交明文）
        return user.equals(username)
                && (passwd.equals(password) || DigestUtils.md5(passwd).equals(password));
    }
}
