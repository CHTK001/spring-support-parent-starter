package com.chua.starter.monitor.support.config;

import com.chua.common.support.storage.FileStorage;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import com.chua.starter.filesystem.support.server.FileStorageServerLauncher;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件存储网关（随应用启动的独立文件服务器）。
 *
 * <p>monitor 排除了 filesystem-starter 的 {@code FileSystemAutoConfiguration}
 * （其 web 分片上传链引用 monitor 未装配的 Bean），导致随应用启动的
 * {@link FileStorageServerLauncher} 此前从未装配、文件服务器网关端口
 * （默认 18082）从未监听——前端文件管理页直连该端口必然拒绝连接。</p>
 *
 * <p>本配置只取所需：装配 {@link FileStorageTemplate} 与
 * {@link FileStorageServerLauncher}，不引入被排除的 web 上传链
 * （FileObjectController / FileDirController / FileServerManager）。</p>
 *
 * <p>存储后端复用 {@link MonitorFileStorageConfig} 注册的本地后端 Bean，
 * 以 Bean 名 {@value #BUCKET} 注册为网关存储桶——与前端
 * {@code app.yaml otherConfig.filestorage.bucket} 及
 * {@code FileAssetServiceImpl#LOCAL_BUCKET} 对齐，且与「服务管理」
 * file-storage 节点的按 Bean 名注册桶语义一致（文件资产页与网关共用同一后端）。</p>
 *
 * <p>开关：{@code plugin.filesystem.storage-server.enable}（默认 true，
 * 留空即随应用启动，置 false 关闭网关）；端口
 * {@code plugin.filesystem.storage-server.port}（默认 18082，
 * 与前端文件服务器页 DEFAULT_SERVER 及 Vite 代理目标对齐）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = {
        "com.chua.starter.filesystem.support.server.FileStorageServerLauncher",
        "com.chua.starter.filesystem.support.template.FileStorageTemplate"
})
@EnableConfigurationProperties({FileStorageProperties.class, FileStorageServerProperties.class})
public class MonitorFileStorageGatewayConfig {

    /**
     * 网关存储桶名称（与前端 app.yaml bucket、FileAssetServiceImpl#LOCAL_BUCKET 对齐）
     */
    public static final String BUCKET = "localFileStorage";

    /**
     * 创建文件存储模板并挂载 monitor 本地后端为网关桶。
     *
     * <p>模板先按 {@code plugin.filesystem.providers} 初始化（monitor 未配置时
     * 仅告警跳过），再把 {@link MonitorFileStorageConfig} 的本地后端以
     * {@value #BUCKET} 为名注册进 storageMap——FileStorageServerLauncher 启动时
     * 将 storageMap 全部注册为网关存储桶。</p>
     *
     * <p>无条件装配：即使关闭网关开关也保留该 Bean，使「服务管理」的
     * file-storage 节点（ServerFilterFactory 按名 {@code fileStorageTemplate}
     * 取模板）不再取空。</p>
     *
     * @param properties       文件存储配置
     * @param applicationContext 应用上下文（用于定位本地后端 Bean）
     * @return 文件存储模板
     */
    @Bean
    @ConditionalOnMissingBean(FileStorageTemplate.class)
    public FileStorageTemplate fileStorageTemplate(FileStorageProperties properties,
                                                   ApplicationContext applicationContext) {
        FileStorageTemplate template = new FileStorageTemplate(properties);
        template.initialize();
        Object backend = applicationContext.getBean(MonitorFileStorageConfig.LOCAL_STORAGE_BEAN_NAME);
        if (backend instanceof FileStorage storage) {
            template.addStorage(BUCKET, storage);
        } else {
            log.warn("[FileStorageGateway] 本地后端 {} 不是 FileStorage 实例: {}",
                    MonitorFileStorageConfig.LOCAL_STORAGE_BEAN_NAME,
                    backend == null ? "null" : backend.getClass().getName());
        }
        return template;
    }

    /**
     * 创建并启动文件存储服务器启动器（幂等；端口占用时告警跳过，不影响应用启动）。
     *
     * @param properties 文件服务器配置
     * @param template   文件存储模板
     * @return 文件存储服务器启动器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(FileStorageServerLauncher.class)
    @ConditionalOnProperty(prefix = FileStorageServerProperties.PRE, name = "enable",
            havingValue = "true", matchIfMissing = true)
    public FileStorageServerLauncher fileStorageServerLauncher(FileStorageServerProperties properties,
                                                               FileStorageTemplate template) {
        FileStorageServerLauncher launcher = new FileStorageServerLauncher(properties, template);
        launcher.start();
        return launcher;
    }
}
