package com.chua.report.client.starter.configuration;

import com.chua.report.client.starter.controller.FileManagementController;
import com.chua.report.client.starter.controller.HealthController;
import com.chua.report.client.starter.properties.FileManagementProperties;
import com.chua.report.client.starter.service.ClientHealthService;
import com.chua.report.client.starter.service.FileManagementService;
import com.chua.report.client.starter.service.impl.ClientHealthServiceImpl;
import com.chua.report.client.starter.service.impl.FileManagementServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件管理自动配置
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(FileManagementProperties.class)
@ConditionalOnProperty(prefix = "plugin.report.client.file-management", name = "enable", havingValue = "true")
public class FileManagementConfiguration {

    /**
     * 文件管理属性配置
     */
    @Bean
    @ConditionalOnMissingBean
    public FileManagementProperties fileManagementProperties() {
        FileManagementProperties properties = new FileManagementProperties();
        log.info("文件管理配置初始化完成: rootDirectory={}, maxFileSize={}, healthValidityPeriod={}s", 
            properties.getRootDirectory(), properties.getMaxFileSize(), properties.getHealthValidityPeriod());
        return properties;
    }

    /**
     * 客户端健康状态服务
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientHealthService clientHealthService(FileManagementProperties properties) {
        ClientHealthServiceImpl service = new ClientHealthServiceImpl(properties);
        log.info("客户端健康状态服务初始化完成: validityPeriod={}s", properties.getHealthValidityPeriod());
        return service;
    }

    /**
     * 文件管理服务
     */
    @Bean
    @ConditionalOnMissingBean
    public FileManagementService fileManagementService(FileManagementProperties properties) {
        FileManagementServiceImpl service = new FileManagementServiceImpl(properties);
        log.info("文件管理服务初始化完成: rootDirectory={}, allowedOperations={}", 
            properties.getRootDirectory(), getEnabledOperations(properties));
        return service;
    }

    /**
     * 健康检查控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public HealthController healthController(ClientHealthService clientHealthService) {
        HealthController controller = new HealthController(clientHealthService);
        log.info("健康检查控制器初始化完成");
        return controller;
    }

    /**
     * 文件管理控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public FileManagementController fileManagementController(
            FileManagementService fileManagementService,
            ClientHealthService clientHealthService) {
        
        FileManagementController controller = new FileManagementController(fileManagementService, clientHealthService);
        log.info("文件管理控制器初始化完成");
        return controller;
    }

    /**
     * 获取启用的操作列表
     */
    private String getEnabledOperations(FileManagementProperties properties) {
        StringBuilder operations = new StringBuilder();

        if (properties.isAllowCreateDirectory()) {
            operations.append("CREATE_DIR,");
        }
        if (properties.isAllowDeleteFile()) {
            operations.append("DELETE_FILE,");
        }
        if (properties.isAllowDeleteDirectory()) {
            operations.append("DELETE_DIR,");
        }
        if (properties.isAllowRenameFile()) {
            operations.append("RENAME,");
        }
        if (properties.isAllowMoveFile()) {
            operations.append("MOVE,");
        }
        if (properties.isAllowChangePermissions()) {
            operations.append("CHMOD,");
        }

        operations.append("LIST,UPLOAD,DOWNLOAD,INFO,PREVIEW,SEARCH,TREE");

        return operations.toString();
    }
}
