package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * 面板表数据视图。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "面板表数据视图")
public class PanelTableDataView {

    /** 列名列表。 */
    @Schema(description = "列名列表")
    private List<String> panelColumns;
    /** 行数据列表。 */
    @Schema(description = "行数据列表")
    private List<Map<String, Object>> panelRows;
    /** 总记录数。 */
    @Schema(description = "总记录数")
    private long panelTotal;
    /** 当前页码。 */
    @Schema(description = "当前页码")
    private long panelPageNum;
    /** 当前页大小。 */
    @Schema(description = "当前页大小")
    private long panelPageSize;
    /** 执行耗时（毫秒）。 */
    @Schema(description = "执行耗时（毫秒）")
    private long panelElapsedMillis;
}
