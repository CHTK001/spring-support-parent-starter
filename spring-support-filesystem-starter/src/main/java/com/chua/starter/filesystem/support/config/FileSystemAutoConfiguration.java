package com.chua.starter.filesystem.support.config;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.setting.BucketSetting;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.filesystem.support.controller.FileChunkUploadController;
import com.chua.starter.filesystem.support.entity.SysFileSystemUpload;
import com.chua.starter.filesystem.support.mapper.SysFileSystemUploadMapper;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.properties.FileStorageUploadProperties;
import com.chua.starter.filesystem.support.service.SysFileSystemUploadService;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import com.chua.starter.filesystem.support.utils.FileStorageSupport;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 文件系统自动配置类。
 *
 * @author CH
 * @since 1.0
 */
@Configuration
@EnableConfigurationProperties({FileStorageProperties.class, FileStorageUploadProperties.class})
@MapperScan(basePackages = "com.chua.starter.filesystem.support.mapper")
@ConditionalOnProperty(prefix = FileStorageProperties.PREFIX, name = "enable", havingValue = "true")
public class FileSystemAutoConfiguration {

    @Bean
    public FileStorageTemplate fileStorageTemplate(FileStorageProperties properties) {
        FileStorageTemplate template = new FileStorageTemplate(properties);
        template.initialize();
        return template;
    }

    @Bean
    @ConditionalOnMissingBean
    public SysFileSystemUploadService sysFileSystemUploadService(SysFileSystemUploadMapper uploadMapper) {
        return new com.chua.starter.filesystem.support.service.impl.SysFileSystemUploadServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public FileChunkUploadController fileChunkUploadController(
            SysFileSystemUploadService uploadService,
            FileStorageTemplate fileStorageTemplate,
            FileStorageProperties fileStorageProperties) {
        return new FileChunkUploadController(uploadService, fileStorageTemplate, fileStorageProperties);
    }
}
