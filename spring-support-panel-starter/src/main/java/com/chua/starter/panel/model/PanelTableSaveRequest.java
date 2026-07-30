package com.chua.starter.panel.model;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 面板表保存请求。
 *
 * @author CH
 */
@Data
@Schema(description = "面板表保存请求")
public class PanelTableSaveRequest {

    /** catalog 名称。 */
    @Schema(description = "catalog 名称")
    private String panelCatalogName;
    /** schema 名称。 */
    @Schema(description = "schema 名称")
    private String panelSchemaName;
    /** 表名。 */
    @Schema(description = "表名")
    private String panelTableName;
    /** 行更新项列表。 */
    @Schema(description = "行更新项列表")
    private List<PanelTableRowUpdate> panelUpdates = new ArrayList<>();
}
