package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * JDBC 对象树节点。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "JDBC 对象树节点")
public class JdbcCatalogNode {

    /** 节点标识。 */
    @Schema(description = "节点标识")
    private String nodeId;
    /** 父节点标识。 */
    @Schema(description = "父节点标识")
    private String parentId;
    /** 节点类型。 */
    @Schema(description = "节点类型")
    private String nodeType;
    /** 节点名称。 */
    @Schema(description = "节点名称")
    private String nodeName;
    /** 描述信息。 */
    @Schema(description = "描述信息")
    private String description;
    /** catalog 名称。 */
    @Schema(description = "catalog 名称")
    private String catalogName;
    /** schema 名称。 */
    @Schema(description = "schema 名称")
    private String schemaName;
    /** 表名。 */
    @Schema(description = "表名")
    private String tableName;
    /** 子节点列表。 */
    @Schema(description = "子节点列表")
    private List<JdbcCatalogNode> children;
}
