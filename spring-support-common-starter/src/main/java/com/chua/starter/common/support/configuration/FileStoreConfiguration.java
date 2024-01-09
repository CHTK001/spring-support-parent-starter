package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.filestorage.FileStorageLoggerService;
import com.chua.starter.common.support.filestorage.FileStorageProperties;
import com.chua.starter.common.support.filestorage.FileStorageProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 文件存储配置
 *
 * @author CH
 */
@Slf4j
@ConditionalOnProperty(prefix = FileStorageProperties.PRE, name = "enable", havingValue = "true")
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

}
