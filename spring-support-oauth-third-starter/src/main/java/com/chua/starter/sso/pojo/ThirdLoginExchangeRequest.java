package com.chua.starter.sso.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 三方登录换票请求
 *
 * <p>回调重定向带回的 {@code redirectParam} 是 AES 密文（密钥仅存服务端），
 * 前端无法本地解密，只能原样回传本接口一次性兑换 {@link LoginResult}。</p>
 *
 * @since 2026/10/07
 * @author CH
 */
@Schema(description = "三方登录换票")
public record ThirdLoginExchangeRequest(
        /**
         * 回调带回的加密登录凭证
         */
        @NotBlank(message = "redirectParam不能为空")
        @Schema(description = "回调带回的加密登录凭证")
        String redirectParam
) {
}
