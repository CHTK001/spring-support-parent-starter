package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 模板同步请求
 *
 * @author CH
 * @since 2026/08/03
 */
@Data
@Schema(description = "模板同步请求")
public class TemplateSyncRequest {

    /**
     * 提供商ID
     */
    @Schema(description = "提供商ID")
    private Integer sysMessageProviderId;
}
