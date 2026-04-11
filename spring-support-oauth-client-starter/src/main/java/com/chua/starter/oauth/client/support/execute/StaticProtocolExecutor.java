package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.text.json.Json;
import com.chua.common.support.core.utils.CollectionUtils;
import com.chua.common.support.core.utils.DigestUtils;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.common.support.utils.ResponseUtils;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.google.common.collect.Sets;
import jakarta.servlet.http.Cookie;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.chua.starter.oauth.client.support.execute.AuthClientExecute.DEFAULT_KEY;

/**
 * 实现了ProtocolExecutor接口的StaticProtocolExecutor类。
 * <p>
 * 该类旨在处理特定的协议执行逻辑，通过静态方法提供协议处理能力。
 * 作为协议执行器，它负责解析和执行特定格式的协议数据。
 * </p>
 *
 * @author CH
 * @since 2024/6/12
 */
public class StaticProtocolExecutor implements ProtocolExecutor {

    private final AuthClientProperties authClientProperties;
    private final String encryption;

    public StaticProtocolExecutor() {
        this.authClientProperties = OauthClientRuntimeContext.getAuthClientProperties();
        this.encryption = "SM4";
    }

    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        return newLoginAuthResult(username, password);
    }

    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        Cookie cookie = CookieUtil.get(RequestUtils.getRequest(), "x-oauth-cookie");
        if (null != cookie) {
            CookieUtil.remove(RequestUtils.getRequest(), ResponseUtils.getResponse(), "x-oauth-cookie");
        }
        return new LoginAuthResult(200, "");
    }

    /**
     * 新登录身份验证结果
     *
     * @param username 用户名
     * @param password 暗语
     * @return {@link LoginAuthResult}
     */
    private LoginAuthResult newLoginAuthResult(String username, String password) {
        AuthClientProperties.TempUser temp = authClientProperties.getTemp();
        LoginAuthResult loginAuthResult = new LoginAuthResult();
        String user = temp.getUser();
        if (StringUtils.isNotEmpty(user)) {
            Set<String> strings = Splitter.on(";").omitEmptyStrings().trimResults().splitToSet(user);
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

                    userResult.setUid(DigestUtils.md5Hex(userResult.getUserId()));
                    userResult.setExt(Map.of(
                            "sysUserId", 1,
                            "sysUserUsername", userResult.getUsername(),
                            "sysUserNickname", userResult.getNickName(),
                            "sysUserAvatar", "",
                            "avatar", ""
                    ));
                    userResult.setOpenId(userResult.getUid());
                    userResult.setUnionId(userResult.getUid());

                    String token = encodeUserResult(userResult);
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

    private String encodeUserResult(UserResult userResult) {
        try {
            return Codec.build(encryption, DEFAULT_KEY).encodeHex(Json.toJson(userResult));
        } catch (Exception ignored) {
            return null;
        }
    }


    /**
     * 匹配
     *
     * @param userAndPassword 用户和密码
     * @param username        用户名
     * @param password        暗语
     * @return boolean
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
        return user.equals(username) && DigestUtils.md5Hex(passwd).equals(password);
    }
}

