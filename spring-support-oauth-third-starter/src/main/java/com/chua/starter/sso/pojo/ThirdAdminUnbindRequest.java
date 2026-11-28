package com.chua.starter.sso.pojo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理员解绑用户第三方绑定请求
 *
 * @since 2026/11/24
 * @author CH
 */
@Schema(description = "管理员解绑用户第三方绑定请求")
public record ThirdAdminUnbindRequest(
        /**
         * 目标本地用户标识
         */
        @Schema(description = "目标本地用户ID")
        Integer userId,
        /**
         * 第三方平台类型，如: gitee、GitHub
         */
        @Schema(description = "第三方平台类型")
        String loginType
) {
}
