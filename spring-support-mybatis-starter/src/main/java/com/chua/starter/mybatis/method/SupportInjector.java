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
* 支持
*
* @author CH
* @since 4.0.0
 */
public class SupportInjector extends DefaultSqlInjector implements DisposableBean {

    /** MyBatis属性 */
    private MybatisPlusProperties mybatisProperties;
    private final Map<MybatisPlusProperties.SqlMethodProperties, SqlMethodMarker> cache = new ConcurrentHashMap<>(); // 缓存
    private final Map<MybatisPlusProperties.SqlMethodProperties, List<AbstractMethod>> cacheAbstractMethod = new ConcurrentHashMap<>(); // 缓存抽象方法

    /**
    * 创建 支持injector 实例
    * @param mybatisProperties MyBatis属性
    */
    public SupportInjector(MybatisPlusProperties mybatisProperties) {
        this.mybatisProperties = mybatisProperties;
    }

    @Override
    /** 获取方法列表 */
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

    /**
    * 获取记号笔
    *
    * @param sqlMethodProperties SQL方法属性
    * @return 获取记号笔的结果
    */
    private SqlMethodMarker getMarker(MybatisPlusProperties.SqlMethodProperties sqlMethodProperties) {
        MybatisPlusProperties.SqlMethodType type = sqlMethodProperties.getType();
        return type == MybatisPlusProperties.SqlMethodType.FILE ?
                new MapperSqlMethodMarker(sqlMethodProperties.getTimeout(), sqlMethodProperties.isWatchdog()) :
                new MysqlSqlMethodMarker(sqlMethodProperties.getTimeout(), sqlMethodProperties.isWatchdog());
    }

    @Override
    /** 销毁 */
    public void destroy() throws Exception {
        for (SqlMethodMarker sqlMethodMarker : cache.values()) {
            sqlMethodMarker.destroy();
        }
    }

    /**
    * Refresh
    *
    * @param name 名称
    * @return refresh的结果
    */
    public String refresh(String name) {
        for (SqlMethodMarker marker : cache.values()) {
            marker.refresh(name);
        }
        return "刷新成功";
    }

    /**
    * refresh全部
    *
    * @return refresh全部的结果
    */
    public String refreshAll() {
        for (SqlMethodMarker marker : cache.values()) {
            if (marker instanceof MysqlSqlMethodMarker mysqlMarker) {
                mysqlMarker.refreshAll();
            }
        }
        return "全部刷新成功";
    }
}
