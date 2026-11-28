package com.chua.starter.sso.pojo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 第三方解绑请求
 *
 * @since 2024/9/6
 * @author CH
 */
@Schema(description = "第三方解绑")
public record ThirdPartyUnbindRequest(
        /**
         * 解绑类型
         */
        @Schema(description = "解绑类型")
        /**
         * Login类型
         */
        String loginType
) {
}
