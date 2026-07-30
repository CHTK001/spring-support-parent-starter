package com.chua.starter.job.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;

/**
 * Job 表初始化器（简化版）。
 * <p>
 * 在启动阶段根据配置决定是否初始化表结构。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class JobSchemaInitializer implements InitializingBean {

    private final JobProperties jobProperties;
    private final ObjectProvider<DataSource> dataSourceProvider;

    @Override
    public void afterPropertiesSet() {
        JobSchemaInitMode initMode = jobProperties.getTableInitMode();
        if (initMode == null || !initMode.isEnabled()) {
            log.debug("[JobSchemaInitializer] 表初始化模式为 NONE，跳过");
            return;
        }

        log.info("[JobSchemaInitializer] 表初始化模式: {}", initMode);
        // 简化实现：仅记录日志，实际表结构由 Flyway 或 Liquibase 管理
    }
}
