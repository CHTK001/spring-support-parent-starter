package com.chua.starter.mybatis.method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.chua.common.support.utils.MapUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XPathParser;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 动态方法
 *
 * @author CH
 */
public class DynamicSqlMethod extends AbstractMethod {
    private static final Logger log = LoggerFactory.getLogger(DynamicSqlMethod.class);
    private final Field sqlSource;
    private Map<String, Object> param;
    private MappedStatement mappedStatement;

    private Class<?> type = MappedStatement.class;
    private Class<?> modelClass;
    private Class<?> mapperClass;

    /**
     * @param methodName 方法名
     * @since 3.5.0
     */
    public DynamicSqlMethod(String methodName, Map<String, Object> param) {
        super(methodName);
        this.param = param;
        this.sqlSource = ReflectionUtils.findField(type, "sqlSource");
        ReflectionUtils.makeAccessible(sqlSource);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        String sql = MapUtils.getString(param, "content");
        String type = MapUtils.getString(param, "type");
        try {
            this.mapperClass = ClassUtils.forName(MapUtils.getString(param, "mapper_type", mapperClass.getTypeName()), ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }

        try {
            this.modelClass = ClassUtils.forName(MapUtils.getString(param, "model_type", modelClass.getTypeName()), ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }

        SqlSource sqlSource = null;
        if ("SQL".equalsIgnoreCase(type)) {
            sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        } else {
            XPathParser parser = new XPathParser("<script>" + sql + "</script>", false, configuration.getVariables(), new XMLMapperEntityResolver());
//            XNode xNode = new XNode(parser, new , new Properties());
            sqlSource = languageDriver.createSqlSource(configuration, parser.evalNodes("*").get(0), modelClass);
        }

        return (mappedStatement = this.addSelectMappedStatementForTable(mapperClass, methodName, sqlSource, tableInfo));
    }

    public String getName() {
        return methodName;
    }

    public void refresh(String sql, String type) {
        log.info("动态刷新Mapper语句[{}]", mapperClass.getTypeName() + "." + methodName);
        SqlSource sqlSource = null;
        if ("SQL".equalsIgnoreCase(type)) {
            sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        } else {
            XPathParser parser = new XPathParser("<script>" + sql + "</script>", false, configuration.getVariables(), new XMLMapperEntityResolver());
//            XNode xNode = new XNode(parser, new , new Properties());
            sqlSource = languageDriver.createSqlSource(configuration, parser.evalNodes("*").get(0), modelClass);
        }
        ReflectionUtils.setField(this.sqlSource, mappedStatement, sqlSource);
    }
}