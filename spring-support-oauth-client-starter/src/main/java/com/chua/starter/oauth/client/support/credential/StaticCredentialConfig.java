package com.chua.starter.oauth.client.support.credential;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.StaticUserStore;

/**
 * 静态凭据配置读取入口：登录签发、鉴权、内置凭据实现共用同一份口径。
 *
 * <p>单独抽出来的原因是这三处必须给出完全一致的答案。只要有一处漏读
 * {@link StaticUserStore} 的可变凭据，就会出现「改密后新口令能登、旧口令也能登」
 * 或「登录能过、鉴权不认」这类分叉。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class StaticCredentialConfig {

    private StaticCredentialConfig() {
    }

    /**
     * 当前生效的静态账号配置。
     *
     * <p>取值顺序为「配置文件 → 可变凭据存储覆盖」：改密后的新口令优先生效，
     * 未配置过可变凭据时回落到配置文件原文。</p>
     *
     * @return {@code 用户名:口令;用户名2:口令2} 形态的配置串；未配置返回 {@code null}
     */
    public static String effectiveUserConfig() {
        var properties = OauthClientRuntimeContext.getAuthClientProperties();
        if (properties == null) {
            return null;
        }
        var temp = properties.getTemp();
        var rawConfig = temp == null ? null : temp.getUser();
        if (StringUtils.isBlank(rawConfig)) {
            return rawConfig;
        }
        return StaticUserStore.get().effective(rawConfig);
    }

    /**
     * 静态账号签发的令牌有效期（秒）。
     *
     * @return 有效期秒数；配置尚未绑定时返回 0
     */
    public static long tokenTtlSeconds() {
        AuthClientProperties properties = OauthClientRuntimeContext.getAuthClientProperties();
        return properties == null ? 0L : properties.getClampedStaticTokenExpireSeconds();
    }

    /**
     * 构造一条拒绝结果。
     *
     * <p>供凭据校验失败时统一使用：返回 403 与人话提示，而不是抛异常。
     * 抛异常会被上层当成系统故障报 500，把「密码错了」暴露成「服务不可用」。</p>
     *
     * @param code    业务码，鉴权失败固定 403
     * @param message 面向用户的提示文案
     * @return 拒绝结果
     */
    public static LoginAuthResult denied(int code, String message) {
        var result = new LoginAuthResult();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
