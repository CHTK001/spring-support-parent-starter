package com.chua.report.client.starter.sync.handler;

import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.sync.support.spi.SyncMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MyBatis 配置处理器
 * <p>
 * 监听 MyBatis 配置 Topic，提供 XML 刷新功能
 * 映射器 信息查询通过 actuator 端点 /actuator/MyBatis 获取
 * </p>
 *
 * @版本 1.0.0
 * @since 2025/01/17
 * @author CH
 */
@Slf4j
@Spi("myBatisConfigHandler")
public class MyBatisConfigHandler implements SyncMessageHandler, ApplicationContextAware {


    /** Application上下文 */
    private ApplicationContext applicationContext;

    @Override
    /** 设置application上下文 */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    /** 获取名称 */
    public String getName() {
        return "myBatisConfigHandler";
    }

    @Override
    /** 支持 */
    public boolean supports(String topic) {
        return MonitorTopics.MYBATIS_CONFIG.equals(topic);
    }

    @Override
    /** 处理 */
    public Object handle(String topic, String sessionId, Map<String, Object> data) {
        String action = MapUtils.getString(data, "action");
        log.debug("[MyBatisConfig] 收到请求: action={}", action);

        return switch (action) {
            case "refreshXml" -> handleRefreshXml(data);
            case "refreshAllXml" -> handleRefreshAllXml(data);
            default -> Map.of("code", 400, "message", "未知操作: " + action);
        };
    }

    /**
     * 刷新指定的 XML 文件
     *
     * @param data 请求数据，包含 resource路径
     * @return 刷新结果
     */
    private Object handleRefreshXml(Map<String, Object> data) {
        String resourcePath = MapUtils.getString(data, "resourcePath");

        if (resourcePath == null || resourcePath.isEmpty()) {
            return Map.of("code", 400, "message", "resourcePath 不能为空");
        }

        try {
            SqlSessionFactory sqlSessionFactory = getSqlSessionFactory();
            if (sqlSessionFactory == null) {
                return Map.of("code", 500, "message", "未找到 SqlSessionFactory");
            }

            Configuration configuration = sqlSessionFactory.getConfiguration();
            
            // 刷新指定 XML
            boolean success = refreshMapperXml(configuration, resourcePath);

            if (success) {
                log.info("[MyBatisConfig] XML 刷新成功: resourcePath={}", resourcePath);
                return Map.of(
                        "code", 200,
                        "message", "SUCCESS",
                        "resourcePath", resourcePath
                );
            } else {
                return Map.of("code", 500, "message", "刷新失败，未找到指定资源");
            }

        } catch (Exception e) {
            log.error("[MyBatisConfig] XML 刷新失败: resourcePath={}, error={}", 
                    resourcePath, e.getMessage(), e);
            return Map.of("code", 500, "message", "XML 刷新失败: " + e.getMessage());
        }
    }

    /**
     * 刷新所有 XML 文件
     *
     * @param data 请求数据
     * @return 刷新结果
     */
    private Object handleRefreshAllXml(Map<String, Object> data) {
        try {
            SqlSessionFactory sqlSessionFactory = getSqlSessionFactory();
            if (sqlSessionFactory == null) {
                return Map.of("code", 500, "message", "未找到 SqlSessionFactory");
            }

            Configuration configuration = sqlSessionFactory.getConfiguration();
            
            // 获取所有已加载的资源
            Set<String> loadedResources = getLoadedResources(configuration);
            int refreshedCount = 0;

            for (String resource : loadedResources) {
                if (resource.endsWith(".xml")) {
                    try {
                        if (refreshMapperXml(configuration, resource)) {
                            refreshedCount++;
                        }
                    } catch (Exception e) {
                        log.warn("[MyBatisConfig] 刷新 XML 失败: resource={}, error={}", 
                                resource, e.getMessage());
                    }
                }
            }

            log.info("[MyBatisConfig] 批量刷新 XML 完成: 刷新数量={}", refreshedCount);
            return Map.of(
                    "code", 200,
                    "message", "SUCCESS",
                    "refreshedCount", refreshedCount,
                    "totalResources", loadedResources.size()
            );

        } catch (Exception e) {
            log.error("[MyBatisConfig] 批量刷新 XML 失败: {}", e.getMessage(), e);
            return Map.of("code", 500, "message", "批量刷新失败: " + e.getMessage());
        }
    }

