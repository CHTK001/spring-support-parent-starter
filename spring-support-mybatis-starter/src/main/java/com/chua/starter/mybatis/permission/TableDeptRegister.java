package com.chua.starter.mybatis.permission;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.chua.starter.mybatis.interceptor.MybatisPlusPermissionHandler.NO_DATA;

/**
 * 数据权限注册器
 * <p>支持多表关联场景，通过deptAlias和userAlias指定表别名</p>
 *
 * @author CH
 */
public class TableDeptRegister implements DeptRegister {
    private static final String DEPT_LEADER = "DEPT_LEADER";
    private static final String MANAGED_DEPT_TREE_IDS = "managedDeptTreeIds";

    private final Table table;
    private final Expression where;
    private final CurrentUser currentUser;
    private final DataFilterTypeEnum dataPermission;
    private final MybatisPlusDataScopeProperties dataScopeProperties;
    private final String deptIdColumn;
    private final String createByColumn;
    private final String deptTreeIdColumn;
    private final String deptAlias;
    private final String userAlias;

    /**
     * 构造函数
     *
     * @param table               表对象
     * @param where               WHERE条件表达式
     * @param currentUser         当前用户信息
     * @param dataPermission      数据权限类型
     * @param dataScopeProperties 数据权限配置
     * @param deptAlias           部门表别名，用于多表关联场景
     * @param userAlias           用户表别名，用于多表关联场景
     */
    public TableDeptRegister(Table table, Expression where, CurrentUser currentUser, DataFilterTypeEnum dataPermission, MybatisPlusDataScopeProperties dataScopeProperties, String deptAlias, String userAlias) {
        this.table = table;
        this.where = where;
        this.currentUser = currentUser;
        this.dataPermission = dataPermission;
        this.dataScopeProperties = dataScopeProperties;
        this.deptIdColumn = dataScopeProperties.getDeptIdColumn();
        this.createByColumn = dataScopeProperties.getCurrentUserIdColumn();
        this.deptTreeIdColumn = dataScopeProperties.getDeptTreeIdColumn();
        this.deptAlias = deptAlias;
        this.userAlias = userAlias;
    }

    /**
     * 注册数据权限过滤条件
     * <p>根据数据权限类型创建相应的WHERE条件表达式</p>
     *
     * @return 权限过滤表达式，如果表信息不存在或表没有部门ID字段则返回null
     */
    @Override
    public Expression register() {
        TableInfo tableInfo = getTableInfo(table.getName());
        if (null == tableInfo) {
            return null;
        }
        if (!matchesScopedAlias()) {
            return null;
        }
        return createExpression(tableInfo);
    }

    /**
     * 创建权限过滤表达式
     * <p>首先检查表是否有部门ID字段，如果没有则返回null（不进行权限过滤）</p>
     *
     * @param tableInfo 表信息
     * @return 权限过滤表达式，如果表没有部门ID字段则返回null
     */
    private Expression createExpression(TableInfo tableInfo) {
        boolean deptIdFromTable = findDeptIdFromTable(tableInfo);
        if (!deptIdFromTable) {
            return null;
        }

        return createDeptWhere();
    }

    /**
     * 创建部门表达式
     *
     * @return 表达式，如果where为null则直接返回权限条件，否则返回AND组合
     */
    private Expression createDeptWhere() {
        if (hasManagedDeptLeaderScope()) {
            return combineWithWhere(createManagedDeptLeaderExpression());
        }

        if (DataFilterTypeEnum.DEPT_SETS == dataPermission) {
            if (StringUtils.isEmpty(currentUser.getDataPermissionRule())) {
                return NO_DATA;
            }

            Expression inExpression = createInExpression();
            return combineWithWhere(inExpression);
        }

        if (DataFilterTypeEnum.DEPT == dataPermission) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(createDeptColumn());
            equalsTo.setRightExpression(new LongValue(currentUser.getDeptId()));
            return combineWithWhere(equalsTo);
        }

