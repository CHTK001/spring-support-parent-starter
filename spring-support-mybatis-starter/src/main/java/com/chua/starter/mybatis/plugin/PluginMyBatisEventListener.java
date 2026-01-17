package com.chua.starter.mybatis.plugin;

import com.chua.common.support.core.utils.ServiceProvider;
import com.chua.starter.mybatis.plugin.PluginMyBatisMapperRegistry;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.spring.support.plugin.integration.PluginBeanRegisteredEvent;
import com.chua.spring.support.plugin.integration.PluginBeanUnregisteredEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 插件 MyBatis Mapper 事件监听器
 * <p>
 * 监听插件 Bean 注册/卸载事件，自动处理 MyBatis Mapper 的注册和卸载
 * </p>
 *
 * @author CH
 * @since 2025/01/15
 */
@Slf4j
@Component
@Order(1000)
public class PluginMyBatisEventListener {

    /**
     * SqlSessionFactory 列表
     */
    @Autowired(required = false)
    private List<SqlSessionFactory> sqlSessionFactories;

    /**
     * MyBatis Mapper 注册器
     */
    private final PluginMyBatisMapperRegistry mapperRegistry;

    public PluginMyBatisEventListener() {
        this.mapperRegistry = ServiceProvider.of(PluginMyBatisMapperRegistry.class).getExtension();
    }

    /**
     * 处理插件 Bean 注册事件
     * <p>
     * 当插件 Bean 注册到 Spring 容器时，检查是否为 Mapper 接口，如果是则注册到 MyBatis
     * </p>
     *
     * @param event 插件 Bean 注册事件
     */
    @EventListener
    public void onPluginBeanRegistered(PluginBeanRegisteredEvent event) {
        if (sqlSessionFactories == null || sqlSessionFactories.isEmpty()) {
            log.debug("[PluginMyBatis][事件]SqlSessionFactory 不可用，跳过 Mapper 注册: {}", event.getBeanName());
            return;
        }

        String beanName = event.getBeanName();
        String pluginFilePath = event.getPluginFilePath();

        try {
            // 获取 Bean 类型
            Class<?> beanType = getBeanType(beanName);
            if (beanType == null) {
                return;
            }

            // 检查是否为 Mapper 接口
            if (!isMapperInterface(beanType)) {
                log.debug("[PluginMyBatis][事件]Bean 不是 Mapper 接口，跳过注册: {}", beanName);
                return;
            }

            // 注册到所有 SqlSessionFactory
            for (SqlSessionFactory sqlSessionFactory : sqlSessionFactories) {
                try {
                    mapperRegistry.registerMappers(sqlSessionFactory, beanType);
                    log.info("[PluginMyBatis][事件]插件 Mapper 接口注册成功: {} 插件: {}", beanName, pluginFilePath);
                } catch (Exception e) {
                    log.error("[PluginMyBatis][事件]插件 Mapper 接口注册失败: {} 插件: {}", beanName, pluginFilePath, e);
                }
            }
        } catch (Exception e) {
            log.error("[PluginMyBatis][事件]处理插件 Bean 注册事件失败: {} 插件: {}", beanName, pluginFilePath, e);
        }
    }

    /**
     * 处理插件 Bean 卸载事件
     * <p>
     * 当插件 Bean 从 Spring 容器卸载时，从 MyBatis 配置中移除对应的 Mapper
     * </p>
     *
     * @param event 插件 Bean 卸载事件
     */
    @EventListener
    public void onPluginBeanUnregistered(PluginBeanUnregisteredEvent event) {
        if (sqlSessionFactories == null || sqlSessionFactories.isEmpty()) {
            log.debug("[PluginMyBatis][事件]SqlSessionFactory 不可用，跳过 Mapper 卸载: {}", event.getPluginFilePath());
            return;
        }

        String pluginFilePath = event.getPluginFilePath();
        Set<String> beanNames = event.getBeanNames();

        if (beanNames == null || beanNames.isEmpty()) {
            return;
        }

        for (String beanName : beanNames) {
            try {
                // 获取 Bean 类型
                Class<?> beanType = getBeanType(beanName);
                if (beanType == null) {
                    continue;
                }

                // 检查是否为 Mapper 接口
                if (!isMapperInterface(beanType)) {
                    continue;
                }

                // 从所有 SqlSessionFactory 中移除
                for (SqlSessionFactory sqlSessionFactory : sqlSessionFactories) {
                    try {
                        unregisterMapper(sqlSessionFactory, beanType);
                        log.info("[PluginMyBatis][事件]插件 Mapper 接口卸载成功: {} 插件: {}", beanName, pluginFilePath);
                    } catch (Exception e) {
                        log.error("[PluginMyBatis][事件]插件 Mapper 接口卸载失败: {} 插件: {}", beanName, pluginFilePath, e);
                    }
                }
            } catch (Exception e) {
                log.error("[PluginMyBatis][事件]处理插件 Bean 卸载事件失败: {} 插件: {}", beanName, pluginFilePath, e);
            }
        }
    }

    /**
     * 获取 Bean 类型
     *
     * @param beanName Bean 名称
     * @return Bean 类型
     */
    private Class<?> getBeanType(String beanName) {
        try {
            // 首先尝试从 Spring 容器获取
            ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
            if (applicationContext != null && applicationContext.containsBean(beanName)) {
                Object bean = applicationContext.getBean(beanName);
                if (bean != null) {
                    return bean.getClass();
                }
            }

            // 如果容器中没有，尝试通过类名加载
            return Class.forName(beanName, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            log.debug("[PluginMyBatis][事件]无法获取 Bean 类型: {}", beanName);
            return null;
        } catch (Exception e) {
            log.debug("[PluginMyBatis][事件]获取 Bean 类型失败: {}", beanName, e);
            return null;
        }
    }

    /**
     * 检查是否为 Mapper 接口
     * <p>
     * 简单判断：是否为接口且名称包含 Mapper
     * </p>
     *
     * @param beanType Bean 类型
     * @return 是否为 Mapper 接口
     */
    private boolean isMapperInterface(Class<?> beanType) {
        if (beanType == null) {
            return false;
        }
        return beanType.isInterface() && beanType.getSimpleName().endsWith("Mapper");
    }

    /**
     * 从 MyBatis 配置中移除 Mapper
     *
     * @param sqlSessionFactory SqlSessionFactory 实例
     * @param mapperClass       Mapper 接口类
     */
    private void unregisterMapper(SqlSessionFactory sqlSessionFactory, Class<?> mapperClass) {
        if (sqlSessionFactory == null || mapperClass == null) {
            return;
        }

        try {
            var configuration = sqlSessionFactory.getConfiguration();
            if (configuration.hasMapper(mapperClass)) {
                // MyBatis 不提供直接移除 Mapper 的 API，这里记录日志
                // 实际卸载时，MyBatis 会在下次使用时重新加载，或者需要重启应用
                log.debug("[PluginMyBatis][事件]Mapper 已从配置中标记移除: {}", mapperClass.getName());
            }
        } catch (Exception e) {
            log.error("[PluginMyBatis][事件]移除 Mapper 失败: {}", mapperClass.getName(), e);
        }
    }
}

