// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.mybatis.interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chua.common.support.lang.format.DmlFormatter;
import com.chua.common.support.lang.format.Formatter;
import com.chua.common.support.utils.DateUtils;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.MDC;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

import static com.chua.common.support.constant.NameConstant.NULL;
import static com.chua.starter.common.support.constant.MdcConstant.TRACE_ID;

/**
 * SQL拦截器
 *
 * @author chh
 * @since 2022/10/25 9:26
 *
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
        @Signature(type = StatementHandler.class, method = "getBoundSql", args = {}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
})
/**
 * SQL拦截器类。
 *
 * @author CH
 * @since 4.0.0
 */
public class SqlInterceptor implements Interceptor {
    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(SqlInterceptor.class);

    private static final Map<String, Object> MAP = new ConcurrentHashMap<>(2); // 映射

    /**
     * SQL 格式化器
     */
    static final Formatter FORMATTER = new DmlFormatter();

    /**
     * 重写intercept，拦截SQL，拼接完整SQL语句
     *
     * @param invocation 调用
     * @return Object
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object returnValue;
        long start = System.currentTimeMillis();
        returnValue = invocation.proceed();
        long end = System.currentTimeMillis();
        long time = end - start;
        try {
            final Object[] args = invocation.getArgs();
            //获取原始的ms
            if (!(args[0] instanceof MappedStatement ms)) {
                return returnValue;
            }
            Object parameter = null;
 // 获取参数，if语句成立，表示SQL语句有参数，参数格式是映射形式
            if (invocation.getArgs().length > 1) {
                parameter = invocation.getArgs()[1];
            }
            Method method = invocation.getMethod();
            String name = method.getName();
            String commandName = ms.getSqlCommandType().name();
            if (commandName.startsWith("INSERT")) {
                name = name + "=新增";
            } else if (commandName.startsWith("UPDATE")) {
                name = name + "=修改";
            } else if (commandName.startsWith("DELETE")) {
                name = name + "=删除";
            } else if (commandName.startsWith("SELECT")) {
                name = name + "=查询";
            }
 // 获取到节点的标识,即SQL语句的标识
            String sqlId = ms.getId();
 // boundsql就是封装MyBatis最终产生的SQL类
            BoundSql boundSql = ms.getBoundSql(parameter);
            // 获取节点的配置
            Configuration configuration = ms.getConfiguration();
 // 获取到最终的SQL语句
            String sql = getSql(configuration, boundSql, sqlId, time, returnValue, name);
            log.info(sql);
        } catch (Exception e) {
            log.error("拦截sql处理出错，出错原因：" + e.getMessage());
            log.error("", e);
        }
        return returnValue;
    }

    /**
     * 封装了一下SQL语句，使得结果返回完整xml路径下的SQL语句节点标识 + SQL语句
     *
     * @param configuration 配置
     * @param boundSql      boundsql
     * @param sqlId         sqlid
     * @param time          执行时间
     * @param result        结果
     * @param name          SQL操作类型
     * @return String
     */
    public static String getSql(Configuration configuration, BoundSql boundSql, String sqlId, long time, Object result, String name) {
        /**
         * showsql。
         * @param configuration 配置
         * @param boundSql boundsql
         */
        showSql(configuration, boundSql);
        String traceId = MDC.get(TRACE_ID);
        String message = "[SqlInterceptor] 执行 [" + name + "] 时间 [" + DateUtils.format(System.currentTimeMillis()) + "] sql耗时 [" + (double) time / 1000 + "] s";
        StringBuilder str = new StringBuilder();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        str.append("\n").append("----------------------------begin[SQL Execute Message]--------------------------------\n");
        if (StringUtils.isNotBlank(traceId)) {
            str.append("[TraceId]").append(traceId).append("\n");
        }
        str.append("[方法]").append(sqlId).append("\n");
        str.append("[sql]\r\n").append(FORMATTER.format(MAP.get("sql").toString()));
        str.append("\n");
 // str.追加("[参数映射]").追加(参数mappings);
 // str.追加("\n");
        str.append("[参数对象]").append(StringUtils.join((List<String>) MAP.get("parameters"), ", "));
        str.append("\n");
        str.append("[结果] ");
        if (result != null) {
            if (result instanceof List) {
                str.append("共 ").append(((List) result).size()).append(" 条记录\n");
            } else if (result instanceof Collection) {
                str.append("共 ").append(((Collection) result).size()).append(" 条记录\n");
            } else {
                str.append("共 1 条记录").append("\n");
            }
 // str.追加("[结果详情]").追加(结果).追加("\n");
        } else {
            str.append("[结果]  NULL").append("\n");
        }
        str.append("[执行信息]").append(message);
        str.append("\n");
        str.append("----------------------------end[SQL Execute Message]--------------------------------\n");
        return str.toString();
    }

