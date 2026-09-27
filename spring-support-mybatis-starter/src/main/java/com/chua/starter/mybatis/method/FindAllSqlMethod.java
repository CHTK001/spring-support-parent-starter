package com.chua.starter.mybatis.method;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.chua.common.support.lang.datasource.dialect.SqlName;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 动态方法
 *
 * @author CH
 * @since 4.0.0
 */
public class FindAllSqlMethod extends AbstractMethod {
    /**
     * @param methodName 方法名
     * @since 3.5.0
     */
    protected FindAllSqlMethod(String methodName) {
        /**
         * 父。
         * @param methodName 方法名称
         */
        super(methodName);
    }

    /**
     * 注入 MyBatis-Plus 的 findAll 语句。
     *
     * <p>安全豁免：表名只来自实体 {@link TableInfo} 元数据，不接受请求参数；
     * 注入前仍经 {@link SqlName#check(String, String)} 严格校验。</p>
     */
    @Override
    /**
     * injectmapped对账单
    */
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        String tableName = SqlName.check(tableInfo.getTableName(), "MyBatis 实体表名");
        String sql = "select * from " + tableName;

        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);

        return this.addSelectMappedStatementForTable(mapperClass, methodName, sqlSource, tableInfo);
    }
}
