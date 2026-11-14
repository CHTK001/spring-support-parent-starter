package com.chua.starter.account.spi;

/**
 * 账号种类能力位。
 *
 * <p>界面按钮的显隐只认能力位，不认种类字符串：新增种类时不必回头改
 * 「邮箱显示校验、短信不显示」这类 if 分支。</p>
 *
 * <p><b>声明口径</b>：能力位表示「该种类<b>设计上</b>支持什么」，运行期还要再与
 * 「实现是否真的注册了」取交集（例如 {@link #VERIFY} 需要对应
 * {@link AccountVerifier} 实现存在）。只有真有实现的才可标为支持，
 * 未验证的能力不得声明。</p>
 *
 * @author CH
 * @since 4.0.0.61
 */
public enum AccountCapability {

    /**
     * 可探活校验凭据（需要对应 {@link AccountVerifier} 实现）
     */
    VERIFY,

    /**
     * 可打开收信箱（需要对应 {@link AccountInboxProvider} 实现）
     */
    INBOX,

    /**
     * 可把内联凭据收录进密钥库（{@code sys_account_secret}）
     */
    SECRET_LIBRARY,

    /**
     * 可设为同种类默认账号（{@code sys_account_default}）
     */
    DEFAULT_SWITCH
}
