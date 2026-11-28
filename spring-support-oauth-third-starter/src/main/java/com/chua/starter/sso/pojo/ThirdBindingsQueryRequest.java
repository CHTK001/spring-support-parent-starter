package com.chua.starter.sso.pojo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 批量查询用户第三方绑定请求
 *
 * @since 2026/11/24
 * @author CH
 */
@Schema(description = "批量查询用户第三方绑定请求")
public record ThirdBindingsQueryRequest(
        /**
         * 本地用户标识列表
         */
        @Schema(description = "本地用户ID列表")
        List<Integer> userIds
) {
}