        if (DataFilterTypeEnum.DEPT_AND_SUB == dataPermission) {
            // 使用 LIKE 前缀查询替代 find_in_set，可以利用索引
            // SQL: sys_dept_id IN (SELECT sys_dept_id FROM sys_dept WHERE sys_dept_tree_id LIKE 'xxx%')
            InExpression inExpression = new InExpression();
            inExpression.setLeftExpression(createDeptColumn());

            ParenthesedSelect subSelect = new ParenthesedSelect();
            PlainSelect select = new PlainSelect();
            select.setSelectItems(Collections.singletonList(new SelectItem<>(new Column(deptIdColumn))));

            // 使用配置的部门表
            Table deptTable = new Table(dataScopeProperties.getTableName());
            select.setFromItem(deptTable);

            // 构建 LIKE 表达式: sys_dept_tree_id LIKE 'currentDeptTreeId%'
            LikeExpression likeExpression = new LikeExpression();
            likeExpression.setLeftExpression(new Column(deptTreeIdColumn));
            // 需要获取当前部门的 tree_id，这里用子查询
            PlainSelect treeIdSelect = new PlainSelect();
            treeIdSelect.setSelectItems(Collections.singletonList(new SelectItem<>(new Column(deptTreeIdColumn))));
            treeIdSelect.setFromItem(deptTable);
            EqualsTo deptIdEquals = new EqualsTo();
            deptIdEquals.setLeftExpression(new Column(deptIdColumn));
            deptIdEquals.setRightExpression(new LongValue(currentUser.getDeptId()));
            treeIdSelect.setWhere(deptIdEquals);
            ParenthesedSelect treeIdSubSelect = new ParenthesedSelect();
            treeIdSubSelect.setSelect(treeIdSelect);

            // CONCAT(子查询, '%')
            net.sf.jsqlparser.expression.Function concatFunc = new net.sf.jsqlparser.expression.Function();
            concatFunc.setName("CONCAT");
            concatFunc.setParameters(new ExpressionList<>(treeIdSubSelect, new StringValue("%")));
            likeExpression.setRightExpression(concatFunc);

            select.setWhere(likeExpression);
            subSelect.setSelect(select);
            inExpression.setRightExpression(subSelect);

            return combineWithWhere(inExpression);
        }

