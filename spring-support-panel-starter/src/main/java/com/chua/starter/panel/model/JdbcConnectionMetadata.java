package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * JDBC 连接元信息。
 */
@Data
@Builder
public class JdbcConnectionMetadata {

    private String connectionId;
    private String host;
    private Integer port;
    private String catalog;
    private String defaultSchema;
    private String databaseProductName;
    private String databaseProductVersion;
    private String driverName;
    private String driverVersion;
    private Map<String, Object> attributes;
}
