package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.application.Binder;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.google.common.collect.Sets;

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
 * @author CH
 * @since 2024/6/12
 */
public class StaticProtocolExecutor implements ProtocolExecutor{

    private final AuthClientProperties authClientProperties;
    private final String encryption;

    public StaticProtocolExecutor() {
        this.authClientProperties = Binder.binder(AuthClientProperties.PRE, AuthClientProperties.class);
        this.encryption = "SM4";
    }

    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        return newLoginAuthResult(username, password);
    }

    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
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
        if(StringUtils.isNotEmpty(user)) {
            Set<String> strings = Splitter.on(";").omitEmptyStrings().trimResults().splitToSet(user);
            for (String string : strings) {
                List<String> userAndPassword = Splitter.on(":").omitEmptyStrings().limit(2).trimResults().splitToList(string);
                if(isMatch(userAndPassword, username, password)) {
                    loginAuthResult.setCode(200);
                    UserResume userResult = UserResume.builder().build();
                    userResult.setUserId("0");
                    userResult.setLoginType(AuthType.STATIC.name());
                    userResult.setUsername(username);
                    if("admin".equals(username)) {
                        userResult.setRoles(Sets.newHashSet("admin"));
                    }
                    loginAuthResult.setUserResume(userResult);
                    try {
                        loginAuthResult.setToken(Codec.build(encryption, DEFAULT_KEY).encodeHex(Json.toJson(userResult)));
                    } catch (Exception ignored) {
                    }
                    userResult.setUid(loginAuthResult.getToken());
                    return loginAuthResult;
                }
            }
        }

        loginAuthResult.setCode(403);
        loginAuthResult.setMessage("账号或密码错误");
        return loginAuthResult;

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
        if(userAndPassword.isEmpty()) {
            return false;
        }

        String user = CollectionUtils.find(userAndPassword, 0);
        if(userAndPassword.size() == 1) {
            return user.equals(username) && user.equals(password);
        }

        String passwd = CollectionUtils.find(userAndPassword, 1);
        return user.equals(username) && DigestUtils.md5Hex(passwd).equals(password);
    }
}

