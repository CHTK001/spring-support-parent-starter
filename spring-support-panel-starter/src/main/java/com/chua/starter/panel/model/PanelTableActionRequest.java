package com.chua.starter.panel.model;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 面板表动作请求。
 *
 * @author CH
 */
@Data
@Schema(description = "面板表动作请求")
public class PanelTableActionRequest {

    /** catalog 名称。 */
    @Schema(description = "catalog 名称")
    private String panelCatalogName;
    /** schema 名称。 */
    @Schema(description = "schema 名称")
    private String panelSchemaName;
    /** 表名。 */
    @Schema(description = "表名")
    private String panelTableName;
    /** 动作类型。 */
    @Schema(description = "动作类型")
    private String panelActionType;
    /** 备份表名。 */
    @Schema(description = "备份表名")
    private String panelBackupTableName;
}
