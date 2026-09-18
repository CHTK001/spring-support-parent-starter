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
* where注入机构标识
*
* @author CH
* @since 4.0.0
 */
public class WhereDeptRegister extends AbstractDeptRegister {


    /** 当前表 */
    private final Table currentTable;

    /**
    * 创建 wheredept注册 实例
    * @param plainSelect plain选择
    * @param where where
    * @param user 用户
    * @param dataPermission 数据权限
    * @param scopeProperties scope属性
    * @param currentTable 当前table
    */
    public WhereDeptRegister(PlainSelect plainSelect,
                             Expression where,
                             CurrentUser user,
                             DataFilterTypeEnum dataPermission,
                             MybatisPlusDataScopeProperties scopeProperties,
                             Table currentTable) {
        /**
        * 父。
        * @param plainSelect plain选择
        * @param where where
        * @param user 用户
        * @param dataPermission 数据权限
        * @param scopeProperties scope属性
        */
        super(plainSelect, where, user, dataPermission, scopeProperties);
        this.currentTable = currentTable;
        deptTable = currentTable;
    }

    /**
    * 注册
    *
    * @return 注册的结果
    */
    public Expression register() {
        return registerDataScope();
    }

    /**
    * 创建入表达式
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
