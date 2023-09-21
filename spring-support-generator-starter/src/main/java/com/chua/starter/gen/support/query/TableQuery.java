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
    private String dataSourceId;
    /**
     * 名称
     */
    private String dataSourceName;

    /**
     * 表名称
     */
    private String tableName;
}
