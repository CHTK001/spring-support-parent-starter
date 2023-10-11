package com.chua.starter.gen.support.query;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.session.query.SessionQuery;
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
     * id
     */
    private String databaseId;

    /**
     * 表名称
     */
    private String[] tableName;
    /**
     * 关键字
     */
    private String keyword;


    public SessionQuery createSessionQuery() {
        return BeanUtils.copyProperties(this, SessionQuery.class);
    }
}
