package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.google.common.collect.Sets;
import io.micrometer.core.instrument.util.StringUtils;
import jakarta.servlet.http.Cookie;

import java.util.Collections;
import java.util.Map;

import static com.chua.common.support.constant.NameConstant.NULL;
import static com.chua.starter.common.support.constant.Constant.ADMIN;
import static com.chua.starter.common.support.constant.Constant.OPS;
import static com.chua.starter.oauth.client.support.execute.AuthClientExecute.DEFAULT_KEY;

/**
 * 静态协议实现类，用于处理静态认证逻辑。
 * 该类通过SM4加密算法解析token或cookie中的用户信息，并构建认证信息。
 */
@Extension("Static")
public class StaticProtocol extends AbstractProtocol {
    /**
     * SM4加密解密器，用于解析token和cookie中的用户信息
     */
    private static volatile Codec SM4;

    /**
     * 构造函数
     *
     * @param authClientProperties 认证客户端配置属性
     */
    public StaticProtocol(AuthClientProperties authClientProperties) {
        super(authClientProperties);
    }

    /**
     * 认证方法，根据cookie和token进行用户认证
     *
     * @param cookie      Cookie对象
     * @param token       认证token
     * @param subProtocol 子协议
     * @return 认证信息对象
     */
    @Override
    protected AuthenticationInformation approve(Cookie cookie, String token, String subProtocol) {
        initial();
        return newAuthenticationInformation(token, new Cookie[]{cookie});
    }

    /**
     * 升级认证信息方法，根据cookie、token和升级类型进行认证信息升级
     *
     * @param cookie       Cookie对象
     * @param token        认证token
     * @param upgradeType  升级类型
     * @param refreshToken 刷新token
     * @return 认证信息对象
     */
    @Override
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken) {
        initial();
        return newAuthenticationInformation(token, new Cookie[]{cookie});
    }

    /**
     * 初始化SM4加密解密器
     */
    private void initial() {
        if (null == SM4) {
            synchronized (this) {
                if (null == SM4) {
                    SM4 = Codec.build("SM4", DEFAULT_KEY);
                }
            }
        }
    }


    /**
     * 创建新的认证信息对象
     *
     * @param token  认证token
     * @param cookie Cookie数组
     * @return 认证信息对象
     */
    private AuthenticationInformation newAuthenticationInformation(String token, Cookie[] cookie) {
        UserResume userResume = new UserResume();
        UserResult userResult = null;
        // 如果token不为空且不为"null"，则尝试解析token
        if (StringUtils.isNotBlank(token) && !NULL.equals(token)) {
            userResult = Json.fromJson(SM4.decodeHex(token), UserResult.class);
        }

        // 如果通过token未能获取到用户信息，则尝试从cookie中解析
        if (null == userResult && null != cookie) {
            for (Cookie cookie1 : cookie) {
                if (!"JSESSIONID".equals(cookie1.getName())) {
                    String source;
                    try {
                        source = SM4.decodeHex(cookie1.getValue());
                        userResult = Json.fromJson(source, UserResult.class);
                    } catch (Exception e) {
                        // 解析异常时继续尝试其他cookie
                    }
                    if (null != userResult) {
                        break;
                    }
                }
            }
        }

        // 如果成功解析到用户信息，则设置用户名并保存到请求上下文
        if (null != userResult) {
            userResume.setUsername(userResult.getUsername());
            RequestUtils.setUsername(userResume.getUsername());
        }

        // 根据用户名设置角色信息
        if (ADMIN.equalsIgnoreCase(userResume.getUsername())) {
            userResume.setRoles(Sets.newHashSet(ADMIN));
        } else {
            userResume.setRoles(Sets.newHashSet(OPS));
        }
        // 设置空权限集合
        userResume.setPermission(Collections.emptySet());
        // 将用户信息保存到请求上下文
        RequestUtils.setUserInfo(userResume);
        // 返回认证成功的信息
        return new AuthenticationInformation(Information.OK, userResume);
    }

    /**
     * 升级认证信息
     *
     * @param cookie        Cookie数组
     * @param token         认证token
     * @param upgradeType   升级类型
     * @param refreshToken  刷新token
     * @return 登录结果对象
     */
    @Override
    public LoginResult upgrade(Cookie[] cookie, String token, UpgradeType upgradeType, String refreshToken) {
        return new LoginAuthResult(0, null);
    }

    /**
     * 获取访问令牌
     *
     * @param username  用户名
     * @param password  密码
     * @param authType  认证类型
     * @param ext       扩展信息
     * @return 登录认证结果对象
     */
    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        return null;
    }

    /**
     * 用户登出
     *
     * @param uid          用户ID
     * @param logoutType   登出类型
     * @param userResult   用户结果对象
     * @return 登录认证结果对象
     */
    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        return null;
    }
}
