//package com.chua.starter.mybatis.interceptor;
//
//import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
//import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
//import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
//import lombok.*;
//import net.sf.jsqlparser.statement.select.PlainSelect;
//import net.sf.jsqlparser.statement.select.SelectBody;
//import net.sf.jsqlparser.statement.select.SetOperationList;
//import org.apache.ibatis.mapping.BoundSql;
//import org.apache.ibatis.mapping.MappedStatement;
//import org.apache.ibatis.session.RowBounds;
//
//import java.sql.SQLException;
//import java.util.concurrent.Executor;
//
///**
// * 数据拦截器
// * @author CH
// */
//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//@ToString(callSuper = true)
//@EqualsAndHashCode(callSuper = true)
//public class CustomDataPermissionInterceptor extends DataPermissionInterceptor {
//
////    private GitEggDataPermissionHandler dataPermissionHandler;
////    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
////        if (!InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId())) {
////            PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
////            mpBs.sql(this.parserSingle(mpBs.sql(), ms.getId()));
////        }
////    }
////    protected void processSelect(Select select, int index, String sql, Object obj) {
////        SelectBody selectBody = select.getSelectBody();
////        if (selectBody instanceof PlainSelect) {
////            PlainSelect plainSelect = (PlainSelect)selectBody;
////            this.processDataPermission(plainSelect, (String)obj);
////        } else if (selectBody instanceof SetOperationList) {
////            SetOperationList setOperationList = (SetOperationList)selectBody;
////            List<selectbody> selectBodyList = setOperationList.getSelects();
////            selectBodyList.forEach((s) -> {
////                PlainSelect plainSelect = (PlainSelect)s;
////                this.processDataPermission(plainSelect, (String)obj);
////            });
////        }
////    }
////    protected void processDataPermission(PlainSelect plainSelect, String whereSegment) {
////        this.dataPermissionHandler.processDataPermission(plainSelect, whereSegment);
////    }
//}
