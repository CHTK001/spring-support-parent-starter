package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * JDBC 表结构。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "JDBC 表结构")
public class JdbcTableStructure {

    /** catalog 名称。 */
    @Schema(description = "catalog 名称")
    private String catalogName;
    /** schema 名称。 */
    @Schema(description = "schema 名称")
    private String schemaName;
    /** 表名。 */
    @Schema(description = "表名")
    private String tableName;
    /** 表注释。 */
    @Schema(description = "表注释")
    private String tableComment;
    /** 建表 DDL 文本。 */
    @Schema(description = "建表 DDL 文本")
    private String ddlText;
    /** 列信息列表。 */
    @Schema(description = "列信息列表")
    private List<Map<String, Object>> columns;
    /** 索引信息列表。 */
    @Schema(description = "索引信息列表")
    private List<Map<String, Object>> indexes;
    /** 触发器信息列表。 */
    @Schema(description = "触发器信息列表")
    private List<Map<String, Object>> triggers;
    /** 主键列表。 */
    @Schema(description = "主键列表")
    private List<String> primaryKeys;
}
