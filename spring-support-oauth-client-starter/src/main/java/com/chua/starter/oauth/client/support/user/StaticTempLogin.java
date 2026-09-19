package com.chua.starter.oauth.client.support.user;

import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.algorithm.TokenCipher;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.chua.starter.common.support.constant.Constant.ADMIN;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.SUPER_ADMIN;

/**
 * 静态临时账号登录（{@code plugin.oauth.temp.user}）。
 *
 * <p>配置格式：{@code 用户名:密码;用户名2:密码2}，密码兼容明文与 md5 两种提交方式。
 * 签发与鉴权（{@link com.chua.starter.oauth.client.support.protocol.StaticProtocol}）共用同一份解析，
 * 避免两条链路对同一配置给出不同的用户标识。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class StaticTempLogin {

    /**
     * 令牌有效期（秒）
     */
    private static final long TOKEN_TTL_SECONDS = 7L * 24 * 60 * 60;

    private StaticTempLogin() {
    }

    /**
     * 按静态临时账号换发访问令牌。
     *
     * @param rawUsers   {@code plugin.oauth.temp.user} 原始配置
     * @param username   登录名
     * @param password   口令（明文或配置明文的 md5）
     * @param loginType  登录类型，写入令牌便于用量归因
     * @return 登录结果，账号未配置或不匹配返回 {@code null}
     */
    public static LoginAuthResult login(String rawUsers, String username, String password, String loginType) {
        if (StringUtils.isBlank(rawUsers) || StringUtils.isBlank(username)) {
            return null;
        }
        for (String entry : Splitter.on(";").omitEmptyStrings().trimResults().splitToList(rawUsers)) {
            List<String> parts = Splitter.on(":").omitEmptyStrings().limit(2).trimResults().splitToList(entry);
            if (!isMatch(parts, username, password)) {
                continue;
            }
            return issue(parts.get(0), loginType);
        }
        return null;
    }

    /**
     * 签发令牌。
     *
     * <p>静态账号没有独立主键，用户标识直接取用户名：跨登录稳定，且不同账号的数据归属可区分。</p>
     *
     * @param username  用户名
     * @param loginType 登录类型
     * @return 登录结果，令牌签发失败返回 {@code null}
     */
    private static LoginAuthResult issue(String username, String loginType) {
        String userId = username;
        String uid = DigestUtils.md5(userId);

        UserResult userResult = new UserResult();
        userResult.setUserId(userId);
        userResult.setUid(uid);
        userResult.setUsername(username);
        userResult.setNickName(username);
        userResult.setLoginType(loginType);
        userResult.setOpenId(uid);
        userResult.setUnionId(uid);
        userResult.setPermission(Collections.emptySet());
        userResult.setRoles(ADMIN.equalsIgnoreCase(username)
                ? Sets.newHashSet(SUPER_ADMIN, ADMIN)
                : Sets.newHashSet("OPS"));
        userResult.setExpireTime(System.currentTimeMillis() / 1000 + TOKEN_TTL_SECONDS);

        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("userId", userId);
        ext.put("sysUserId", userId);
        ext.put("sysUserUsername", username);
        ext.put("sysUserNickname", username);
        ext.put("sysUserAvatar", "");
        ext.put("avatar", "");
        userResult.setExt(ext);

        String token = TokenCipher.encrypt(userResult);
        if (StringUtils.isBlank(token)) {
            LoginAuthResult failed = new LoginAuthResult();
            failed.setCode(500);
            failed.setMessage("静态登录令牌生成失败");
            return failed;
        }
        userResult.setToken(token);
        userResult.setRefreshToken(token);

        LoginAuthResult result = new LoginAuthResult();
        result.setCode(200);
        result.setToken(token);
        result.setRefreshToken(token);
        result.setFingerprint(userResult.getFingerprint());
        result.setUserResume(userResult);
        return result;
    }

    /**
     * 账号口令是否匹配。
     *
     * @param parts    配置片段，{@code [用户名]} 或 {@code [用户名, 密码]}
     * @param username 登录名
     * @param password 口令
     * @return 是否匹配
     */
    private static boolean isMatch(List<String> parts, String username, String password) {
        if (parts.isEmpty()) {
            return false;
        }
        String user = parts.get(0);
        if (!user.equals(username)) {
            return false;
        }
        if (parts.size() == 1) {
            return user.equals(password);
        }
        String passwd = parts.get(1);
        return passwd.equals(password) || DigestUtils.md5(passwd).equals(password);
    }
}
