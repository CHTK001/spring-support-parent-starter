// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.credential;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.StaticTempLogin;

/**
 * 内置「账号 + 口令」凭据实现，覆盖网页、App、令牌透传与三方登录。
 *
 * <p>这些登录方式在校验口径上完全一致：拿登录名和口令去匹配
 * {@code plugin.oauth.temp.user}。三方登录（微信 / Gitee / GitHub）的凭证在进入本实现前
 * 已由业务层换算成三方确认过的凭据，因此同样走口令比对，不再单独校验。</p>
 *
 * <p>注册名统一带 {@code builtin-} 前缀，给同名自定义实现让位：
 * 想改网页登录（例如把登录名从用户名换成手机号）只需注册一个 {@code @Spi("web")}
 * 实现，无需修改本类，也无需修改 oauth 模块。</p>
 *
 * @since 4.0.0.42
 * @author CH
 * @see CredentialResolver
 */
@Spi({
        "builtin-web",
        "builtin-app",
        "builtin-token",
        "builtin-wechat",
        "builtin-gitee",
        "builtin-github",
        "builtin-third_party"
})
public class PasswordCredentialProvider implements CredentialProvider {

    @Override
    public LoginAuthResult authenticate(CredentialRequest request) {
        var rawUsers = StaticCredentialConfig.effectiveUserConfig();
        if (StringUtils.isBlank(rawUsers)) {
            return StaticCredentialConfig.denied(403, "静态临时账号未配置");
        }
        var result = StaticTempLogin.login(rawUsers, request.account(), request.secret(),
                request.authType() == null ? "STATIC" : request.authType().getCode(),
                resolveTtlSeconds(request));
        if (result == null) {
            return StaticCredentialConfig.denied(403, "账号或密码错误");
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