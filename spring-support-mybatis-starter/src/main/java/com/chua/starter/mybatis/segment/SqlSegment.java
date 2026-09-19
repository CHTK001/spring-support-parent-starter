package com.chua.starter.mybatis.segment;

import net.sf.jsqlparser.expression.Expression;

/**
 * 权限片段处理器
 * @author CH
 * @since 4.0.0
 */
public interface SqlSegment {
    /**
     * SQL片段
     * @param where where
     * @return 结果
     */
    Expression getSqlSegment(Expression where);
}
