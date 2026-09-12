package com.chua.starter.message.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 模板预览响应
 *
 * @since 2026/08/03
 * @author CH
 */
@Data
@Builder
@Schema(description = "模板预览响应")
public class TemplatePreviewResponse {

    /**
     * 渲染后的模板内容
     */
    @Schema(description = "渲染后的模板内容")
    /** 内容 */
    private String content;

    /**
     * 模板中未被填充的占位符列表
     */
    @Schema(description = "模板中未被填充的占位符列表")
    /** Unresolved参数 */
    private java.util.List<String> unresolvedParams;

    /**
     * 模板内容是否完整（所有占位符均被填充）
     */
    @Schema(description = "模板内容是否完整（所有占位符均被填充）")
    /** 完成 */
    private boolean complete;
}
