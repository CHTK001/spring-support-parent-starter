package com.chua.starter.monitor.support.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * file-storage ServerFilter 插件的本地磁盘后端。
 *
 * <p>为服务管理中的 <b>file-storage</b> 插件提供一个本地 {@code FileStorage} 后端，
 * 使创建的 HTTP 文件服务器节点真正具备存储能力（无需外部 MinIO/S3）。</p>
 *
 * <p>通过反射构造 {@code LocalFileStorage}，避免 monitor 对 filesystem-starter 的编译期强依赖，
 * 保持其「可选插件」语义（classpath 无 filesystem-starter 时本配置自动失效）。</p>
 *
 * <p>存储根目录由配置项 <code>monitor.file-storage.local-path</code> 控制，
 * 缺省落在 {@code {user.dir}/.data/filesystem-local}，保证文件长期落盘（非系统临时目录）。
 * 该值会同步注入 {@code filesystem.temp.path} 系统属性，供 {@code LocalFileStorage} 解析根目录。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "com.chua.starter.filesystem.support.local.LocalFileStorage")
public class MonitorFileStorageConfig {

    /**
     * 本地文件存储 Bean 名称（ServerFilterFactory 通过 getBeanNamesForType 发现并注册为文件服务器存储桶）
     */
    public static final String LOCAL_STORAGE_BEAN_NAME = "localFileStorage";

    /**
     * 录制本地存储根目录
     */
    @Value("${monitor.file-storage.local-path:${user.dir}/.data/filesystem-local}")
    private String localPath;

    /**
     * 注册本地磁盘存储后端。
     *
     * @return FileStorage 实例（反射创建；null 时注入失败被过滤）
     */
    @Bean(LOCAL_STORAGE_BEAN_NAME)
    public Object localFileStorage() {
        try {
            // 让 LocalFileStorage 落在可配置的持久目录而非系统临时目录
            System.setProperty("filesystem.temp.path", localPath); 

            Class<?> localClass = Class.forName("com.chua.starter.filesystem.support.local.LocalFileStorage");
            Class<?> storageIf = Class.forName("com.chua.common.support.storage.FileStorage");
            Class<?> settingClass = Class.forName("com.chua.common.support.storage.setting.BucketSetting");

            // BucketSetting.builder().bucket("default").build()
            Object builder = settingClass.getMethod("builder").invoke(null);
            builder.getClass().getMethod("bucket", String.class).invoke(builder, "default");
            Object setting = builder.getClass().getMethod("build").invoke(builder);

            // new LocalFileStorage(BucketSetting)
            Object storage = localClass.getDeclaredConstructor(settingClass).newInstance(setting);
            if (storageIf.isInstance(storage)) {
                log.info("[MonitorFileStorageConfig] 本地文件存储已就绪: 桶=default, 根目录={}", localPath);
                return storage;
            }
            log.warn("[MonitorFileStorageConfig] {} 未实现 FileStorage 接口, 跳过", localClass.getName());
            return null;
        } catch (Exception e) {
            log.warn("[MonitorFileStorageConfig] 本地文件存储后端创建失败(可忽略): {}", e.getMessage());
            return null;
        }
    }
}
