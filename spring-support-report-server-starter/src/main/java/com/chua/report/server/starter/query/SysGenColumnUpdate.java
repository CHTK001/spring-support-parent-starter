package com.chua.report.server.starter.query;

import com.chua.report.server.starter.entity.MonitorSysGenColumn;
import lombok.Data;

import java.util.List;

/**
 * @author CH
 */
@Data
public class SysGenColumnUpdate {
    /**
     * 字段
     */
    private List<MonitorSysGenColumn> columns;
    /**
     * 表ID
     */
    private String tabId;
}
