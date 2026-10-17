package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 模板预览请求
 *
 * @param sysMessageTemplateId 模板ID
 * @param params               模板参数
 * @since 2026/08/03
 * @author CH
 */
@Schema(description = "模板预览请求")
public record TemplatePreviewRequest(
        @Schema(description = "模板ID") Integer sysMessageTemplateId,
        @Schema(description = "模板参数") Map<String, Object> params) {
}
