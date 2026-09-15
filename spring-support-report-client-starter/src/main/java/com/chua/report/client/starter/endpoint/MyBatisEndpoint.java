package com.chua.report.client.starter.endpoint;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* MyBatis Actuator 端点
* <p>
* 通过 actuator 暴露 MyBatis 配置信息，包括 映射器 数量、SQL 语句统计、
* 已加载资源列表等，便于运行时诊断和运维排查。
* </p>
*
* @since 2025/01/17
* @author CH
 */
@Slf4j
@Component
@Endpoint(id = "mybatis")
@ConditionalOnClass(SqlSessionFactory.class)
public class MyBatisEndpoint {


    /**
    * SQL会话工厂，可为 空（MyBatis 未启用时）
     */
    @Autowired(required = false)
    private SqlSessionFactory sqlSessionFactory; // SQL会话工厂

    /**
    * 获取 MyBatis 概览信息，包括 映射器 数量、SQL 语句统计、已加载资源等。
    *
    * @return MyBatis 概览信息 映射，包含 已启用、映射器数量、mapped对账单数量 等字段
    * @throws Exception 获取信息失败时记录错误日志并返回 错误 字段
     */
    @ReadOperation
    public Map<String, Object> overview() {
        Map<String, Object> result = new HashMap<>();

        if (sqlSessionFactory == null) {
            result.put("enabled", false);
            result.put("message", "SqlSessionFactory 未配置");
            return result;
        }

        try {
            Configuration configuration = sqlSessionFactory.getConfiguration();
            
            result.put("enabled", true);
            result.put("mapperCount", configuration.getMapperRegistry().getMappers().size());
            result.put("mappedStatementCount", configuration.getMappedStatementNames().size());
            result.put("resultMapCount", configuration.getResultMapNames().size());
            result.put("sqlFragmentCount", configuration.getSqlFragments().size());
            result.put("cacheEnabled", configuration.isCacheEnabled());
            result.put("lazyLoadingEnabled", configuration.isLazyLoadingEnabled());
            result.put("loadedResources", getLoadedResources(configuration));

            // 统计 SQL 类型
            Map<String, Integer> sqlTypeCount = new HashMap<>();
            for (Object obj : configuration.getMappedStatements()) {
                if (obj instanceof MappedStatement ms) {
                    String type = ms.getSqlCommandType().name();
                    sqlTypeCount.merge(type, 1, Integer::sum);
                }
            }
            result.put("sqlTypeCount", sqlTypeCount);

 // 映射器 列表
            result.put("mappers", getMapperList(configuration));

        } catch (Exception e) {
            log.error("[MyBatisEndpoint] 获取概览失败: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
    * 获取指定 映射器 的详情，包括方法列表和 SQL 类型统计。
    *
    * @param mapperName 映射器 全限定类名（URL 编码）
    * @return Mapper 详情 映射，包含 对账单、方法数量 等字段
    * @throws Exception 获取详情失败时记录错误日志并返回 错误 字段
     */
    @ReadOperation
    public Map<String, Object> mapper(@Selector String mapperName) {
        Map<String, Object> result = new HashMap<>();

        if (sqlSessionFactory == null) {
            result.put("error", "SqlSessionFactory 未配置");
            return result;
        }

        try {
            // URL 解码
            String decodedMapperName = java.net.URLDecoder.decode(mapperName, "UTF-8");
            Configuration configuration = sqlSessionFactory.getConfiguration();

            result.put("mapperName", decodedMapperName);
            result.put("simpleName", decodedMapperName.substring(decodedMapperName.lastIndexOf('.') + 1));

            List<Map<String, Object>> statements = new ArrayList<>();
            for (Object obj : configuration.getMappedStatements()) {
                if (obj instanceof MappedStatement ms) {
                    String id = ms.getId();
                    if (id.startsWith(decodedMapperName + ".")) {
                        Map<String, Object> statementInfo = new HashMap<>();
                        statementInfo.put("id", id);
                        statementInfo.put("methodName", id.substring(decodedMapperName.length() + 1));
                        statementInfo.put("sqlCommandType", ms.getSqlCommandType().name());
                        statementInfo.put("resultType", ms.getResultMaps().isEmpty() ? null
                                : ms.getResultMaps().getFirst().getType().getName());
                        statementInfo.put("resource", ms.getResource());
                        statements.add(statementInfo);
                    }
                }
            }

            result.put("statements", statements);
            result.put("methodCount", statements.size());

        } catch (Exception e) {
            log.error("[MyBatisEndpoint] 获取 Mapper 详情失败: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
    * 获取 映射器 列表
    * @param configuration 配置
    * @return 获取映射器列表的结果
     */
    private List<Map<String, Object>> getMapperList(Configuration configuration) {
        Collection<String> mappedStatementNames = configuration.getMappedStatementNames();

 // 提取唯一的 映射器 接口名称
        Set<String> mapperSet = new HashSet<>();
        for (String statementId : mappedStatementNames) {
            int lastDot = statementId.lastIndexOf('.');
            if (lastDot > 0) {
                String mapperName = statementId.substring(0, lastDot);
                mapperSet.add(mapperName);
            }
        }

 // 构建 映射器 列表
        List<Map<String, Object>> mappers = new ArrayList<>();
        for (String name : mapperSet) {
            Map<String, Object> mapper = new HashMap<>();
            mapper.put("name", name);
            mapper.put("simpleName", name.substring(name.lastIndexOf('.') + 1));

 // 计算该 映射器 下的方法数量
            long methodCount = mappedStatementNames.stream()
                    .filter(s -> s.startsWith(name + "."))
                    .count();
            mapper.put("methodCount", methodCount);

            // 获取资源文件
            for (Object obj : configuration.getMappedStatements()) {
                if (obj instanceof MappedStatement ms) {
                    if (ms.getId().startsWith(name + ".")) {
                        mapper.put("resource", ms.getResource());
                        break;
                    }
                }
            }

            mappers.add(mapper);
        }

        // 按名称排序
        mappers.sort((a, b) -> String.valueOf(a.get("name")).compareTo(String.valueOf(b.get("name"))));
        return mappers;
    }

    /**
    * 获取已加载的资源列表
    * @param configuration 配置
    * @return 获取加载resources的结果
     */
    @SuppressWarnings("unchecked")
    private Set<String> getLoadedResources(Configuration configuration) {
        try {
            Field loadedResourcesField = Configuration.class.getDeclaredField("loadedResources");
            loadedResourcesField.setAccessible(true);
            return (Set<String>) loadedResourcesField.get(configuration);
        } catch (Exception e) {
            log.warn("[MyBatisEndpoint] 获取已加载资源失败: {}", e.getMessage());
            return Collections.emptySet();
        }
    }
}
