package com.chua.starter.panel.model;

import lombok.Data;

/**
 * 面板账号保存请求。
 */
@Data
public class PanelJdbcAccountSaveRequest {

    private String panelAccountName;
    private String panelHost;
    private String panelPassword;
}
