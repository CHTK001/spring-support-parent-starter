package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chua.common.support.lang.exception.AuthenticationException;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.mybatis.oauth.AuthService;
import com.chua.starter.mybatis.oauth.CurrentUser;
import com.chua.starter.mybatis.segment.SqlSegment;
import com.google.common.base.Joiner;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.HexValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.apache.commons.lang3.ArrayUtils;

import javax.annotation.Resource;

/**
 * 数据处理器
 * @author CH
 */
public class CustomDataPermissionHandler implements SelectDataPermissionHandler {

    private static final String CREATE_BY = "rule_create_by";
    private static final String DEPT_ID = "rule_dept_id";
    @Resource
    private AuthService authService;

    @Override
    public Expression getSqlSegment(PlainSelect plainSelect, Expression where, String mappedStatementId) {
        SqlSegment sqlSegment = ServiceProvider.of(SqlSegment.class).getNewExtension(mappedStatementId);
        if(null != sqlSegment) {
            return sqlSegment.getSqlSegment(where);
        }

        CurrentUser currentUser = authService.getCurrentUser();

        DataFilterTypeEnum dataFilterTypeEnum = currentUser.getDataPermissionEnum();
        if(null == dataFilterTypeEnum) {
            return where;
        }

        Expression expression = new HexValue(" 1 = 1 ");
        if (where == null) {
            where = expression;
        }

        if(dataFilterTypeEnum == DataFilterTypeEnum.ALL) {
            return where;
        }

        if(dataFilterTypeEnum == DataFilterTypeEnum.SELF) {
            String userId = currentUser.getExtValue("userId");
            if(plainSelect.getSelectItems().stream().map(SelectItem::toString).anyMatch(it -> it.equals(CREATE_BY))) {
                return new AndExpression(where, new HexValue(CREATE_BY + " = " + userId));
            }
            return where;
        }

        if(dataFilterTypeEnum == DataFilterTypeEnum.DEPT) {
            String deptId = currentUser.getExtValue("deptId");
            if(plainSelect.getSelectItems().stream().map(SelectItem::toString).anyMatch(it -> it.equals(DEPT_ID))) {
                return new AndExpression(where, new HexValue(DEPT_ID + " = " + deptId));
            }
            return where;
        }

        if(dataFilterTypeEnum == DataFilterTypeEnum.DEPT_AND_SUB) {
            String deptId = currentUser.getExtValue("deptId");
            if(plainSelect.getSelectItems().stream().map(SelectItem::toString).anyMatch(it -> it.equals(DEPT_ID))) {
                return new AndExpression(where, new HexValue(DEPT_ID + " IN (SELECT deptId from sys_dept WHERE FIND_IN_SET('"+ deptId +"', dept_tree_path"));
            }
            return where;
        }

        if(dataFilterTypeEnum == DataFilterTypeEnum.DEPT_SETS) {
            String[] deptId = currentUser.getDataPermissionRule().split(",");
            if(ArrayUtils.isEmpty(deptId)) {
                throw new AuthenticationException("暂无权限");
            }
            if(plainSelect.getSelectItems().stream().map(SelectItem::toString).anyMatch(it -> it.equals(DEPT_ID))) {
                String join = Joiner.on("','").join(deptId);
                return new AndExpression(where, new HexValue(DEPT_ID + " IN ('"+ join +"')"));
            }
            return where;
        }
        if(dataFilterTypeEnum == DataFilterTypeEnum.CUSTOM) {
            String dataPermissionRule = currentUser.getDataPermissionRule();
            if(StringUtils.isEmpty(dataPermissionRule)) {
                throw new AuthenticationException("暂无权限");
            }
            return new AndExpression(where, new HexValue(dataPermissionRule));
        }

        return null;
    }



}
