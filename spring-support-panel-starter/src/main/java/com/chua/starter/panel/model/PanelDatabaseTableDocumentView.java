package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * 面板数据库表文档视图。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "面板数据库表文档视图")
public class PanelDatabaseTableDocumentView {

    /** catalog 名称。 */
    @Schema(description = "catalog 名称")
    private String panelCatalogName;
    /** schema 名称。 */
    @Schema(description = "schema 名称")
    private String panelSchemaName;
    /** 表名。 */
    @Schema(description = "表名")
    private String panelTableName;
    /** 表注释。 */
    @Schema(description = "表注释")
    private String panelTableComment;
    /** 主键列表。 */
    @Schema(description = "主键列表")
    private List<String> panelPrimaryKeys;
    /** 列定义列表。 */
    @Schema(description = "列定义列表")
    private List<Map<String, Object>> panelColumns;
    /** 索引定义列表。 */
    @Schema(description = "索引定义列表")
    private List<Map<String, Object>> panelIndexes;
}
