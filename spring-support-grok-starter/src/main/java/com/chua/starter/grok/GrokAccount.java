package com.chua.starter.grok;

import lombok.Builder;
import lombok.Data;

/**
 * Grok 账号信息。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Builder
public class GrokAccount {

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 密码
     */
    private String password;

    /**
     * SSO Token
     */
    private String ssoToken;

    /**
     * OAuth Access Token（通过 SSO 兑换）
     */
    private String accessToken;

    /**
     * OAuth Refresh Token
     */
    private String refreshToken;

    /**
     * 创建时间
     */
    private long createdAt;

    /**
     * 是否有效
     */
    private boolean active;

    public GrokAccount(String email, String password, String ssoToken) {
        this.email = email;
        this.password = password;
        this.ssoToken = ssoToken;
        this.createdAt = System.currentTimeMillis();
        this.active = true;
    }
}
