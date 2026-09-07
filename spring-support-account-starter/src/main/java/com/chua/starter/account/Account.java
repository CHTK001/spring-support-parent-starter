package com.chua.starter.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号信息。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    /**
     * 快速构造已注册的账号（用于注册成功后返回）。
     */
    public static Account registered(String email, String password, String token, PlatformType platformType) {
        return Account.builder()
                .email(email)
                .password(password)
                .token(token)
                .platformType(platformType)
                .platform(platformType.name().toLowerCase())
                .createdAt(System.currentTimeMillis())
                .active(true)
                .build();
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
