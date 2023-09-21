package com.chua.starter.gen.support.query;

import com.chua.starter.mybatis.entity.RequestPage;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表查询
 *
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class TableQuery extends RequestPage<TableQuery> {

    /**
     * id
     */
    private Integer genId;
    /**
     * 名称
     */
    private String dataSourceName;

    /**
     * 表名称
     */
    private String[] tableName;
}
