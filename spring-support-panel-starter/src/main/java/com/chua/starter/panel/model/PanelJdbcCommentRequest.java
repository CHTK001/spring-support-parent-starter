package com.chua.starter.panel.model;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JDBC 注释更新请求。
 *
 * @author CH
 */
@Data
@Schema(description = "JDBC 注释更新请求")
public class PanelJdbcCommentRequest {

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
    /** 注释内容。 */
    @Schema(description = "注释内容")
    private String panelCommentContent;
}
