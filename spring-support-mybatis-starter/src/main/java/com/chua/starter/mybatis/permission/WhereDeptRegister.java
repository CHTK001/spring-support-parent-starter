package com.chua.starter.mybatis.permission;

import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * where注入机构ID
 *
 * @author CH
 */
public class WhereDeptRegister extends AbstractDeptRegister {


    private final Table currentTable;

    public WhereDeptRegister(PlainSelect plainSelect,
                             Expression where,
                             CurrentUser user,
                             DataFilterTypeEnum dataPermission,
                             MybatisPlusDataScopeProperties scopeProperties,
                             Table currentTable) {
        super(plainSelect, where, user, dataPermission, scopeProperties);
        this.currentTable = currentTable;
        deptTable = currentTable;
    }

    public Expression register() {
        return registerDataScope();
    }

    /**
     * 创建in表达式
     *
     * @return
     */
    private Expression createInExpression() {
        return new InExpression(
                new Column(currentTable, deptIdColumn),
                new ExpressionList(Arrays.stream(user.getDataPermissionRule().split(","))
                        .map(LongValue::new).collect(Collectors.toList()))
        );
    }

}
