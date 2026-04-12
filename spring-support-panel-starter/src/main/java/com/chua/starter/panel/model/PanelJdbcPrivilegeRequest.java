package com.chua.starter.panel.model;

import lombok.Data;

import java.util.List;

/**
 * 面板账号权限请求。
 */
@Data
public class PanelJdbcPrivilegeRequest {

    private String panelAccountName;
    private String panelHost;
    private List<String> panelPrivileges;
    private String panelCatalogName;
    private String panelTableName;
    private boolean panelGrantOption;
}
