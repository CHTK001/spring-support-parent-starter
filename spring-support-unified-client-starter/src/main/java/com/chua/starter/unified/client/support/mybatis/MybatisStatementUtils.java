package com.chua.starter.unified.client.support.mybatis;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * mybatis声明实用程序
 *
 * @author CH
 */
@Slf4j
public class MybatisStatementUtils {

    protected static Field STATIC_SQL_SOURCE_FIELD;
    static {
        STATIC_SQL_SOURCE_FIELD = ReflectionUtils.findField(MappedStatement.class, "sqlSource");
        if(null != STATIC_SQL_SOURCE_FIELD) {
            ReflectionUtils.makeAccessible(STATIC_SQL_SOURCE_FIELD);
        }
    }

    /**
     * 刷新
     *
     * @param configuration
     * @param mappedStatement 映射语句
     * @param sql             统一mybatis sql
     * @param sqlType         sql类型1
     * @param mapperType      映射器类型
     * @param modelType       型号
     */
    public static void refresh(Configuration configuration, MappedStatement mappedStatement,
                               String sql,
                               SqlType sqlType,
                               Class<?> mapperType,
                               Class<?> modelType) {
       if(null == STATIC_SQL_SOURCE_FIELD || null == configuration) {
           return;
       }

        LanguageDriver languageDriver = configuration.getDefaultScriptingLanguageInstance();
        log.info("动态刷新Mapper语句[{}]", mapperType.getTypeName() + "." + mappedStatement.getId());
        SqlSource sqlSource = null;
        if (sqlType == SqlType.SQL) {
            sqlSource = languageDriver.createSqlSource(configuration, sql, modelType);
        } else {
            XPathParser parser = new XPathParser("<script>" + sql + "</script>", false, configuration.getVariables(), new XMLMapperEntityResolver());
            sqlSource = languageDriver.createSqlSource(configuration, parser.evalNodes("*").get(0), modelType);
        }
        ReflectionUtils.setField(STATIC_SQL_SOURCE_FIELD, mappedStatement, sqlSource);
    }
}
