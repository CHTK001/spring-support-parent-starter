package com.chua.starter.sso.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应对象
 *
 * <p>{@code @Builder} 生成全参构造后会抑制 {@code @Data} 的构造生成，类上没有无参构造，
 * Jackson 反序列化（三方登录换票 {@code /v2/user/third/login/exchange} 解密
 * {@code redirectParam}）必然失败；必须显式补齐 {@code @NoArgsConstructor}
 * + {@code @AllArgsConstructor}（后者供 {@code @Builder} 使用）。</p>
 *
 * @since 2024/8/15
 * @author CH
 */
@Schema(description = "登录响应对象")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult {

    /** 二次认证挑战；此时不包含正式令牌或用户信息。 */
    private com.chua.starter.oauth.client.support.user.SecondFactorChallenge secondFactor;

    /** 手机号身份已验证但尚未绑定本地账号时的一次性凭证。 */
    @Schema(description = "待绑定凭证，与访问令牌互斥")
    private String bindToken;

    /**
     * 访问令牌
     */
    @Schema(description = "访问token")
    /**
     * Access令牌
    */
    private String accessToken;

    /**
     * 令牌 类型
     */
    @Schema(description = "token 类型", example = "Bearer")
    /**
     * 令牌类型
    */
    private String tokenType;

    /**
     * 刷新令牌
     */
    @Schema(description = "刷新token")
    /**
     * Refresh令牌
    */
    private String refreshToken;

    /**
     * 用户信息
     */
    @Schema(description = "用户信息")
    /**
     * 用户信息
    */
    private UserInfoVO userInfo;

    /**
     * 过期时间(单位：毫秒)
     */
    @Schema(description = "过期时间(单位：毫秒)")
    /**
     * Expires
    */
    private Long expires;
}
