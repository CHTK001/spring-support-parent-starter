package com.chua.starter.filesystem.support.config;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.filesystem.support.controller.FileDirController;
import com.chua.starter.filesystem.support.controller.FileObjectController;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import com.chua.starter.filesystem.support.properties.FileStorageUploadProperties;
import com.chua.starter.filesystem.support.server.FileServerManager;
import com.chua.starter.filesystem.support.server.FileStorageServerLauncher;
import com.chua.starter.filesystem.support.server.embedded.FileStorageEmbeddedFilter;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 文件系统自动配置类。
 *
 * <p>文件服务器支持两种部署形态，由
 * {@code plugin.filesystem.storage-server.mode} 选择：</p>
 *
 * <ul>
 *   <li><b>embedded</b>（默认）：{@link FileStorageEmbeddedFilter} 挂进宿主应用，
 *       走应用端口 + {@code path-prefix}（默认 {@code /fs}），不额外占端口；</li>
 *   <li><b>standalone</b>：{@link FileStorageServerLauncher} 另起独立 HTTP 服务，
 *       监听 {@code host:port}（默认 18082）。</li>
 * </ul>
 *
 * <p>两种形态共用 {@code FileStorageServerAssembler} 的装配逻辑（存储桶、CORS、
 * 安全头、私有桶鉴权），保证 URL 语义与开关行为一致。</p>
 *
 * @since 1.0
 * @author CH
 */
@Configuration
@EnableConfigurationProperties({FileStorageProperties.class, FileStorageUploadProperties.class,
        FileStorageServerProperties.class})
@ConditionalOnProperty(prefix = FileStorageProperties.PREFIX, name = "enable", havingValue = "true")
public class FileSystemAutoConfiguration {

    /**
     * 创建文件存储模板。
     *
     * @param properties 文件存储配置
     * @return 文件存储模板
     */
    @Bean
    public FileStorageTemplate fileStorageTemplate(FileStorageProperties properties) {
        FileStorageTemplate template = new FileStorageTemplate(properties);
        template.initialize();
        return template;
    }

    /**
     * 创建文件对象控制器。
     *
     * @param fileStorageTemplate 文件存储模板
     * @param uploadProperties 上传配置
     * @return 文件对象控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public FileObjectController fileObjectController(
            FileStorageTemplate fileStorageTemplate,
            FileStorageUploadProperties uploadProperties) {
        return new FileObjectController(fileStorageTemplate, uploadProperties);
    }

    /**
     * 创建文件目录控制器。
     *
     * @param properties 文件存储配置
     * @return 文件目录控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public FileDirController fileDirController(FileStorageProperties properties) {
        return new FileDirController(properties);
    }

    /**
     * 创建文件系统服务信息贡献者。
     *
     * @param serverProperties 文件服务器配置（用于按形态给出真实地址）
     * @return 文件系统服务信息贡献者
     */
    @Bean
    public ServiceInfoContributor fileSystemServiceInfoContributor(FileStorageServerProperties serverProperties) {
        return registry -> {
            registry.registerEndpoint("文件系统", "单次上传", "/v2/file/upload", "小文件单次上传");
            registry.registerEndpoint("文件系统", "文件回显", "/v2/file/view/**", "匿名直链读取对象");
            registry.registerEndpoint("文件系统", "文件服务器", fileServerEndpoint(serverProperties),
                    serverProperties.isStandalone()
                            ? "独立文件存储网关（单独端口，见 plugin.filesystem.storage-server.mode=standalone）"
                            : "内置文件存储接口（挂载在应用端口，路径前缀见 path-prefix）");
            registry.registerFlag("filesystem", "文件系统", true, "文件存储模块");
        };
    }

    /**
     * 文件服务器对外地址（按部署形态给出）。
     *
     * @param properties 文件服务器配置
     * @return 独立形态返回 {@code http://host:port/}；内置形态返回应用内路径前缀
     */
    private static String fileServerEndpoint(FileStorageServerProperties properties) {
        if (properties.isStandalone()) {
            String host = properties.getHost() == null || properties.getHost().isBlank()
                    || "0.0.0.0".equals(properties.getHost()) ? "127.0.0.1" : properties.getHost();
            return "http://" + host + ":" + properties.getPort() + "/";
        }
        String prefix = properties.normalizedPathPrefix();
        return (prefix.isEmpty() ? "" : prefix) + "/{桶}/{键}";
    }

    /**
     * 创建文件服务端管理器。
     *
     * @param properties 文件存储配置
     * @param template 文件存储模板
     * @param applicationContext 应用上下文
     * @return 文件服务端管理器
     */
    @Bean
    public FileServerManager fileServerManager(FileStorageProperties properties, FileStorageTemplate template, ApplicationContext applicationContext) {
        FileServerManager manager = new FileServerManager(properties, template, applicationContext);
        manager.startAll();
        return manager;
    }

    /**
     * 内置形态：把完整文件服务协议挂进宿主应用（默认）。
     *
     * <p>类级条件控制总开关（{@code storage-server.enable}），方法级条件控制形态，
     * 两个条件作用在不同层级，避免依赖 {@code @ConditionalOnProperty} 的重复注解能力。</p>
     */
    @Configuration
    @ConditionalOnProperty(prefix = FileStorageServerProperties.PRE, name = "enable",
            havingValue = "true", matchIfMissing = true)
    static class EmbeddedServerConfiguration {

        /**
         * 注册内置文件服务器过滤器：{@code {path-prefix}/*}。
         *
         * @param properties 文件服务器配置
         * @param template   文件存储模板
         * @return 过滤器注册
         */
        @Bean
        @ConditionalOnProperty(prefix = FileStorageServerProperties.PRE, name = "mode",
                havingValue = "embedded", matchIfMissing = true)
        public FilterRegistrationBean<FileStorageEmbeddedFilter> fileStorageEmbeddedFilter(
                FileStorageServerProperties properties, FileStorageTemplate template) {
            FileStorageEmbeddedFilter filter = new FileStorageEmbeddedFilter(properties, template);
            String prefix = properties.normalizedPathPrefix();
            FilterRegistrationBean<FileStorageEmbeddedFilter> registration =
                    new FilterRegistrationBean<>(filter);
            registration.addUrlPatterns(prefix.isEmpty() ? "/*" : prefix + "/*");
            registration.setName("fileStorageEmbeddedFilter");
            // 尽量靠前：文件服务器有自己的令牌体系（x-oauth-token），不应被应用侧
            // 安全链/表单过滤器先一步拦掉或消费请求体；同时仍排在字符编码过滤器之后。
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
            registration.setAsyncSupported(true);
            return registration;
        }
    }

    /**
     * 独立形态：另起独立 HTTP 服务（{@code mode=standalone}）。
     */
    @Configuration
    @ConditionalOnProperty(prefix = FileStorageServerProperties.PRE, name = "enable",
            havingValue = "true", matchIfMissing = true)
    static class StandaloneServerConfiguration {

        /**
         * 创建文件存储服务器启动器。
         *
         * @param properties 文件服务器配置
         * @param template   文件存储模板
         * @return 文件存储服务器启动器
         */
        @Bean(destroyMethod = "close")
        @ConditionalOnProperty(prefix = FileStorageServerProperties.PRE, name = "mode",
                havingValue = "standalone")
        public FileStorageServerLauncher fileStorageServerLauncher(FileStorageServerProperties properties,
                                                                   FileStorageTemplate template) {
            FileStorageServerLauncher launcher = new FileStorageServerLauncher(properties, template);
            launcher.start();
            return launcher;
        }
    }
}
