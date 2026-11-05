// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.credential;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.StaticTempLogin;

/**
 * 内置「验证码」凭据实现，覆盖短信与邮箱验证码登录。
 *
 * <p>与 {@link PasswordCredentialProvider} 的关键差别是<b>不比对口令</b>：验证码的归属
 * （是否发到本人手机、是否过期、是否已使用）由业务层在调用本实现之前完成校验，
 * 本实现只按登录名匹配账号并签发令牌。</p>
 *
 * <p>因此本实现绝不能被「口令为空」的情形绕过：登录名为空直接拒绝。</p>
 *
 * @since 4.0.0.42
 * @author CH
 * @see CredentialResolver
 */
@Spi({
        "builtin-sms",
        "builtin-email"
})
public class VerifiedCodeCredentialProvider implements CredentialProvider {

    @Override
    public LoginAuthResult authenticate(CredentialRequest request) {
        var rawUsers = StaticCredentialConfig.effectiveUserConfig();
        if (StringUtils.isBlank(rawUsers)) {
            return StaticCredentialConfig.denied(403, "静态临时账号未配置");
        }
        // 登录名为空时必须拒绝：验证码登录只凭账号签发，空账号等于给任意匿名请求发令牌
        if (StringUtils.isBlank(request.account())) {
            return StaticCredentialConfig.denied(403, "登录名不能为空");
        }
        var result = StaticTempLogin.loginByVerifiedCode(rawUsers, request.account(),
                request.authType() == null ? AuthType.SMS.getCode() : request.authType().getCode(),
                resolveTtlSeconds(request));
        if (result == null) {
            return StaticCredentialConfig.denied(403, "账号未配置，无法登录");
        }
        return result;
    }

    /**
     * 令牌有效期：优先用调用方给定的值，缺省时回落到配置，避免传 0 签发出 1 秒即失效的令牌。
     *
     * @param request 登录请求上下文
     * @return 有效期秒数
     */
    private long resolveTtlSeconds(CredentialRequest request) {
        return request.tokenTtlSeconds() > 0
                ? request.tokenTtlSeconds()
                : StaticCredentialConfig.tokenTtlSeconds();
    }
}