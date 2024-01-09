package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.util.List;

/**
 * 数据拦截器
 * @author CH
 */
@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class MybatisPlusPermissionInterceptor extends JsqlParserSupport implements InnerInterceptor {

    private MybatisPlusPermissionHandler mybatisPlusPermissionHandler = new MybatisPlusPermissionHandler();

    /**
     * 主要处理查询
     */
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        // 通过MP插件拿到即将执行的SQL
        PluginUtils.MPBoundSql mp = PluginUtils.mpBoundSql(boundSql);
        // parserSingle方法是JsqlParserSupport父类实现的方法，这里会根据执行的SQL是查询、新增、修改、删除来调用不同的方法，例如：如果是查询，就会调用当前类的processSelect方法
        mp.sql(parserSingle(mp.sql(), ms.getId()));
    }

    /**
     * 操作前置处理，可以在这里改改sql啥的
     */
    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        InnerInterceptor.super.beforePrepare(sh, connection, transactionTimeout);
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        super.processDelete(delete, index, sql, obj);
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        super.processUpdate(update, index, sql, obj);
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        SelectBody selectBody = select.getSelectBody();
        try {
            // 单个sql
            if (selectBody instanceof PlainSelect) {
                this.setWhere((PlainSelect) selectBody, obj.toString());
            } else if (selectBody instanceof SetOperationList) {
                // 多个sql，用;号隔开，一般不会用到。例如：select * from user;select * from role;
                SetOperationList setOperationList = (SetOperationList) selectBody;
                List<SelectBody> selects = setOperationList.getSelects();
                selects.forEach(s -> this.setWhere((PlainSelect) s, obj.toString()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void setWhere(PlainSelect plainSelect, String mapperId) {
        Expression sqlSegment = mybatisPlusPermissionHandler.getSqlSegment(plainSelect.getWhere(), mapperId);
        if (null != sqlSegment) {
            plainSelect.setWhere(sqlSegment);
        }
    }
}
