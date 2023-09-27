package com.chua.starter.gen.support.query;

import com.chua.starter.gen.support.entity.SysGenColumn;
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
    private List<SysGenColumn> columns;
    /**
     * 表ID
     */
    private String tabId;
}
