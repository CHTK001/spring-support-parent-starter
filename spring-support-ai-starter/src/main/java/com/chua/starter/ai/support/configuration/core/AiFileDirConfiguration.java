package com.chua.starter.ai.support.configuration.core;

import com.chua.starter.filesystem.support.controller.FileDirController;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 服务器目录浏览接口独立配置。
 *
 * <p>{@code FileSystemAutoConfiguration} 因会启动文件服务被排除，
 * 此处仅按 {@code plugin.filesystem.dir-list} 开关注册目录浏览控制器，
 * 供前端「上传目录」选择器对接服务器目录。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
@ConditionalOnProperty(prefix = FileStorageProperties.PREFIX, name = "dir-list", havingValue = "true")
public class AiFileDirConfiguration {

    /**
     * 注册服务器目录浏览控制器。
     *
     * @param properties 文件存储配置（含 dir-list 开关）
     * @return 目录浏览控制器
     */
    @Bean
    public FileDirController fileDirController(FileStorageProperties properties) {
        return new FileDirController(properties);
    }
}
