package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

/**
 * Panel 数据源视图。
 */
@Data
@Builder
public class PanelDatasourceView {

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
