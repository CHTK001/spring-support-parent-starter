package com.chua.starter.panel.model;

import lombok.Data;

/**
 * Panel 数据源请求。
 */
@Data
public class PanelDatasourceRequest {

    private String panelSourceId;
    private String panelConnectionId;
    private String panelSourceType;
    private String panelConnectionName;
    private String panelHost;
    private Integer panelPort;
    private String panelDatabaseName;
    private String panelUsername;
    private String panelPassword;
    private String panelProtocol;
    private String panelNote;
    private Boolean panelFavorite;
    private String panelUpdatedAt;
}
