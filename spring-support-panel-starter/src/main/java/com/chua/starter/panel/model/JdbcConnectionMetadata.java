package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * JDBC 连接元信息。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "JDBC 连接元信息")
public class JdbcConnectionMetadata {

    /** 连接标识。 */
    @Schema(description = "连接标识")
    private String connectionId;
    /** 主机地址。 */
    @Schema(description = "主机地址")
    private String host;
    /** 端口。 */
    @Schema(description = "端口")
    private Integer port;
    /** 当前 catalog。 */
    @Schema(description = "当前 catalog")
    private String catalog;
    /** 默认 schema。 */
    @Schema(description = "默认 schema")
    private String defaultSchema;
    /** 数据库产品名称。 */
    @Schema(description = "数据库产品名称")
    private String databaseProductName;
    /** 数据库产品版本。 */
    @Schema(description = "数据库产品版本")
    private String databaseProductVersion;
    /** 驱动名称。 */
    @Schema(description = "驱动名称")
    private String driverName;
    /** 驱动版本。 */
    @Schema(description = "驱动版本")
    private String driverVersion;
    /** 扩展属性。 */
    @Schema(description = "扩展属性")
    private Map<String, Object> attributes;
}
