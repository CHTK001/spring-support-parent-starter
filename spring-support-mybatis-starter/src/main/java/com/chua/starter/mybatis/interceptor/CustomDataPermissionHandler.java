package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.mybatis.oauth.AuthService;
import com.chua.starter.mybatis.oauth.CurrentUser;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.HexValue;

import javax.annotation.Resource;

/**
 * 数据处理器
 * @author CH
 */
public class CustomDataPermissionHandler implements DataPermissionHandler {

    @Resource
    private AuthService authService;
    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        CurrentUser currentUser = authService.getCurrentUser();
        DataFilterTypeEnum dataFilterTypeEnum = currentUser.getDataPermissionEnum();
        if(null == dataFilterTypeEnum) {
            return where;
        }

        Expression expression = new HexValue(" 1 = 1 ");
        if (where == null) {
            where = expression;
        }

        return null;
    }
}
