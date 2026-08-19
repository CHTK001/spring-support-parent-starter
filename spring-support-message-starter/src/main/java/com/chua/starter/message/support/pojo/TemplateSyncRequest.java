package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 模板同步请求
 *
 * @since 2026/08/03
 * @author CH
 */
@Data
@Schema(description = "模板同步请求")
public class TemplateSyncRequest {

    /**
     * 提供商ID
     */
    @Schema(description = "提供商ID")
    /** SYS消息提供者ID */
    private Integer sysMessageProviderId;
}
