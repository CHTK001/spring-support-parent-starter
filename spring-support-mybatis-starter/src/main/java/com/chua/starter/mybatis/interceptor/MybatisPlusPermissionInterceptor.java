package com.chua.starter.mybatis.interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
* 数据权限拦截器
* 用于在执行查询时自动添加数据权限过滤条件
*
* @author CH
* @since 4.0.0
 */
@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class MybatisPlusPermissionInterceptor extends JsqlParserSupport implements InnerInterceptor {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(MybatisPlusPermissionInterceptor.class);

    /** Mybatispluspermission处理器 */
    private DataPermissionHandler mybatisPlusPermissionHandler;
    /** 认证服务供应商 */
    private Supplier<AuthService> authServiceSupplier;
    static final Map<String, Expression> MAPPED_STATEMENT_ID = new ConcurrentReferenceHashMap<>(4096); // mapped对账单标识

    /**
    * 创建 mybatisplus权限拦截器 实例
    * @param mybatisPlusPermissionHandler mybatisplus权限处理器
    * @param methodSecurityInterceptor mybatisplus数据scope属性
    * @param methodSecurityInterceptor 方法安全性拦截器
    */
    public MybatisPlusPermissionInterceptor(DataPermissionHandler mybatisPlusPermissionHandler, MybatisPlusDataScopeProperties methodSecurityInterceptor) {
        this(mybatisPlusPermissionHandler, methodSecurityInterceptor, () -> null);
    }

    /**
    * 创建 mybatisplus权限拦截器 实例
    * @param mybatisPlusPermissionHandler mybatisplus权限处理器
    * @param methodSecurityInterceptor 方法安全性拦截器
    * @param authServiceSupplier 认证服务供应商
    */
    public MybatisPlusPermissionInterceptor(DataPermissionHandler mybatisPlusPermissionHandler,
                                            MybatisPlusDataScopeProperties methodSecurityInterceptor,
                                            Supplier<AuthService> authServiceSupplier) {
        this.mybatisPlusPermissionHandler = mybatisPlusPermissionHandler;
        this.authServiceSupplier = authServiceSupplier;
    }

    /**
    * 查询前置处理，解析SQL并添加数据权限条件
    *
    * @param executor      执行器
    * @param ms           mapped对账单对象
    * @param rowBounds    行边界
    * @param resultHandler 结果处理器
    * @param boundSql     boundsql对象
    */
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        // 通过MP插件拿到即将执行的SQL
        PluginUtils.MPBoundSql mp = PluginUtils.mpBoundSql(boundSql);
        try {
 // parser单个方法是jsqlparser支持父类实现的方法，这里会根据执行的SQL是查询、新增、修改、删除来调用不同的方法，例如：如果是查询，就会调用当前类的处理选择方法
            mp.sql(parserSingle(mp.sql(), ms.getId()));
        } catch (Exception e) {
            // SQL 解析失败（如字段别名撞数据库保留字）时，跳过数据权限过滤，避免阻断查询
            log.warn("[DataPermission] SQL 解析失败，跳过数据权限过滤: {}", e.getMessage());
        }
    }

    /**
    * 操作前置处理
    *
    * @param sh                  对账单处理器对象
    * @param connection          数据库连接
    * @param transactionTimeout 事务超时时间
    */
    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        InnerInterceptor.super.beforePrepare(sh, connection, transactionTimeout);
    }

    /**
    * 处理删除语句
    *
    * @param delete 删除语句对象
    * @param index  索引
    * @param sql    SQL语句
    * @param obj    对象
    */
    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        super.processDelete(delete, index, sql, obj);
    }

    /**
    * 处理更新语句
    *
    * @param update 更新语句对象
    * @param index  索引
    * @param sql    SQL语句
    * @param obj    对象
    */
    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        super.processUpdate(update, index, sql, obj);
    }

    /**
    * 处理查询语句，添加数据权限过滤条件
    *
    * @param select 选择语句对象
    * @param index  索引
    * @param sql    SQL语句
    * @param obj    对象
    */
    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        CurrentUser currentUser = getCurrentUser();
        if (currentUser == null) {
            log.debug("未找到AuthService，跳过数据权限处理");
            return;
        }

        if (ObjectUtils.isEmpty(currentUser)) {
            return;
        }

        if (!currentUser.isDept()) {
            return;
        }

        Select selectBody = select.getSelectBody();
        try {
 // 单个SQL
            if (selectBody instanceof PlainSelect plainSelect) {
                this.setWhere(plainSelect, obj.toString(), currentUser);
            } else if (selectBody instanceof SetOperationList setOperationList) {
 // 多个SQL，用;号隔开，一般不会用到。例如：选择 * 从 login;选择 * 从 角色;
                List<Select> selects = setOperationList.getSelects();
                selects.forEach(s -> {
                    if (s.getSelectBody() instanceof PlainSelect ps) {
                        this.setWhere(ps, obj.toString(), currentUser);
                    }
                });
            }
        } catch (Exception e) {
            log.error("处理数据权限异常, mappedStatementId: {}", obj, e);
        }
    }

    /**
    * 获取当前用户
    *
    * @return 获取当前用户的结果
    */
    private CurrentUser getCurrentUser() {
        if (authServiceSupplier == null) {
            return null;
        }
        AuthService authService = authServiceSupplier.get();
        return authService == null ? null : authService.getCurrentUser();
    }

    /**
    * 设置WHERE条件，添加数据权限过滤
    *
    * @param plainSelect plain选择对象
    * @param mapperId    映射器方法标识
    * @param currentUser 当前用户
    */
    protected void setWhere(PlainSelect plainSelect, String mapperId, CurrentUser currentUser) {
        if (mybatisPlusPermissionHandler == null) {
            return;
        }

        if (mybatisPlusPermissionHandler instanceof SelectDataPermissionHandler selectDataPermissionHandler) {
            selectDataPermissionHandler.processSelect(plainSelect, plainSelect.getWhere(), mapperId, currentUser);
        } else {
            Expression sqlSegment = mybatisPlusPermissionHandler.getSqlSegment(plainSelect.getWhere(), mapperId);
            if (null != sqlSegment) {
                plainSelect.setWhere(sqlSegment);
            }
        }
    }
}
