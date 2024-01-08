package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.filestorage.FileStorageLoggerService;
import com.chua.starter.common.support.filestorage.FileStorageProperties;
import com.chua.starter.common.support.filestorage.FileStorageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 文件存储配置
 *
 * @author CH
 */
@ConditionalOnProperty(prefix = FileStorageProperties.PRE, name = "enable", havingValue = "true")
public class FileStoreConfiguration {


    /**
     * 文件存储提供程序
     *
     * @return {@link FileStorageProvider}
     */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageProvider fileStorageProvider(FileStorageProperties fileStorageProperties,
                                                   @Autowired(required = false) FileStorageLoggerService fileStorageLoggerService) {
        return new FileStorageProvider(fileStorageProperties, fileStorageLoggerService);
    }

}
