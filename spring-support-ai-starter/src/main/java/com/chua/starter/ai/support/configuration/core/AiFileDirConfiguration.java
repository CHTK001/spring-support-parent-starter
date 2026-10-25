package com.chua.starter.ai.support.configuration.core;

import com.chua.starter.filesystem.support.controller.FileDirController;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * <p><b>本类是 {@link FileDirController} 的唯一注册点</b>，宿主应用不要
 * 再注册同名 Bean：{@code @Configuration} 类的装配顺序对
 * {@code @ConditionalOnMissingBean} 不可靠（该注解只对自动配置类有保证），
 * 两处都注册会抛 {@code BeanDefinitionOverrideException} 直接启动失败；
 * 两处都放行则会有两个 {@code @RestController} 抢同一路径映射。
 * 使用方只需打开 {@code plugin.filesystem.dir-list} 开关。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
@ConditionalOnProperty(prefix = FileStorageProperties.PREFIX, name = "dir-list", havingValue = "true")
public class AiFileDirConfiguration {

    /**
     * 注册服务器目录浏览控制器。
     *
     * @param properties 文件存储配置（含 dir-列表 开关）
     * @return 目录浏览控制器
     */
    @Bean
    public FileDirController fileDirController(FileStorageProperties properties) {
        /**
         * 文件dir控制器。
         * @param properties 属性
         * @return 文件dir控制器的结果
         */
        return new FileDirController(properties);
    }
}
