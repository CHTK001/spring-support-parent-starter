package com.chua.starter.sso.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 三方账号绑定完成请求
 *
 * <p>前端在绑定页输入本地账号密码，连同回调带回的 {@code bindToken} 一起提交；
 * 服务端核销 bindToken、校验账号密码后落绑定关系并签发登录结果。</p>
 *
 * @since 2026/11/21
 * @author CH
 */
@Schema(description = "三方账号绑定完成请求")
public record ThirdBindCompleteRequest(
        /**
         * 回调带回的待绑定凭证
         */
        @NotBlank(message = "bindToken不能为空")
        @Schema(description = "回调带回的待绑定凭证")
        String bindToken,
        /**
         * 本地账号用户名
         */
        @NotBlank(message = "用户名不能为空")
        @Schema(description = "本地账号用户名")
        String username,
        /**
         * 本地账号密码
         */
        @NotBlank(message = "密码不能为空")
        @Schema(description = "本地账号密码")
        String password
) {
}
