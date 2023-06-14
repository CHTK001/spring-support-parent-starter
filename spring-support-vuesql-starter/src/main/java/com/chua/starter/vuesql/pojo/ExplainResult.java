package com.chua.starter.vuesql.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ExplainResult extends SqlResult {

    public ExplainResult() {
        this.setRsType("explain");
    }
}
