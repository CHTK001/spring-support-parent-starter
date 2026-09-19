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
 * @since 4.0.0
 */
public class DynamicSqlMethod extends AbstractMethod {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(DynamicSqlMethod.class);
    /** SQL来源 */
    private final Field sqlSource;
    private Map<String, Object> param; // 参数
    /** Mappedstatement */
    private MappedStatement mappedStatement;

    private Class<?> type = MappedStatement.class; // 类型
    private Class<?> modelClass; // 模型类
    private Class<?> mapperClass; // 映射器类

    /**
    * @param methodName 方法名
    * @since 3.5.0
    * @param param 参数
    */
    public DynamicSqlMethod(String methodName, Map<String, Object> param) {
        /**
         * 父。
         * @param methodName 方法名称
         */
        super(methodName);
        this.param = param;
        this.sqlSource = ReflectionUtils.findField(type, "sqlSource");
        ReflectionUtils.makeAccessible(sqlSource);
    }

    @Override
    /** injectmapped对账单 */
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
 // x节点 x节点 = 新 x节点(parser, 新 , 新 属性());
            sqlSource = languageDriver.createSqlSource(configuration, parser.evalNodes("*").getFirst(), modelClass);
        }

        return (mappedStatement = this.addSelectMappedStatementForTable(mapperClass, methodName, sqlSource, tableInfo));
    }

    /**
     * 获取名称
     *
     * @return 获取名称的结果
     */
    public String getName() {
        return methodName;
    }

    /**
     * Refresh
     *
     * @param sql SQL
     * @param type 类型
     */
    public void refresh(String sql, String type) {
        log.info("动态刷新Mapper语句[{}]", mapperClass.getTypeName() + "." + methodName);
        SqlSource sqlSource = null;
        if ("SQL".equalsIgnoreCase(type)) {
            sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        } else {
            XPathParser parser = new XPathParser("<script>" + sql + "</script>", false, configuration.getVariables(), new XMLMapperEntityResolver());
 // x节点 x节点 = 新 x节点(parser, 新 , 新 属性());
            sqlSource = languageDriver.createSqlSource(configuration, parser.evalNodes("*").getFirst(), modelClass);
        }
        ReflectionUtils.setField(this.sqlSource, mappedStatement, sqlSource);
    }
}
