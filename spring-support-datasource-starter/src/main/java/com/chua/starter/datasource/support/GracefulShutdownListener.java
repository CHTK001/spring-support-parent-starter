package com.chua.starter.datasource.support;

import com.chua.starter.datasource.datasource.MultiDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 数据源优雅关闭监听器
 * <p>
 * 在 Spring 容器关闭时，遍历所有已注册的数据源，依次执行关闭操作：
 * <ul>
 *   <li>HikariCP 等连接池会通过 {@link AutoCloseable} 或 {@link java.io.Closeable} 接口关闭</li>
 *   <li>{@link MultiDataSource} 的 Calcite 连接池将被归还并关闭</li>
 *   <li>非连接池类型的数据源不会被执行额外操作</li>
 * </ul>
 * </p>
 *
 * @since 2026/07/14
 * @author CH
 */
@Slf4j
public class GracefulShutdownListener implements ApplicationListener<ContextClosedEvent> {

    /** 关闭超时（秒） */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("[数据源] 开始优雅关闭数据源连接池...");
        long start = System.currentTimeMillis();

        Map<String, DataSource> allDataSources = DataSourceContextSupport.getAllDataSources();
        if (allDataSources.isEmpty()) {
            log.info("[数据源] 没有已注册的数据源，跳过关闭");
            return;
        }

        int closed = 0;
        int failed = 0;

        for (Map.Entry<String, DataSource> entry : allDataSources.entrySet()) {
            String dsName = entry.getKey();
            DataSource ds = entry.getValue();

            try {
                closeDataSource(dsName, ds);
                closed++;
            } catch (Exception e) {
                failed++;
                log.warn("[数据源] 关闭 '{}' 失败: {}", dsName, e.getMessage());
            }
        }

        // 清除分组注册表
        DataSourceGroupRegistry.clear();

        long elapsed = System.currentTimeMillis() - start;
        log.info("[数据源] 优雅关闭完成 (耗时: {}ms, 成功: {}, 失败: {})", elapsed, closed, failed);
    }

    /**
     * 尝试以多种方式关闭数据源
     */
    private void closeDataSource(String name, DataSource dataSource) throws Exception {
        if (dataSource == null) {
            return;
        }

        // 1. 尝试 DisposableBean 方式
        if (dataSource instanceof org.springframework.beans.factory.DisposableBean disposable) {
            log.debug("[数据源] 关闭 '{}' (DisposableBean)", name);
            disposable.destroy();
            return;
        }

        // 2. 尝试 AutoCloseable 方式
        if (dataSource instanceof AutoCloseable autoCloseable) {
            log.debug("[数据源] 关闭 '{}' (AutoCloseable)", name);
            autoCloseable.close();
            return;
        }

        // 3. 尝试 Closeable 方式
        if (dataSource instanceof java.io.Closeable closeable) {
            log.debug("[数据源] 关闭 '{}' (Closeable)", name);
            closeable.close();
            return;
        }

        // 4. 针对特定连接池的反射关闭
        try {
            closeByReflection(name, dataSource);
        } catch (Exception e) {
            log.debug("[数据源] '{}' 不支持关闭操作 (类型: {})", name, dataSource.getClass().getName());
        }
    }

    /**
     * 通过反射尝试关闭特定连接池
     */
    @SuppressWarnings("JavaReflectionMemberAccess")
    private void closeByReflection(String name, DataSource dataSource) throws Exception {
        Class<?> clazz = dataSource.getClass();

        // HikariCP
        if (clazz.getName().contains("HikariDataSource")) {
            java.lang.reflect.Method closeMethod = clazz.getMethod("close");
            closeMethod.invoke(dataSource);
            log.debug("[数据源] 关闭 '{}' (HikariCP 反射)", name);
            return;
        }

        // Druid
        if (clazz.getName().contains("DruidDataSource")) {
            java.lang.reflect.Method closeMethod = clazz.getMethod("close");
            closeMethod.invoke(dataSource);
            log.debug("[数据源] 关闭 '{}' (Druid 反射)", name);
            return;
        }

        // DBCP2
        if (clazz.getName().contains("BasicDataSource")) {
            java.lang.reflect.Method closeMethod = clazz.getMethod("close");
            closeMethod.invoke(dataSource);
            log.debug("[数据源] 关闭 '{}' (DBCP2 反射)", name);
            return;
        }

        // Tomcat JDBC
        if (clazz.getName().contains("DataSource") && clazz.getName().contains("tomcat")) {
            java.lang.reflect.Method closeMethod = clazz.getMethod("close");
            closeMethod.invoke(dataSource);
            log.debug("[数据源] 关闭 '{}' (Tomcat JDBC 反射)", name);
        }
    }
}
