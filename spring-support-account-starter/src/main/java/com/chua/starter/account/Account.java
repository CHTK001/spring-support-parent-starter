package com.chua.starter.account;

import lombok.Builder;
import lombok.Data;

/**
 * 账号信息。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Builder
public class Account {

    /**
     * 账号ID
     */
    private String id;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 密码
     */
    private String password;

    /**
     * 第三方平台返回的token（如SSO token、access_token等）
     */
    private String token;

    /**
     * 附加数据（JSON字符串）
     */
    private String extra;

    /**
     * 注册平台名称（如 grok/twitter/discord）
     */
    private String platform;

    /**
     * 邮箱提供商（fce/mail-tm/luckmail）
     */
    private String emailProvider;

    /**
     * 创建时间
     */
    private long createdAt;

    /**
     * 是否有效
     */
    private boolean active;

    /**
     * 最后使用时间
     */
    private long lastUsedAt;

    public Account(String email, String password, String token, String platform) {
        this.email = email;
        this.password = password;
        this.token = token;
        this.platform = platform;
        this.createdAt = System.currentTimeMillis();
        this.active = true;
    }
}
