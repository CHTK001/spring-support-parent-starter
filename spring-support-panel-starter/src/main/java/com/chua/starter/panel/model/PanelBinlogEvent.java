package com.chua.starter.panel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Binlog 变更事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PanelBinlogEvent {

    private String type;
    private String database;
    private String table;
    private String sql;
    private long timestamp;
}
