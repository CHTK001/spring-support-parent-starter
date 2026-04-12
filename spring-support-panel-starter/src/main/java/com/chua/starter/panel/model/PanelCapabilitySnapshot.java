package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

/**
 * 面板能力快照。
 */
@Data
@Builder
public class PanelCapabilitySnapshot {

    private boolean jdbcEnabled;
    private boolean documentEnabled;
    private boolean aiEnabled;
    private boolean aiStarterEnabled;
    private String message;
}
