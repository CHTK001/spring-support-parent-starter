package com.chua.starter.mybatis;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;

/**
 * 无权限判断
 * @author CH
 */
public class EmptyDataPermissionHandler implements DataPermissionHandler {
    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        return where;
    }
}
