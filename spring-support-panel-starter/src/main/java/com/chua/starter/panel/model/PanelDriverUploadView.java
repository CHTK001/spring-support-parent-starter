package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

/**
 * 上传 JDBC 驱动返回结果。
 */
@Data
@Builder
public class PanelDriverUploadView {

    private String panelDialectType;
    private String panelDriverClassName;
    private String panelDriverJarName;
    private String panelDriverJarPath;
}
