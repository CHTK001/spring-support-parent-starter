package com.chua.starter.filesystem.support.config;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.filesystem.support.controller.FileDirController;
import com.chua.starter.filesystem.support.controller.FileObjectController;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import com.chua.starter.filesystem.support.properties.FileStorageUploadProperties;
import com.chua.starter.filesystem.support.server.FileServerManager;
import com.chua.starter.filesystem.support.server.FileStorageServerLauncher;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件系统自动配置类。
 *
 * @since 1.0
 * @author CH
 */
@Configuration
@EnableConfigurationProperties({FileStorageProperties.class, FileStorageUploadProperties.class,
        FileStorageServerProperties.class})
@ConditionalOnProperty(prefix = FileStorageProperties.PREFIX, name = "enable", havingValue = "true")
public class FileSystemAutoConfiguration {

    /**
     * 创建文件存储模板。
     *
     * @param properties 文件存储配置
     * @return 文件存储模板
     */
    @Bean
    public FileStorageTemplate fileStorageTemplate(FileStorageProperties properties) {
        FileStorageTemplate template = new FileStorageTemplate(properties);
        template.initialize();
        return template;
    }

    /**
     * 创建文件对象控制器。
     *
     * @param fileStorageTemplate 文件存储模板
     * @param uploadProperties 上传配置
     * @return 文件对象控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public FileObjectController fileObjectController(
            FileStorageTemplate fileStorageTemplate,
            FileStorageUploadProperties uploadProperties) {
        return new FileObjectController(fileStorageTemplate, uploadProperties);
    }

    /**
     * 创建文件目录控制器。
     *
     * @param properties 文件存储配置
     * @return 文件目录控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public FileDirController fileDirController(FileStorageProperties properties) {
        return new FileDirController(properties);
    }

    /**
     * 创建文件系统服务信息贡献者。
     *
     * @return 文件系统服务信息贡献者
     */
    @Bean
    public ServiceInfoContributor fileSystemServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("文件系统", "单次上传", "/v2/file/upload", "小文件单次上传");
            registry.registerEndpoint("文件系统", "文件回显", "/v2/file/view/**", "匿名直链读取对象");
            registry.registerEndpoint("文件系统", "文件服务器", "http://127.0.0.1:18082",
                    "独立文件存储网关（随应用自动启动，见 plugin.filesystem.storage-server）");
            registry.registerFlag("filesystem", "文件系统", true, "文件存储模块");
        };
    }

    /**
     * 创建文件服务端管理器。
     *
     * @param properties 文件存储配置
     * @param template 文件存储模板
     * @param applicationContext 应用上下文
     * @return 文件服务端管理器
     */
    @Bean
    public FileServerManager fileServerManager(FileStorageProperties properties, FileStorageTemplate template, ApplicationContext applicationContext) {
        FileServerManager manager = new FileServerManager(properties, template, applicationContext);
        manager.startAll();
        return manager;
    }

    /**
     * 创建文件存储服务器启动器。
     *
     * @param properties 文件服务器配置
     * @param template 文件存储模板
     * @return 文件存储服务器启动器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = FileStorageServerProperties.PRE, name = "enable",
            havingValue = "true", matchIfMissing = true)
    public FileStorageServerLauncher fileStorageServerLauncher(FileStorageServerProperties properties,
                                                               FileStorageTemplate template) {
        FileStorageServerLauncher launcher = new FileStorageServerLauncher(properties, template);
        launcher.start();
        return launcher;
    }
}
