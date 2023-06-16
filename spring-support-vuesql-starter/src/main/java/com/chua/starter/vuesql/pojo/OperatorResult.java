package com.chua.starter.vuesql.pojo;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 打开表
 *
 * @author CH
 */
@Data
public class OperatorResult<T> {

    private List<Column> columns = Collections.emptyList();
    private boolean status;
    private List<T> data = Collections.emptyList();

}
