package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 模板同步请求
 *
 * @param sysMessageProviderId SYS消息提供者标识
 *
 * @since 2026/08/03
 * @author CH
 */
@Schema(description = "模板同步请求")
public record TemplateSyncRequest(
        @Schema(description = "提供商ID") Integer sysMessageProviderId
) {
}
