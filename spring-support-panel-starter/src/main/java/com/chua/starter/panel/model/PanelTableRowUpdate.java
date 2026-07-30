package com.chua.starter.panel.model;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 面板表行更新项。
 *
 * @author CH
 */
@Data
@Schema(description = "面板表行更新项")
public class PanelTableRowUpdate {

    /** 原始行数据。 */
    @Schema(description = "原始行数据")
    private Map<String, Object> panelOriginalRow;
    /** 当前行数据。 */
    @Schema(description = "当前行数据")
    private Map<String, Object> panelCurrentRow;
}
