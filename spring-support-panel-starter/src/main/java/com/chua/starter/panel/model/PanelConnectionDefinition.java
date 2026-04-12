package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 面板连接定义。
 */
@Data
@Builder
public class PanelConnectionDefinition {

    private String connectionId;
    private String connectionName;
    private PanelConnectionType connectionType;
    private String protocol;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;
    private boolean enabled;
    private Map<String, Object> attributes;
}
