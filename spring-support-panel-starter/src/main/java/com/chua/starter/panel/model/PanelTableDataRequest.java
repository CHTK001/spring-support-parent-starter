package com.chua.starter.panel.model;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 面板表数据请求。
 *
 * @author CH
 */
@Data
@Schema(description = "面板表数据请求")
public class PanelTableDataRequest {

    /** catalog 名称。 */
    @Schema(description = "catalog 名称")
    private String panelCatalogName;
    /** schema 名称。 */
    @Schema(description = "schema 名称")
    private String panelSchemaName;
    /** 表名。 */
    @Schema(description = "表名")
    private String panelTableName;
    /** 页码。 */
    @Schema(description = "页码")
    private long panelPageNum = 1;
    /** 每页大小。 */
    @Schema(description = "每页大小")
    private long panelPageSize = 100;
    /** 是否加载总数。 */
    @Schema(description = "是否加载总数")
    private boolean panelLoadTotal;
    /** 模糊筛选关键字。 */
    @Schema(description = "模糊筛选关键字")
    private String panelFilterKeyword;
    /** 筛选连接方式。 */
    @Schema(description = "筛选连接方式")
    private String panelFilterJoin = "and";
    /** 筛选条件列表。 */
    @Schema(description = "筛选条件列表")
    private List<PanelTableFilterItem> panelFilters = new ArrayList<>();
    /** 排序字段。 */
    @Schema(description = "排序字段")
    private String panelSortField;
    /** 排序顺序。 */
    @Schema(description = "排序顺序")
    private String panelSortOrder;
}
