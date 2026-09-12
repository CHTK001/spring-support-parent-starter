package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.algorithm.TokenCipher;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
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
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;

import static com.chua.common.support.constant.NameConstant.NULL;
import static com.chua.starter.common.support.constant.Constant.ADMIN;
import static com.chua.starter.common.support.constant.Constant.OPS;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.SUPER_ADMIN;

/**
 * 静态协议实现类，用于处理静态认证逻辑。
   * 该类通过 令牌cipher（SM4加密）解析令牌或Cookie中的用户信息，并构建认证信息。
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Spi({"Static"})
public class StaticProtocol extends AbstractProtocol {

    /**
     * 构造函数
     *
     * @param authClientProperties 认证客户端配置属性
     */
    public StaticProtocol(AuthClientProperties authClientProperties) {
        /**
         * 父。
         * @param authClientProperties 认证客户端属性
         */
        super(authClientProperties);
    }

    /**
      * 认证方法，根据Cookie和令牌进行用户认证
     *
     * @param cookie      Cookie对象
     * @param token       认证令牌
     * @param subProtocol 子协议
     * @return 认证信息对象
     */
    @Override
    protected AuthenticationInformation approve(Cookie cookie, String token, String subProtocol) {
        try {
            return newAuthenticationInformation(token, new Cookie[]{cookie});
        } catch (Exception e) {
            log.error("[StaticProtocol]approve异常 - 异常: {}", e.toString(), e);
            return AuthenticationInformation.noAuth();
        }
    }

    @Override
    /** 认证用户编码 */
    protected AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret) {
        log.warn("[StaticProtocol]静态协议不支持AppKey认证 - appId: {}",
                appKeySecret != null ? appKeySecret.getAppId() : null);
        return AuthenticationInformation.noAuth();
    }

    /**
      * 升级认证信息方法，根据Cookie、令牌和升级类型进行认证信息升级
     *
     * @param cookie       Cookie对象
     * @param token        认证令牌
     * @param upgradeType  升级类型
     * @param refreshToken 刷新令牌
     * @return 认证信息对象
     */
    @Override
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken) {
        return newAuthenticationInformation(token, new Cookie[]{cookie});
    }

    /**
     * 创建新的认证信息对象
     *
     * @param token  认证令牌
     * @param cookie Cookie数组
     * @return 认证信息对象
     */
    private AuthenticationInformation newAuthenticationInformation(String token, Cookie[] cookie) {
        UserResume userResume = new UserResume();
        UserResult userResult = null;
 // 如果令牌不为空且不为"空"，则尝试解析令牌
        if (StringUtils.isNotBlank(token) && !NULL.equals(token)) {
            userResult = TokenCipher.decryptToUser(token);
            if (null == userResult) {
                log.error("[StaticProtocol]token解密或解析失败 - token长度: {}", token.length());
            }
        }

 // 如果通过令牌未能获取到用户信息，则尝试从Cookie中解析
        if (null == userResult && null != cookie) {
            for (Cookie cookie1 : cookie) {
                if (null == cookie1) {
                    continue;
                }
                if (!"JSESSIONID".equals(cookie1.getName())) {
                    userResult = TokenCipher.decryptToUser(cookie1.getValue());
                    if (null != userResult) {
                        break;
                    }
                }
            }
        }

        if (null == userResult || StringUtils.isBlank(userResult.getUsername())) {
            return AuthenticationInformation.noAuth();
        }

        // 如果成功解析到用户信息，则设置用户名并保存到请求上下文
        userResume.setUsername(userResult.getUsername());
        userResume.setLoginType(userResult.getLoginType());
        userResume.setNickName(userResult.getNickName());
        userResume.setPhone(userResult.getPhone());
        userResume.setTenantId(userResult.getTenantId());
        userResume.setDeptId(userResult.getDeptId());
        userResume.setAddress(userResult.getAddress());
        userResume.setExt(userResult.getExt());
        com.chua.starter.oauth.client.support.execute.AuthSessionUtils.setUsername(userResume.getUsername());

        // 根据用户名设置角色信息
        if (userResult.getRoles() != null && !userResult.getRoles().isEmpty()) {
            userResume.setRoles(userResult.getRoles());
        } else if (ADMIN.equalsIgnoreCase(userResume.getUsername())) {
            userResume.setRoles(Sets.newHashSet(SUPER_ADMIN, ADMIN));
        } else {
            userResume.setRoles(Sets.newHashSet(OPS));
        }
        userResume.setUserId(StringUtils.defaultString(userResult.getUserId(), "1"));
        userResume.setUid(StringUtils.defaultString(userResult.getUid(), DigestUtils.sha1(userResume.getUserId())));
        userResume.setOpenId(StringUtils.defaultString(userResult.getOpenId(), userResume.getUid()));
        userResume.setUnionId(StringUtils.defaultString(userResult.getUnionId(), userResume.getUid()));
        userResume.setPermission(userResult.getPermission() == null ? Collections.emptySet() : userResult.getPermission());
        userResume.setFingerprint(userResult.getFingerprint());
        userResume.setExpireTime(userResult.getExpireTime());
        userResume.setDataPermission(userResult.getDataPermission());
        userResume.setDataPermissionRule(userResult.getDataPermissionRule());
        // 将用户信息保存到请求上下文
        com.chua.starter.oauth.client.support.execute.AuthSessionUtils.setUserInfo(userResume);
        // 返回认证成功的信息
        /**
         * 认证信息。
         * @param Information.OK 信息.OK
         * @param userResume 用户resume
         * @return 认证信息的结果
         */
        return new AuthenticationInformation(Information.OK, userResume);
    }

    /**
     * 升级认证信息
     *
     * @param cookie        Cookie数组
     * @param token         认证令牌
     * @param upgradeType   升级类型
     * @param refreshToken  刷新令牌
     * @return 登录结果对象
     */
    @Override
    public LoginResult upgrade(Cookie[] cookie, String token, UpgradeType upgradeType, String refreshToken) {
        String sourceToken = StringUtils.isNotBlank(refreshToken) ? refreshToken : token;
        if (StringUtils.isBlank(sourceToken)) {
            return null;
        }

        AuthenticationInformation authenticationInformation = newAuthenticationInformation(sourceToken, cookie);
        if (authenticationInformation == null
                || authenticationInformation.getInformation() == null
                || authenticationInformation.getInformation().getCode() != Information.OK.getCode()
                || authenticationInformation.getReturnResult() == null) {
            return null;
        }

        UserResume userResume = authenticationInformation.getReturnResult();
        UserResult userResult = new UserResult();
        userResult.setUid(userResume.getUid());
        userResult.setUserId(userResume.getUserId());
        userResult.setUsername(userResume.getUsername());
        userResult.setNickName(userResume.getNickName());
        userResult.setPhone(userResume.getPhone());
        userResult.setAddress(userResume.getAddress());
        userResult.setLoginType(userResume.getLoginType());
        userResult.setTenantId(userResume.getTenantId());
        userResult.setDeptId(userResume.getDeptId());
        userResult.setOpenId(userResume.getOpenId());
        userResult.setUnionId(userResume.getUnionId());
        userResult.setFingerprint(userResume.getFingerprint());
        userResult.setPermission(userResume.getPermission() == null ? Collections.emptySet() : userResume.getPermission());
        userResult.setRoles(userResume.getRoles());
        userResult.setExt(userResume.getExt());
        userResult.setDataPermission(userResume.getDataPermission());
        userResult.setDataPermissionRule(userResume.getDataPermissionRule());
        userResult.setExpireTime(System.currentTimeMillis() / 1000 + 7L * 24 * 60 * 60);

        String refreshedToken = TokenCipher.encrypt(userResult);
        if (StringUtils.isBlank(refreshedToken)) {
            return null;
        }

        userResult.setToken(refreshedToken);
        userResult.setRefreshToken(refreshedToken);
        /**
         * login结果。
         * @param refreshedToken refreshed令牌
         * @param refreshedToken refreshed令牌
         * @param userResume.getFingerprint( 用户resume.getfingerprint(
         * @return login结果的结果
         */
        return new LoginResult(refreshedToken, refreshedToken, userResume.getFingerprint(), userResult);
    }

    /**
     * 获取访问令牌
     *
     * <p>静态协议下基于 {@code plugin.oauth.temp.user} 验证账号密码，
       * 命中后通过 {@link TokenCipher} 签发 令牌，角色/用户标识 与
     * {@link #newAuthenticationInformation} 保持一致（admin → SUPER_ADMIN + ADMIN）。</p>
     *
     * @param username  用户名
     * @param password  密码
     * @param authType  认证类型
     * @param ext       扩展信息
     * @return 登录认证结果对象
     */
    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        try {
            String tempUser = authClientProperties.getTemp() != null
                    ? authClientProperties.getTemp().getUser() : null;
            if (StringUtils.isBlank(tempUser)) {
                log.warn("[StaticProtocol] 临时账号未配置 (plugin.oauth.temp.user)");
                return null;
            }
            String[] parts = tempUser.split(":", 2);
            if (parts.length != 2) {
                log.warn("[StaticProtocol] 临时账号格式错误 - 期望 user:password");
                return null;
            }
            if (!parts[0].equals(username) || !parts[1].equals(password)) {
                log.warn("[StaticProtocol] 临时账号不匹配 - 输入: {}", username);
                return null;
            }

            UserResult userResult = new UserResult();
            userResult.setUserId("1");
            userResult.setUid(DigestUtils.sha1("1"));
            userResult.setUsername(parts[0]);
            userResult.setNickName(parts[0]);
            userResult.setLoginType(authType != null ? authType.getCode() : "web");
            userResult.setOpenId(userResult.getUid());
            userResult.setUnionId(userResult.getUid());
            if (ADMIN.equalsIgnoreCase(parts[0])) {
                userResult.setRoles(Sets.newHashSet(SUPER_ADMIN, ADMIN));
            } else {
                userResult.setRoles(Sets.newHashSet(OPS));
            }
            userResult.setExpireTime(System.currentTimeMillis() / 1000 + 7L * 24 * 60 * 60);

            String token = TokenCipher.encrypt(userResult);
            if (StringUtils.isBlank(token)) {
                log.warn("[StaticProtocol] TokenCipher 签发失败");
                return null;
            }
            userResult.setToken(token);
            userResult.setRefreshToken(token);

            LoginAuthResult result = new LoginAuthResult();
            result.setCode(200);
            result.setToken(token);
            result.setRefreshToken(token);
            result.setFingerprint(userResult.getFingerprint());
            UserResume userResume = new UserResume();
            userResume.setUserId(userResult.getUserId());
            userResume.setUid(userResult.getUid());
            userResume.setUsername(userResult.getUsername());
            userResume.setNickName(userResult.getNickName());
            userResume.setLoginType(userResult.getLoginType());
            userResume.setRoles(userResult.getRoles());
            userResume.setOpenId(userResult.getOpenId());
            userResume.setUnionId(userResult.getUnionId());
            userResume.setFingerprint(userResult.getFingerprint());
            userResume.setPermission(userResult.getPermission());
            userResume.setExpireTime(userResult.getExpireTime());
            result.setUserResume(userResume);
            return result;
        } catch (Exception e) {
            log.error("[StaticProtocol] getAccessToken 异常", e);
            return null;
        }
    }

    /**
     * 用户登出
     *
     * @param uid          用户标识
     * @param logoutType   登出类型
     * @param userResult   用户结果对象
     * @return 登录认证结果对象
     */
    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        return null;
    }
}