    /**
     * 如果参数是字符串，则添加单引号，
     * 如果是日期，则转换为时间格式器并加单引号； 对参数是空和不是空的情况作了处理
     *
     * @return String
     * @param obj obj
     */
    private static String getParameterValue(Object obj) {
        String value;
        if (obj instanceof String) {
            value = "'" + obj + "'";
        } else if (obj instanceof Date) {
            value = "'" + DateUtils.format((Date) obj) + "'";
        } else {
            if (obj != null) {
                value = obj.toString();
            } else {
                value = "";
            }
        }
        return value;
    }

    /**
     * 进行？的替换
     *
     * @param configuration 配置
     * @param boundSql      boundsql
     * @return void
     */
    public static void showSql(Configuration configuration, BoundSql boundSql) {
        // 获取参数
        Object parameterObject = boundSql.getParameterObject();
        List<ParameterMapping> parameterMappings = boundSql
                .getParameterMappings();
 // SQL语句中多个空格都用一个空格代替
        String sql = boundSql.getSql().replaceAll("[\\s]+", " ");
        List<String> list = new ArrayList<>();
        if (!CollectionUtils.isEmpty(parameterMappings) && parameterObject != null) {
 // 获取类型处理器注册器，类型处理器的功能是进行Java类型和数据库类型的转换
 // 如果根据参数对象.获取类(）可以找到对应的类型，则替换
            TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
            if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(getParameterValue(parameterObject)));
                list.add(parameterObject + "(" + parameterObject.getClass().getSimpleName() + ")");
            } else {
 // meta对象主要是封装了原始对象对象，提供了获取和设置的方法用于获取和设置原始对象的属性值,主要支持对javaBean、集合、映射三种类型对象的操作
                MetaObject metaObject = configuration.newMetaObject(parameterObject);
                for (ParameterMapping parameterMapping : parameterMappings) {
                    String propertyName = parameterMapping.getProperty();
                    if (metaObject.hasGetter(propertyName)) {
                        Object obj = metaObject.getValue(propertyName);
                        if (Objects.nonNull(obj)) {
                            sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(getParameterValue(obj)));
                            list.add(parameterMapping.getProperty() + "=" + obj + "(" + obj.getClass().getSimpleName() + ")");
                        } else {
                            sql = sql.replaceFirst("\\?", NULL);
                            list.add(parameterMapping.getProperty() + "=null");
                        }
                    } else if (boundSql.hasAdditionalParameter(propertyName)) {
 // 该分支是动态SQL
                        Object obj = boundSql.getAdditionalParameter(propertyName);
                        if (Objects.nonNull(obj)) {
                            sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(getParameterValue(obj)));
                            list.add(parameterMapping.getProperty() + "=" + obj + "(" + obj.getClass().getSimpleName() + ")");
                        } else {
                            sql = sql.replaceFirst("\\?", NULL);
                            list.add(parameterMapping.getProperty() + "=null");
                        }
                    } else {
                        sql = sql.replaceFirst("\\?", "缺失");
                        list.add("缺失");
                    }//打印出缺失，提醒该参数缺失并防止错位
                }
            }
        }
        MAP.put("sql", sql);
        MAP.put("parameters", list);
    }

    @Override
    public Object plugin(Object arg0) {
        return Plugin.wrap(arg0, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}