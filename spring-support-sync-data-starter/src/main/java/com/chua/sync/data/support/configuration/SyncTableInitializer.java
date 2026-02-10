package com.chua.sync.data.support.configuration;

import com.chua.sync.data.support.properties.SyncProperties;
import com.chua.sync.data.support.service.sync.SyncTableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * 同步任务表自动初始化器
 * <p>
 * 根据配置 plugin.sync.auto-create-table=true 自动创建同步相关表
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(SyncProperties.class)
@ConditionalOnProperty(prefix = SyncProperties.PRE, name = "enabled", havingValue = "true", matchIfMissing = true)
public class SyncTableInitializer {

    private final SyncProperties syncProperties;
    private final SyncTableService syncTableService;

    /**
     * 应用启动完成后执行初始化
     * <p>
     * 这是系统级别的自动初始化，业务级别请使用 SyncTableService.initializeTables()
     * </p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!syncProperties.isAutoCreateTable()) {
            log.debug("同步表自动创建已禁用 (plugin.sync.auto-create-table=false)");
            return;
        }

        try {
            if (syncTableService.isTableExists()) {
                log.info("同步任务表已存在，跳过自动创建");
                return;
            }

            log.info("开始自动创建同步任务相关表...");
            syncTableService.initializeTables(false);
            log.info("同步任务相关表创建成功");

        } catch (Exception e) {
            log.error("自动创建同步任务表失败", e);
            // 不抛出异常，避免影响应用启动
        }
    }
}
