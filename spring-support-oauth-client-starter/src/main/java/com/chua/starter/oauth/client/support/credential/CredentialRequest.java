package com.chua.starter.oauth.client.support.credential;

import com.chua.starter.oauth.client.support.enums.AuthType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 凭据校验上下文（一次登录请求的全部入参）。
 *
 * <p>这是 {@link CredentialProvider} 唯一能看到的输入。业务方实现扩展点时不需要读
 * 线程上下文，也就不会与协议层（{@code Protocol}）的实现耦合。</p>
 *
 * <p>与 {@code AuthType} 的分工：{@code AuthType} 描述「这次登录怎么来的」
 * （网页 / App / 短信 / 三方），{@code accountType} 描述「账号从哪张表来」
 * （静态配置 / 学生档案 / 企业目录）。两者正交，因此新增账号来源不需要动
 * {@code AuthType} 枚举，也不需要改协议层。</p>
 *
 * @param account         登录名（学号 / 用户名 / 手机号 / 邮箱 / 三方标识，具体含义由账号来源约定）
 * @param secret          口令；验证码与三方登录场景传三方已校验的凭证，未校验则传 {@code null}
 * @param authType        认证类型，见 {@link AuthType}
 * @param accountType     账号来源，缺省回落为 {@link AuthType#getCode()}
 * @param ext             扩展参数，按不可变视图持有
 * @param tokenTtlSeconds 令牌有效期（秒）
 *
 * @since 4.0.0.42
 * @author CH
 */
public record CredentialRequest(
        String account,
        String secret,
        AuthType authType,
        String accountType,
        Map<String, Object> ext,
        long tokenTtlSeconds) {

    /**
     * 紧凑构造器：规范化账号来源，并对扩展参数做防御性拷贝。
     *
     * <p>{@code ext} 来自 HTTP 请求体，可能带 {@code null} 值，因此不能用
     * {@code Map.copyOf}（它直接拒绝 {@code null} 元素），改用不可变包装。</p>
     */
    public CredentialRequest {
        accountType = normalize(accountType);
        ext = ext == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(ext));
    }

    /**
     * 本次请求真正生效的账号来源。
     *
     * <p>缺省回落为 {@link AuthType#getCode()}，保证没有显式声明账号来源的既有调用方
     * 行为与引入扩展点之前完全一致。</p>
     *
     * @return 规范化（小写、去首尾空白）后的账号来源，永不为 {@code null}
     */
    public String effectiveAccountType() {
        if (accountType != null) {
            return accountType;
        }
        return authType == null ? AuthType.NONE.getCode() : authType.getCode();
    }

    /**
     * 读取扩展参数。
     *
     * @param key 参数名
     * @return 参数值；{@code key} 为 {@code null} 或参数不存在时返回 {@code null}
     */
    public Object extValue(String key) {
        return key == null ? null : ext.get(key);
    }

    /**
     * 规范化账号来源：小写并去首尾空白。
     *
     * @param value 原始值
     * @return 规范化结果；空白值归一为 {@code null}，由 {@link #effectiveAccountType()} 兜底
     */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
