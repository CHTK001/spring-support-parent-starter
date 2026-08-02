package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 模板预览请求
 *
 * @author CH
 * @since 2026/08/03
 */
@Data
@Schema(description = "模板预览请求")
public class TemplatePreviewRequest {

    /**
     * 模板ID
     */
    @Schema(description = "模板ID")
    private Integer sysMessageTemplateId;

    /**
     * 模板参数
     */
    @Schema(description = "模板参数")
    private java.util.Map<String, Object> params;
}
