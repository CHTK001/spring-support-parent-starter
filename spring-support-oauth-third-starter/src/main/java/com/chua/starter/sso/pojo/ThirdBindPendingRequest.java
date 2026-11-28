package com.chua.starter.sso.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 三方待绑定身份查询请求
 *
 * @since 2026/11/21
 * @author CH
 */
@Schema(description = "三方待绑定身份查询请求")
public record ThirdBindPendingRequest(
        /**
         * 回调带回的待绑定凭证
         */
        @NotBlank(message = "bindToken不能为空")
        @Schema(description = "回调带回的待绑定凭证")
        String bindToken
) {
}
