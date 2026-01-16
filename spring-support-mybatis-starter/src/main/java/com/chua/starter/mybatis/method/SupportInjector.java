package com.chua.starter.mybatis.method;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.chua.starter.mybatis.marker.MapperSqlMethodMarker;
import com.chua.starter.mybatis.marker.MysqlSqlMethodMarker;
import com.chua.starter.mybatis.marker.SqlMethodMarker;
import com.chua.starter.mybatis.properties.MybatisPlusProperties;
import org.springframework.beans.factory.DisposableBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * support
 *
 * @author CH
 */
public class SupportInjector extends DefaultSqlInjector implements DisposableBean {

    private MybatisPlusProperties mybatisProperties;
    private final Map<MybatisPlusProperties.SqlMethodProperties, SqlMethodMarker> cache = new ConcurrentHashMap<>();
    private final Map<MybatisPlusProperties.SqlMethodProperties, List<AbstractMethod>> cacheAbstractMethod = new ConcurrentHashMap<>();

    public SupportInjector(MybatisPlusProperties mybatisProperties) {
        this.mybatisProperties = mybatisProperties;
    }

    @Override
    public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> list = new ArrayList<>();

        List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);
        list.addAll(methodList);
        List<MybatisPlusProperties.SqlMethodProperties> sqlMethod = mybatisProperties.getSqlMethod();
        if (null == sqlMethod) {
            return list;
        }

        for (MybatisPlusProperties.SqlMethodProperties sqlMethodProperties : sqlMethod) {
            if (cache.containsKey(sqlMethodProperties)) {
                List<AbstractMethod> abstractMethods = cacheAbstractMethod.get(sqlMethodProperties);
                list.addAll(abstractMethods);
                continue;
            }

            SqlMethodMarker marker = getMarker(sqlMethodProperties);
            if (null == marker) {
                continue;
            }

            cache.put(sqlMethodProperties, marker);
            List<AbstractMethod> analysis = marker.analysis(sqlMethodProperties.getSource());
            cacheAbstractMethod.put(sqlMethodProperties, analysis);
            list.addAll(analysis);
        }
        return list;
    }

    private SqlMethodMarker getMarker(MybatisPlusProperties.SqlMethodProperties sqlMethodProperties) {
        MybatisPlusProperties.SqlMethodType type = sqlMethodProperties.getType();
        return type == MybatisPlusProperties.SqlMethodType.FILE ?
                new MapperSqlMethodMarker(sqlMethodProperties.getTimeout(), sqlMethodProperties.isWatchdog()) :
                new MysqlSqlMethodMarker(sqlMethodProperties.getTimeout(), sqlMethodProperties.isWatchdog());
    }

    @Override
    public void destroy() throws Exception {
        for (SqlMethodMarker sqlMethodMarker : cache.values()) {
            sqlMethodMarker.destroy();
        }
    }

    public String refresh(String name) {
        for (SqlMethodMarker marker : cache.values()) {
            marker.refresh(name);
        }
        return "刷新成功";
    }
}
