package com.chua.starter.vuesql.pojo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author CH
 */
@Data
public class SqlResult {

    private List<String> columns;

    private Integer total;

    private List<Map<String, Object>> data;

    private String rsType = "sql";
}
