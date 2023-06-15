package com.chua.starter.vuesql.pojo;

import lombok.Data;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 打开表
 * @author CH
 */
@Data
public class OpenResult {

    private List<String> columns = Collections.emptyList();
    private Integer total;
    private List<Map<String, Object>> data = Collections.emptyList();

}
