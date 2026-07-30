package com.chua.starter.panel.model;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 面板备注写入请求。
 *
 * @author CH
 */
@Data
@Schema(description = "面板备注写入请求")
public class PanelRemarkRequest {

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
