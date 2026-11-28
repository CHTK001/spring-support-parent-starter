package com.chua.starter.sso.pojo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户第三方绑定信息
 *
 * <p>管理端批量查询用户第三方绑定时返回的单条记录。</p>
 *
 * @since 2026/11/24
 * @author CH
 */
@Schema(description = "用户第三方绑定信息")
public record ThirdBindingVO(
        /**
         * 本地用户标识
         */
        @Schema(description = "本地用户ID")
        Integer sysUserId,
        /**
         * 第三方平台类型，如: gitee、GitHub、DINGTALK
         */
        @Schema(description = "第三方平台类型")
        String type,
        /**
         * 第三方用户标识
         */
        @Schema(description = "第三方用户uid")
        String uid,
        /**
         * 第三方用户名
         */
        @Schema(description = "第三方用户名")
        String name
) {
}
