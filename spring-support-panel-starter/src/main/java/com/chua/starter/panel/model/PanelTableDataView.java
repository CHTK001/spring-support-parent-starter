package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 面板表数据视图。
 */
@Data
@Builder
public class PanelTableDataView {

    private List<String> panelColumns;
    private List<Map<String, Object>> panelRows;
    private long panelTotal;
    private long panelPageNum;
    private long panelPageSize;
    private long panelElapsedMillis;
}
