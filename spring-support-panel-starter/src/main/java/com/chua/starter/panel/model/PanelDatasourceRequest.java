package com.chua.starter.panel.model;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Panel 数据源请求。
 *
 * @author CH
 */
@Data
@Schema(description = "Panel 数据源请求")
public class PanelDatasourceRequest {

    /** 数据源主键。 */
    @Schema(description = "数据源主键")
    private String panelSourceId;
    /** 连接标识。 */
    @Schema(description = "连接标识")
    private String panelConnectionId;
    /** 数据源类型。 */
    @Schema(description = "数据源类型")
    private String panelSourceType;
    /** 连接名称。 */
    @Schema(description = "连接名称")
    private String panelConnectionName;
    /** 主机地址。 */
    @Schema(description = "主机地址")
    private String panelHost;
    /** 端口。 */
    @Schema(description = "端口")
    private Integer panelPort;
    /** 数据库名称。 */
    @Schema(description = "数据库名称")
    private String panelDatabaseName;
    /** 用户名。 */
    @Schema(description = "用户名")
    private String panelUsername;
    /** 密码。 */
    @Schema(description = "密码")
    private String panelPassword;
    /** 协议。 */
    @Schema(description = "协议")
    private String panelProtocol;
    /** 方言类型。 */
    @Schema(description = "方言类型")
    private String panelDialectType;
    /** 驱动类名。 */
    @Schema(description = "驱动类名")
    private String panelDriverClassName;
    /** 驱动 Jar 名称。 */
    @Schema(description = "驱动 Jar 名称")
    private String panelDriverJarName;
    /** 驱动 Jar 路径。 */
    @Schema(description = "驱动 Jar 路径")
    private String panelDriverJarPath;
    /** 备注。 */
    @Schema(description = "备注")
    private String panelNote;
    /** 是否收藏。 */
    @Schema(description = "是否收藏")
    private Boolean panelFavorite;
    /** 环境标记。 */
    @Schema(description = "环境标记")
    private String panelEnvironment;
    /** 负责人。 */
    @Schema(description = "负责人")
    private String panelOwner;
    /** 是否只读。 */
    @Schema(description = "是否只读")
    private Boolean panelReadOnly;
    /** 是否启用 SSL。 */
    @Schema(description = "是否启用 SSL")
    private Boolean panelSslEnabled;
    /** 连接超时时间（毫秒）。 */
    @Schema(description = "连接超时时间（毫秒）")
    private Integer panelConnectTimeoutMillis;
    /** 读写超时时间（毫秒）。 */
    @Schema(description = "读写超时时间（毫秒）")
    private Integer panelSocketTimeoutMillis;
    /** 字符集。 */
    @Schema(description = "字符集")
    private String panelCharset;
    /** 最近延迟（毫秒）。 */
    @Schema(description = "最近延迟（毫秒）")
    private Long panelLastLatencyMs;
    /** 最近连接时间。 */
    @Schema(description = "最近连接时间")
    private String panelLastConnectedAt;
    /** 探测到的版本。 */
    @Schema(description = "探测到的版本")
    private String panelDetectedVersion;
    /** 扩展属性。 */
    @Schema(description = "扩展属性")
    private Map<String, Object> panelAttributes;
    /** 更新时间。 */
    @Schema(description = "更新时间")
    private String panelUpdatedAt;
}
