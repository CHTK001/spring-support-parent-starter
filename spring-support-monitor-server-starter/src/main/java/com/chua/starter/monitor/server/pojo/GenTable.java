package com.chua.starter.monitor.server.pojo;

import com.chua.common.support.datasource.meta.Table;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author CH
 * @since 2024/8/2
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonIgnoreProperties(value = {"tableSharding", "databaseSharding", "javaType"}, ignoreUnknown = true)
public class GenTable extends Table {
    private String genId;
}
