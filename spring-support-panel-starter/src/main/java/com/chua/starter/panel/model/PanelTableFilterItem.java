package com.chua.starter.panel.model;

import lombok.Data;

/**
 * 面板表格筛选项。
 */
@Data
public class PanelTableFilterItem {

    private String panelColumnName;
    private String panelOperator;
    private String panelValue;
}
