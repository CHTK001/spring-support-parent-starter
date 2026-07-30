package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 面板连接定义。
 *
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "面板连接定义")
public class PanelConnectionDefinition {

    /** 连接标识。 */
    @Schema(description = "连接标识")
    private String connectionId;
    /** 连接名称。 */
    @Schema(description = "连接名称")
    private String connectionName;
    /** 连接类型。 */
    @Schema(description = "连接类型")
    private PanelConnectionType connectionType;
    /** 协议。 */
    @Schema(description = "协议")
    private String protocol;
    /** 主机地址。 */
    @Schema(description = "主机地址")
    private String host;
    /** 端口。 */
    @Schema(description = "端口")
    private Integer port;
    /** 数据库名称。 */
    @Schema(description = "数据库名称")
    private String databaseName;
    /** 用户名。 */
    @Schema(description = "用户名")
    private String username;
    /** 密码。 */
    @Schema(description = "密码")
    private String password;
    /** 是否启用。 */
    @Schema(description = "是否启用")
    private boolean enabled;
    /** 扩展属性。 */
    @Schema(description = "扩展属性")
    private Map<String, Object> attributes;
}
