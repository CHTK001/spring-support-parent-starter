package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.filestorage.DefaultFileStorageService;
import com.chua.starter.common.support.filestorage.FileStorageProperties;
import com.chua.starter.common.support.filestorage.FileStorageProvider;
import com.chua.starter.common.support.filestorage.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 文件存储配置
 *
 * @author CH
 */
@Slf4j
@ConditionalOnProperty(prefix = FileStorageProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
public class FileStoreConfiguration {


    /**
     * 文件存储提供程序
     *
     * @return {@link FileStorageProvider}
     */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageProvider fileStorageProvider() {
        log.info(">>>>>>> 开启文件服务器[OSS]");
        return new FileStorageProvider();
    }

    /**
     * 文件存储提供程序
     *
     * @return {@link FileStorageProvider}
     */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageService fileStorageService() {
        log.info(">>>>>>> 开启文件服务器存储器[OSS]");
        return new DefaultFileStorageService();
    }

}
