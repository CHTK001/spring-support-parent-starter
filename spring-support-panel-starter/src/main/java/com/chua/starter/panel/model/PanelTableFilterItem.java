package com.chua.starter.panel.model;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 面板表格筛选项。
 *
 * @author CH
 */
@Data
@Schema(description = "面板表格筛选项")
public class PanelTableFilterItem {

    /** 列名。 */
    @Schema(description = "列名")
    private String panelColumnName;
    /** 操作符。 */
    @Schema(description = "操作符")
    private String panelOperator;
    /** 比较值。 */
    @Schema(description = "比较值")
    private String panelValue;
}
