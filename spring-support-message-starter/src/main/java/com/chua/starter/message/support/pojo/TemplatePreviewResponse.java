package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 模板预览响应
 *
 * @param content          渲染后的模板内容
 * @param unresolvedParams 模板中未被填充的占位符列表
 * @param complete         模板内容是否完整（所有占位符均被填充）
 * @since 2026/08/03
 * @author CH
 */
@Schema(description = "模板预览响应")
public record TemplatePreviewResponse(
        @Schema(description = "渲染后的模板内容") String content,
        @Schema(description = "模板中未被填充的占位符列表") List<String> unresolvedParams,
        @Schema(description = "模板内容是否完整") boolean complete) {
}
