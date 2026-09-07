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
     * 平台类型：GROK/TWITTER/DISCORD 等
     */
    private PlatformType platformType;

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

    public Account(String email, String password, String token, PlatformType platformType) {
        this.email = email;
        this.password = password;
        this.token = token;
        this.platformType = platformType;
        this.platform = platformType.name().toLowerCase();
        this.createdAt = System.currentTimeMillis();
        this.active = true;
    }

    /**
     * 平台类型枚举
     */
    public enum PlatformType {
        /** Grok (xAI) */
        GROK,
        /** Twitter/X */
        TWITTER,
        /** Discord */
        DISCORD,
        /** Reddit */
        REDDIT,
        /** 自定义 */
        CUSTOM
    }
}
