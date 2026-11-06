package com.chua.starter.account.manage.store;

/**
 * 账号种类词表。
 *
 * <p>字符串常量而不是枚举：账号种类由使用方（设备 / 模板 / 邮件 / 短信）扩展，
 * 落库值必须稳定，接口按字符串收发，避免新增种类时改动反序列化契约。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public final class AccountKind {

    /**
     * API 密钥 / Token（设备厂商、第三方服务）
     */
    public static final String API_KEY = "API_KEY";

    /**
     * 邮箱账号（POP3 / SMTP）
     */
    public static final String EMAIL = "EMAIL";

    /**
     * 短信账号（服务商密钥 + 签名 / 模板）
     */
    public static final String SMS = "SMS";

    private AccountKind() {
    }

    /**
     * 是否为受支持的账号种类。
     *
     * @param kind 种类
     * @return true 表示受支持
     */
    public static boolean supported(String kind) {
        return API_KEY.equals(kind) || EMAIL.equals(kind) || SMS.equals(kind);
    }
}
