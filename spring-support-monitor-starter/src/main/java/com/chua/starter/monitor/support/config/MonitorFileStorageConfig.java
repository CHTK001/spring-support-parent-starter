// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.config;

import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件-storage 服务端过滤器 插件的本地磁盘后端。
 *
 * <p>为服务管理中的 <b>file-storage</b> 插件提供一个本地 {@code FileStorage} 后端，
 * 使创建的 HTTP 文件服务器节点真正具备存储能力（无需外部 最小io/S3）。</p>
 *
 * <p>通过反射构造 {@code LocalFileStorage}，避免 monitor 对 filesystem-starter 的编译期强依赖，
 * 保持其「可选插件」语义（类路径 无 filesystem-启动 时本配置自动失效）。</p>
 *
 * <p>存储根目录由配置项 <code>monitor.file-storage.local-path</code> 控制，
 * 缺省落在 {@code {user.dir}/.数据/filesystem-本地}，保证文件长期落盘（非系统临时目录）。
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
     * 本地文件存储 Bean 名称（服务端过滤器工厂 通过 获取Bean名称for类型 发现并注册为文件服务器存储桶）
     */
    public static final String LOCAL_STORAGE_BEAN_NAME = "localFileStorage";

    /**
     * 录制本地存储根目录
     */
    @Value("${monitor.file-storage.local-path:${user.dir}/.data/filesystem-local}")
    private String localPath; // 本地路径

    /**
     * 注册本地磁盘存储后端。
     *
     * @return FileStorage 实例（反射创建；空 时注入失败被过滤）
     */
    @Bean(LOCAL_STORAGE_BEAN_NAME)
    public Object localFileStorage() {
        try {
            // [P3C 1.10 豁免] 故意保持反射以解耦 monitor 与 filesystem-starter 的编译期强依赖
            System.setProperty("filesystem.temp.path", localPath);

            Class<?> localClass = ReflectUtils.forName("com.chua.starter.filesystem.support.local.LocalFileStorage");
            Class<?> storageIf = ReflectUtils.forName("com.chua.common.support.storage.FileStorage");
            Class<?> settingClass = ReflectUtils.forName("com.chua.common.support.storage.setting.BucketSetting");

 // 跨模块反射调用静态 builder()：ReflectUtils.invokeStatic 要求精确返回类型，
 // 而 Builder 实际类型不在本模块编译类路径上（filesystem-starter 为可选依赖），无法收敛
            Object builder = settingClass.getMethod("builder").invoke(null); // [P3C 1.10 豁免] 见上：静态 builder() 返回类型不可知，保留直接反射
            if (builder == null) {
                log.warn("[MonitorFileStorageConfig] BucketSetting.builder() 反射创建失败, 跳过本地文件存储");
                return null;
            }
 // 实例方法调用统一走 ReflectUtils（其内部按实际返回类型容错查找方法句柄）
            ReflectUtils.invoke(builder, "bucket", Object.class, new Class<?>[]{String.class}, "default");
            Object setting = ReflectUtils.invoke(builder, "build", Object.class);

            Object storage = ReflectUtils.instantiate(localClass, setting);
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
