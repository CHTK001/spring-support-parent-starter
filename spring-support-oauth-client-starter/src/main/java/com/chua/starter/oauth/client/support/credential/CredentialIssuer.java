// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.credential;

import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.algorithm.TokenCipher;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 令牌签发器：把一份已经确定的身份装配成可用的登录结果。
 *
 * <p>抽出来是因为每个 {@link CredentialProvider} 最终都要做同一件事：补有效期、
 * 补 {@code ext} 默认字段、加密成令牌、回填令牌与刷新令牌。让各实现自己抄一遍，
 * 迟早会出现「某个实现忘了设 expireTime，令牌永不过期」这类不一致。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class CredentialIssuer {

    private CredentialIssuer() {
    }

    /**
     * 按给定身份签发令牌。
     *
     * <p>调用方负责身份正确性（本方法只装配，不校验）。必须已设置
     * {@code userId}、{@code username}、{@code roles}；其余字段缺失会被补上安全默认值。</p>
     *
     * @param userResult 身份信息，本方法会在其上补齐有效期与 {@code ext} 并回填令牌
     * @param ttlSeconds 令牌有效期（秒），小于等于 0 时按 1 秒处理
     * @return 含令牌的登录结果；令牌生成失败时返回 500 与失败原因，不返回 {@code null}
     */
    public static LoginAuthResult issue(UserResult userResult, long ttlSeconds) {
        if (userResult == null || StringUtils.isBlank(userResult.getUserId())) {
            return StaticCredentialConfig.denied(500, "签发令牌缺少用户标识");
        }
        var uid = StringUtils.isNotBlank(userResult.getUid())
                ? userResult.getUid()
                : DigestUtils.sha256(userResult.getUserId());
        userResult.setUid(uid);
        if (StringUtils.isBlank(userResult.getUsername())) {
            userResult.setUsername(userResult.getUserId());
        }
        if (StringUtils.isBlank(userResult.getNickName())) {
            userResult.setNickName(userResult.getUsername());
        }
        if (StringUtils.isBlank(userResult.getOpenId())) {
            userResult.setOpenId(uid);
        }
        if (StringUtils.isBlank(userResult.getUnionId())) {
            userResult.setUnionId(uid);
        }
        userResult.setExpireTime(System.currentTimeMillis() / 1000 + Math.max(1, ttlSeconds));

        Map<String, Object> ext = userResult.getExt() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(userResult.getExt());
        // sysUser* 字段是前端契约的一部分，缺失会让登录后的用户信息少字段
        ext.putIfAbsent("userId", userResult.getUserId());
        ext.putIfAbsent("sysUserId", userResult.getUserId());
        ext.putIfAbsent("sysUserUsername", userResult.getUsername());
        ext.putIfAbsent("sysUserNickname", userResult.getNickName());
        ext.putIfAbsent("sysUserAvatar", "");
        ext.putIfAbsent("avatar", "");
        userResult.setExt(ext);

        var token = TokenCipher.encrypt(userResult);
        if (StringUtils.isBlank(token)) {
            return StaticCredentialConfig.denied(500, "令牌生成失败");
        }
        userResult.setToken(token);
        userResult.setRefreshToken(token);

        var result = new LoginAuthResult();
        result.setCode(200);
        result.setToken(token);
        result.setRefreshToken(token);
        result.setFingerprint(userResult.getFingerprint());
        result.setUserResume(userResult);
        return result;
    }
}