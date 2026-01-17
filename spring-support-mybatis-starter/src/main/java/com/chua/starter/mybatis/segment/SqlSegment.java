package com.chua.starter.mybatis.segment;

import net.sf.jsqlparser.expression.Expression;

/**
 * 权限片段处理器
 * @author CH
 */
public interface SqlSegment {
    /**
     * sql片段
     * @param where where
     * @return 结果
     */
    Expression getSqlSegment(Expression where);
}
