package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

/**
 * 面板表修改结果。
 */
@Data
@Builder
public class PanelTableMutationView {

    private long panelAffectedRows;
    private long panelElapsedMillis;
    private String panelMessage;
}