        if (DataFilterTypeEnum.SELF == dataPermission) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(createUserColumn());
            equalsTo.setRightExpression(new StringValue(currentUser.getUserId()));
            return combineWithWhere(equalsTo);
        }

        if (DataFilterTypeEnum.CUSTOM == dataPermission) {
            // 自定义权限: 本人数据 + 自定义部门数据
            // SQL: (create_by = userId OR sys_dept_id IN (deptIds))
            EqualsTo selfCondition = new EqualsTo();
            selfCondition.setLeftExpression(createUserColumn());
            selfCondition.setRightExpression(new StringValue(currentUser.getUserId()));

            if (StringUtils.isNotEmpty(currentUser.getDataPermissionRule())) {
                // 有自定义部门列表
                Expression deptInExpression = createInExpression();
                OrExpression orExpression = new OrExpression(selfCondition, deptInExpression);
                return combineWithWhere(orExpression);
            } else {
                // 没有自定义部门，只看本人数据
                return combineWithWhere(selfCondition);
            }
        }

        return NO_DATA;
    }

    private boolean hasManagedDeptLeaderScope() {
        return currentUser.hasRole(DEPT_LEADER) && !resolveManagedDeptTreeIds().isEmpty();
    }

    private Expression createManagedDeptLeaderExpression() {
        EqualsTo selfCondition = new EqualsTo();
        selfCondition.setLeftExpression(createUserColumn());
        selfCondition.setRightExpression(new StringValue(currentUser.getUserId()));

        List<String> managedDeptTreeIds = resolveManagedDeptTreeIds();
        List<Expression> deptConditions = new ArrayList<>(managedDeptTreeIds.size());
        for (String treeId : managedDeptTreeIds) {
            InExpression inExpression = new InExpression();
            inExpression.setLeftExpression(createDeptColumn());

            ParenthesedSelect subSelect = new ParenthesedSelect();
            PlainSelect select = new PlainSelect();
            select.setSelectItems(Collections.singletonList(new SelectItem<>(new Column(deptIdColumn))));

            Table deptTable = new Table(dataScopeProperties.getTableName());
            select.setFromItem(deptTable);

            LikeExpression likeExpression = new LikeExpression();
            likeExpression.setLeftExpression(new Column(deptTreeIdColumn));
            net.sf.jsqlparser.expression.Function concatFunc = new net.sf.jsqlparser.expression.Function();
            concatFunc.setName("CONCAT");
            concatFunc.setParameters(new ExpressionList<>(new StringValue(treeId), new StringValue("%")));
            likeExpression.setRightExpression(concatFunc);
            select.setWhere(likeExpression);

            subSelect.setSelect(select);
            inExpression.setRightExpression(subSelect);
            deptConditions.add(inExpression);
        }

        Expression deptCondition = null;
        for (Expression expression : deptConditions) {
            deptCondition = null == deptCondition ? expression : new OrExpression(deptCondition, expression);
        }

        return null == deptCondition ? selfCondition : new OrExpression(selfCondition, deptCondition);
    }

    /**
     * 将权限条件与现有WHERE条件组合
     *
     * @param permissionCondition 权限条件表达式
     * @return 组合后的表达式
     */
    private Expression combineWithWhere(Expression permissionCondition) {
        Expression normalizedPermissionCondition = wrapOrExpression(permissionCondition);
        if (null == where) {
            return normalizedPermissionCondition;
        }
        return new AndExpression(where, normalizedPermissionCondition);
    }

    private Expression wrapOrExpression(Expression expression) {
        if (!(expression instanceof OrExpression)) {
            return expression;
        }
        try {
            return CCJSqlParserUtil.parseCondExpression("(" + expression + ")");
        } catch (JSQLParserException ignored) {
            return expression;
        }
    }

    private boolean matchesScopedAlias() {
        if (!hasScopedAlias()) {
            return true;
        }
        String currentAlias = currentTableAlias();
        return matchesAlias(currentAlias, deptAlias) || matchesAlias(currentAlias, userAlias);
    }

    private boolean hasScopedAlias() {
        return StringUtils.isNotEmpty(deptAlias) || StringUtils.isNotEmpty(userAlias);
    }

    private String currentTableAlias() {
        if (table.getAlias() != null && StringUtils.isNotEmpty(table.getAlias().getName())) {
            return table.getAlias().getName();
        }
        return table.getName();
    }

    private boolean matchesAlias(String currentAlias, String expectedAlias) {
        return StringUtils.isNotEmpty(currentAlias)
                && StringUtils.isNotEmpty(expectedAlias)
                && currentAlias.equalsIgnoreCase(expectedAlias);
    }

    /**
     * 创建部门ID列，支持表别名
     *
     * @return 列对象
     */
    private Column createDeptColumn() {
        if (deptAlias != null && !deptAlias.isEmpty()) {
            Table aliasTable = new Table(deptAlias);
            return new Column(aliasTable, deptIdColumn);
        }
        return new Column(table, deptIdColumn);
    }

    /**
     * 创建用户ID列，支持表别名
     *
     * @return 列对象
     */
    private Column createUserColumn() {
        if (userAlias != null && !userAlias.isEmpty()) {
            Table aliasTable = new Table(userAlias);
            return new Column(aliasTable, createByColumn);
        }
        return new Column(table, createByColumn);
    }

    /**
     * 查找表是否有部门ID字段
     * <p>用于判断是否需要应用数据权限过滤</p>
     *
     * @param tableInfo 表信息
     * @return 如果表包含部门ID字段则返回true，否则返回false
     */
    private boolean findDeptIdFromTable(TableInfo tableInfo) {
        for (TableFieldInfo tableFieldInfo : tableInfo.getFieldList()) {
            if (tableFieldInfo.getColumn().equalsIgnoreCase(deptIdColumn)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 创建IN表达式，用于部门ID列表查询
     *
     * @return IN表达式
     */
    private Expression createInExpression() {
        return new InExpression(
                createDeptColumn(),
                new ExpressionList<>(Arrays.stream(currentUser.getDataPermissionRule().split(","))
                        .map(LongValue::new).collect(Collectors.toList()))
        );
    }

    private List<String> resolveManagedDeptTreeIds() {
        Map<String, Object> ext = currentUser.getExt();
        if (null == ext) {
            return Collections.emptyList();
        }
        Object managedDeptTreeIds = ext.get(MANAGED_DEPT_TREE_IDS);
        if (managedDeptTreeIds instanceof Collection<?> collection) {
            return collection.stream()
                    .map(this::normalizeManagedDeptTreeId)
                    .filter(StringUtils::isNotEmpty)
                    .toList();
        }
        if (null != managedDeptTreeIds && managedDeptTreeIds.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(managedDeptTreeIds);
            List<String> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                String normalized = normalizeManagedDeptTreeId(java.lang.reflect.Array.get(managedDeptTreeIds, i));
                if (StringUtils.isNotEmpty(normalized)) {
                    values.add(normalized);
                }
            }
            return values;
        }
        if (managedDeptTreeIds instanceof String value) {
            String cleaned = value.trim();
            if (cleaned.startsWith("[") && cleaned.endsWith("]") && cleaned.length() >= 2) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            return Arrays.stream(cleaned.split(","))
                    .map(this::normalizeManagedDeptTreeId)
                    .filter(StringUtils::isNotEmpty)
                    .toList();
        }
        return Collections.emptyList();
    }

    private String normalizeManagedDeptTreeId(Object value) {
        if (null == value) {
            return null;
        }
        String normalized = value.toString().trim();
        if (StringUtils.isEmpty(normalized)) {
            return null;
        }
        while (normalized.startsWith("[") || normalized.startsWith("\"") || normalized.startsWith("'")) {
            normalized = normalized.substring(1).trim();
        }
        while (normalized.endsWith("]") || normalized.endsWith("\"") || normalized.endsWith("'")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return StringUtils.isEmpty(normalized) ? null : normalized;
    }
}

