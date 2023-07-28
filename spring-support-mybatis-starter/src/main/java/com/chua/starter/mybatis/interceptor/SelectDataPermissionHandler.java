package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * 数据处理器
 *
 * @author CH
 */
public interface SelectDataPermissionHandler extends DataPermissionHandler {
    /**
     * sql片段
     *
     * @param plainSelect       select
     * @param where             where
     * @param mappedStatementId 片段ID
     * @return 表达式
     */
    Expression getSqlSegment(PlainSelect plainSelect, Expression where, String mappedStatementId);

    @Override
    default Expression getSqlSegment(Expression where, String mappedStatementId) {
        return getSqlSegment(null, where, mappedStatementId);
    }
}
