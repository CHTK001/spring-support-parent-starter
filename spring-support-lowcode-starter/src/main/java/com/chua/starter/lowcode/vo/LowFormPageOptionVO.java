package com.chua.starter.lowcode.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 低代码-页面选项出参，用于页面等下拉框。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@Schema(description = "低代码—页面选项出参")
public class LowFormPageOptionVO {

    /**
     * 选项标识。
     */
    @Schema(description = "选项标识")
    private String value;

    /**
     * 选项名称。
     */
    @Schema(description = "选项名称")
    private String label;
}
