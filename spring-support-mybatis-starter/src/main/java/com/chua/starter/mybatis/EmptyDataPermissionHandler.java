package com.chua.starter.mybatis;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;

/**
 * 空数据权限处理器
 * 不进行任何数据权限过滤，直接返回原始查询条件
 *
 * @author CH
 */
public class EmptyDataPermissionHandler implements DataPermissionHandler {

    /**
     * 获取 SQL 片段
     * 不进行任何处理，直接返回原始查询条件
     *
     * @param where            当前查询条件
     * @param mappedStatementId Mapper 方法全限定名
     * @return 原始查询条件
     */
    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        return where;
    }
}
