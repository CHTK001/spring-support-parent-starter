package com.chua.starter.panel.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 面板连接类型。
 *
 * @author CH
 */
@Schema(description = "面板连接类型")
public enum PanelConnectionType {
    /** JDBC 连接。 */
    @Schema(description = "JDBC 连接")
    JDBC,
    /** Redis 连接。 */
    @Schema(description = "Redis 连接")
    REDIS
}
