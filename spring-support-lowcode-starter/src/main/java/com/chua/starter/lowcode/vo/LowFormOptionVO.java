package com.chua.starter.lowcode.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 低代码-选项出参，用于表单/页面等下拉框。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "低代码—选项出参")
public class LowFormOptionVO {

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
