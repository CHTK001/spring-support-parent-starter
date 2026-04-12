package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 面板数据库账号视图。
 */
@Data
@Builder
public class PanelJdbcAccountView {

    private String panelAccountName;
    private String panelHost;
    private List<String> panelGrants;
}
