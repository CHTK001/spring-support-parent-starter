package com.chua.starter.unified.client.support.mybatis;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XPathParser;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;


/**
 * 动态sql方法
 *
 * @author CH
 * @since 2023/11/20
 */
@Slf4j
public class DynamicSqlMethod extends AbstractMethod {
    protected Field sqlSource;

    protected String sql;
    protected SqlType sqlType;
    private Class<?> modelType;
    private Class<?> mapperType;
    private JSONObject jsonObject;
    private MappedStatement mappedStatement;

    /**
     * @param methodName 方法名
     * @param jsonObject
     * @since 3.5.0
     */
    protected DynamicSqlMethod(String methodName, String sql, SqlType sqlType, Class<?> modelType, Class<?> mapperType, JSONObject jsonObject) {
        super(methodName);
        this.sql = sql;
        this.sqlType = sqlType;
        this.modelType = modelType;
        this.mapperType = mapperType;
        this.jsonObject = jsonObject;
        this.sqlSource = ReflectionUtils.findField(MappedStatement.class, "sqlSource");
        if(null != sqlSource) {
            ReflectionUtils.makeAccessible(sqlSource);
        }
    }

    /**
     * @param methodName 方法名
     * @since 3.5.0
     */
    protected DynamicSqlMethod(String methodName) {
        super(methodName);
    }


    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        SqlSource sqlSource = null;
        if (sqlType == SqlType.SQL) {
            sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        } else {
            XPathParser parser = new XPathParser("<script>" + sql + "</script>", false, configuration.getVariables(), new XMLMapperEntityResolver());
            sqlSource = languageDriver.createSqlSource(configuration, parser.evalNodes("*").get(0), modelClass);
        }

        return (mappedStatement = this.addSelectMappedStatementForTable(mapperClass, methodName, sqlSource, tableInfo));
    }

    /**
     * 刷新
     *
     * @param sql  sql
     * @param sqlType 类型
     */
    public void refresh(String sql, SqlType sqlType) {
        log.info("动态刷新Mapper语句[{}]", mapperType.getTypeName() + "." + methodName);
        SqlSource sqlSource = null;
        if (sqlType == SqlType.SQL) {
            sqlSource = languageDriver.createSqlSource(configuration, sql, modelType);
        } else {
            XPathParser parser = new XPathParser("<script>" + sql + "</script>", false, configuration.getVariables(), new XMLMapperEntityResolver());
            sqlSource = languageDriver.createSqlSource(configuration, parser.evalNodes("*").get(0), modelType);
        }
        ReflectionUtils.setField(this.sqlSource, mappedStatement, sqlSource);
    }

    /**
     * 获取配置
     *
     * @return {@link JSONObject}
     */
    public JSONObject getConfig() {
        return jsonObject;
    }
}
