package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 面板备注视图。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "面板备注视图")
public class PanelRemarkView {

    /** 备注键。 */
    @Schema(description = "备注键")
    private String panelRemarkKey;
    /** 连接标识。 */
    @Schema(description = "连接标识")
    private String panelConnectionId;
    /** 节点类型。 */
    @Schema(description = "节点类型")
    private String panelNodeType;
    /** catalog 名称。 */
    @Schema(description = "catalog 名称")
    private String panelCatalogName;
    /** schema 名称。 */
    @Schema(description = "schema 名称")
    private String panelSchemaName;
    /** 表名。 */
    @Schema(description = "表名")
    private String panelTableName;
    /** 列名。 */
    @Schema(description = "列名")
    private String panelColumnName;
    /** 备注内容。 */
    @Schema(description = "备注内容")
    private String panelRemarkContent;
}
