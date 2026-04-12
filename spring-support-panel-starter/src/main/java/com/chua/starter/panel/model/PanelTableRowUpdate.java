package com.chua.starter.panel.model;

import lombok.Data;

import java.util.Map;

/**
 * 面板表行更新项。
 */
@Data
public class PanelTableRowUpdate {

    private Map<String, Object> panelOriginalRow;
    private Map<String, Object> panelCurrentRow;
}
