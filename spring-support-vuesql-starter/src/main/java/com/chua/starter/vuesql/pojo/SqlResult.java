package com.chua.starter.vuesql.pojo;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author CH
 */
@Data
public class SqlResult {

    private List<Column> columns = Collections.emptyList();

    private Integer total = Integer.MAX_VALUE;

    private List<Map<String, Object>> data= Collections.emptyList();

    private String rsType = "sql";
}
