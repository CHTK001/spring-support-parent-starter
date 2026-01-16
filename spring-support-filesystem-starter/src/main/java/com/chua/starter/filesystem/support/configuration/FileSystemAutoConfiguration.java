package com.chua.starter.filesystem.support.configuration;

import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.server.FileServerManager;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.chua.starter.common.support.logger.ModuleLog.*;

/**
 * 文件存储自动配置
 * <p>
 * 自动配置文件存储模板和 HTTP 文件服务器
 * </p>
 *
 * @author CH
 * @since 2024/12/28
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
@ConditionalOnProperty(prefix = FileStorageProperties.PREFIX, name = "enable", havingValue = "true")
public class FileSystemAutoConfiguration {

    private final FileStorageProperties properties;
    private FileServerManager serverManager;

    public FileSystemAutoConfiguration(FileStorageProperties properties) {
        this.properties = properties;
    }

    /**
     * 创建文件存储模板
     */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageTemplate fileStorageTemplate() {
        FileStorageTemplate template = new FileStorageTemplate(properties);
        template.initialize();
        return template;
    }

    /**
     * 创建文件服务器管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public FileServerManager fileServerManager(FileStorageTemplate storageTemplate) {
        this.serverManager = new FileServerManager(properties, storageTemplate);
        return serverManager;
    }

    /**
     * 初始化后启动服务器
     */
    @PostConstruct
    public void init() {
        log.info("[FileSystem] ══════════════════════════════════════════════");
        log.info("[FileSystem] 文件存储模块配置");
        log.info("[FileSystem] ├─ 存储后端数量: {}", highlight(properties.getStorages().size()));
        log.info("[FileSystem] ├─ 服务器数量: {}", highlight(properties.getServers().size()));
        log.info("[FileSystem] ├─ 预览功能: [{}]", status(properties.isOpenPreview()));
        log.info("[FileSystem] ├─ 下载功能: [{}]", status(properties.isOpenDownload()));
        log.info("[FileSystem] ├─ 断点续传: [{}]", status(properties.isOpenRange()));
        log.info("[FileSystem] └─ 水印功能: [{}]", status(properties.isOpenWatermark()));
        log.info("[FileSystem] ══════════════════════════════════════════════");
    }

    /**
     * 启动文件服务器（在 Bean 初始化完成后）
     */
    @Bean
    public FileServerStarter fileServerStarter(FileServerManager serverManager) {
        return new FileServerStarter(serverManager);
    }

    /**
     * 销毁时停止服务器
     */
    @PreDestroy
    public void destroy() {
        if (serverManager != null) {
            serverManager.stopAll();
        }
        log.info("[FileSystem] 文件存储模块已关闭");
    }

    /**
     * 服务器启动器（延迟启动，确保所有 Bean 初始化完成）
     */
    public static class FileServerStarter {

        private final FileServerManager serverManager;

        public FileServerStarter(FileServerManager serverManager) {
            this.serverManager = serverManager;
            // 启动服务器
            serverManager.startAll();
        }
    }
}