    /**
     * 获取 SQL会话工厂
     *
     * @return SqlSessionFactory 实例，未找到返回 空
     */
    private SqlSessionFactory getSqlSessionFactory() {
        if (applicationContext == null) {
            return null;
        }
        try {
            return applicationContext.getBean(SqlSessionFactory.class);
        } catch (Exception e) {
            log.warn("[MyBatisConfig] 未找到 SqlSessionFactory: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取已加载的资源列表
     *
     * @param configuration MyBatis 配置
     * @return 资源路径集合
     */
    @SuppressWarnings("unchecked")
    private Set<String> getLoadedResources(Configuration configuration) {
        Set<String> loadedResources = (Set<String>) ReflectUtils.getField(configuration, "loadedResources");
        if (loadedResources == null) {
            log.warn("[MyBatisConfig] 获取已加载资源失败");
            return Collections.emptySet();
        }
        return loadedResources;
    }

    /**
     * 刷新 映射器 XML 文件
     *
     * @param configuration  MyBatis 配置
     * @param resourcePath   资源路径
     * @return 是否刷新成功
     */
    private boolean refreshMapperXml(Configuration configuration, String resourcePath) {
        try {
            // 查找资源文件
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources;
            
            if (resourcePath.startsWith("classpath:") || resourcePath.startsWith("file:")) {
                resources = resolver.getResources(resourcePath);
            } else {
                resources = resolver.getResources("classpath*:**/" + resourcePath);
            }

            if (resources.length == 0) {
                log.warn("[MyBatisConfig] 未找到资源文件: {}", resourcePath);
                return false;
            }

 // 清除旧的 mapped对账单
            /**
             * clearmapped对账单。
             * @param configuration 配置
             * @param resourcePath resource路径
             */
            clearMappedStatements(configuration, resourcePath);

            // 重新加载 XML
            for (Resource resource : resources) {
                /**
                 * 尝试。
                 * @param resource.getInputStream( resource.获取输入流(
                 */
                try (InputStream inputStream = resource.getInputStream()) {
                    XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(
                            inputStream,
                            configuration,
                            resource.toString(),
                            configuration.getSqlFragments()
                    );
                    xmlMapperBuilder.parse();
                    log.debug("[MyBatisConfig] 重新加载 XML: {}", resource.getFilename());
                }
            }

            return true;
        } catch (Exception e) {
            log.error("[MyBatisConfig] 刷新 XML 失败: resourcePath={}, error={}", 
                    resourcePath, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 清除指定资源的 mapped对账单
     *
     * @param configuration MyBatis 配置
     * @param resourcePath  资源路径
     */
    @SuppressWarnings("unchecked")
    private void clearMappedStatements(Configuration configuration, String resourcePath) {
        try {
 // 获取 mapped对账单 字段
            Map<String, MappedStatement> mappedStatements =
                    (Map<String, MappedStatement>) ReflectUtils.getField(configuration, "mappedStatements");
            if (mappedStatements == null) {
                log.warn("[MyBatisConfig] 清除 MappedStatement 失败: mappedStatements 字段读取失败");
                return;
            }

 // 移除与该资源相关的 mapped对账单
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, MappedStatement> entry : mappedStatements.entrySet()) {
                if (entry.getValue() instanceof MappedStatement ms) {
                    String resource = ms.getResource();
                    if (resource != null && resource.contains(resourcePath)) {
                        toRemove.add(entry.getKey());
                    }
                }
            }

            for (String key : toRemove) {
                mappedStatements.remove(key);
            }

            // 从已加载资源中移除
            Set<String> loadedResources = getLoadedResources(configuration);
            loadedResources.removeIf(r -> r.contains(resourcePath));

            log.debug("[MyBatisConfig] 清除 MappedStatement 数量: {}", toRemove.size());
        } catch (Exception e) {
            log.warn("[MyBatisConfig] 清除 MappedStatement 失败: {}", e.getMessage());
        }
    }
}
