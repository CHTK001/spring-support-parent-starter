package com.chua.starter.filesystem.support.config;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.setting.BucketSetting;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import com.chua.starter.filesystem.support.controller.FileChunkUploadController;
import com.chua.starter.filesystem.support.controller.FileDirController;
import com.chua.starter.filesystem.support.server.FileServerManager;
import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;
import com.chua.starter.filesystem.support.mapper.SysFileSystemUploadMapper;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.properties.FileStorageUploadProperties;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadChunkService;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadService;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import com.chua.starter.filesystem.support.utils.FileStorageSupport;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 文件系统自动配置类。
 *
 * @since 1.0
 * @author CH
 */
@Configuration
@EnableConfigurationProperties({FileStorageProperties.class, FileStorageUploadProperties.class})
@MapperScan(basePackages = "com.chua.starter.filesystem.support.mapper")
@ConditionalOnProperty(prefix = FileStorageProperties.PREFIX, name = "enable", havingValue = "true")
public class FileSystemAutoConfiguration {

    @Bean
    /**
     * 文件storagetemplate
     *
     * @param properties 属性
     * @return 文件storagetemplate的结果
     */
    public FileStorageTemplate fileStorageTemplate(FileStorageProperties properties) {
        FileStorageTemplate template = new FileStorageTemplate(properties);
        template.initialize();
        return template;
    }

    @Bean
    @ConditionalOnMissingBean
    /**
     * sys文件系统upload服务
     *
     * @param uploadMapper upload映射器
     * @return sys文件系统upload服务的结果
     */
    public SysFileSystemUploadService sysFileSystemUploadService(SysFileSystemUploadMapper uploadMapper) {
        return new com.chua.starter.filesystem.support.service.impl.SysFileSystemUploadServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    /**
      * 文件chunkupload控制器
     * @param uploadService upload服务
     * @param chunkService chunk服务
     * @param fileStorageTemplate 文件storagetemplate
     * @param fileStorageProperties 文件storage属性
     * @param uploadProperties upload属性
     * @param fileServerManager 文件服务端管理器
     * @param chunkService chunk服务
     * @param fileStorageTemplate 文件storagetemplate
     * @param fileStorageProperties 文件storage属性
     * @param uploadProperties upload属性
     * @param fileServerManager 文件服务端管理器
     * @param properties 属性
     * @param true true
     * @param properties 属性
     * @param template template
     * @param applicationContext application上下文
     * @param template template
     * @param applicationContext application上下文
     */
    public FileChunkUploadController fileChunkUploadController(
            SysFileSystemUploadService uploadService,
            SysFileSystemUploadChunkService chunkService,
            FileStorageTemplate fileStorageTemplate,
            FileStorageProperties fileStorageProperties,
            FileStorageUploadProperties uploadProperties,
            FileServerManager fileServerManager) {
        return new FileChunkUploadController(uploadService, chunkService, fileStorageTemplate,
                fileStorageProperties, uploadProperties, fileServerManager);
    }

    @Bean
    @ConditionalOnMissingBean
    /**
     * 文件dir控制器
     *
     * @param properties 属性
     * @return 文件dir控制器的结果
     */
    public FileDirController fileDirController(FileStorageProperties properties) {
        /**
         * 文件dir控制器。
         * @param properties 属性
         * @return 文件dir控制器的结果
         */
        return new FileDirController(properties);
    }

    @Bean
    /**
     * 文件系统服务信息contributor
     *
     * @return 文件系统服务信息contributor的结果
     */
    public ServiceInfoContributor fileSystemServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("文件系统", "分片上传", "/chunk/create,upload,complete", "分片上传");
            registry.registerEndpoint("文件系统", "文件预览", "preview", "文件预览");
            registry.registerEndpoint("文件系统", "文件下载", "download", "文件下载");
            registry.registerFlag("filesystem", "文件系统", true, "文件存储模块");
        };
    }

    @Bean
    /**
     * 文件服务端管理器
     *
     * @param properties 属性
     * @param template template
     * @param applicationContext application上下文
     * @return 文件服务端管理器的结果
     */
    public FileServerManager fileServerManager(FileStorageProperties properties, FileStorageTemplate template, ApplicationContext applicationContext) {
        FileServerManager manager = new FileServerManager(properties, template, applicationContext);
        manager.startAll();
        return manager;
    }
}
