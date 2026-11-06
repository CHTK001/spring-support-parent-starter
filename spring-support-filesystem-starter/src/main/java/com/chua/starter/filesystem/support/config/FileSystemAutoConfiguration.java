// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.filesystem.support.config;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.filesystem.support.controller.FileDirController;
import com.chua.starter.filesystem.support.controller.FileCollabController;
import com.chua.starter.filesystem.support.controller.FileObjectController;
import com.chua.starter.filesystem.support.controller.FileServerConfigController;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import com.chua.starter.filesystem.support.properties.FileStorageUploadProperties;
import com.chua.starter.filesystem.support.server.FileServerManager;
import com.chua.starter.filesystem.support.server.FileStorageServerLauncher;
import com.chua.starter.filesystem.support.server.embedded.FileStorageEmbeddedFilter;
import com.chua.starter.filesystem.support.controller.UploadTaskController;
import com.chua.starter.filesystem.support.task.UploadTaskManager;
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
       * 创建文件服务器运行期配置控制器（前端进入文件管理页前拉取当前形态与地址）。
       *
       * @param serverProperties 文件服务器配置
       * @param storageProperties 文件存储配置
       * @param contextPath      应用上下文路径（如 {@code /system/api}）
       * @return 文件服务器配置控制器
       */
    @Bean
    @ConditionalOnMissingBean
    public FileServerConfigController fileServerConfigController(
            FileStorageServerProperties serverProperties,
            FileStorageProperties storageProperties,
            @org.springframework.beans.factory.annotation.Value("${server.servlet.context-path:}") String contextPath) {
        return new FileServerConfigController(serverProperties, storageProperties, contextPath);
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
       * 创建上传任务管理器（时间轮调度 + 虚拟线程分片合并）。
       *
       * @param template 文件存储模板
       * @param properties 文件存储配置
       * @return 上传任务管理器
       */
    @Bean(destroyMethod = "shutdown")
    public UploadTaskManager uploadTaskManager(FileStorageTemplate template,
                                               FileStorageProperties properties) {
        return new UploadTaskManager(template, properties.getTaskExpireMinutes());
    }

      /**
       * 创建上传任务控制器（任务化分片上传）。
       *
       * @param uploadTaskManager 上传任务管理器
       * @return 上传任务控制器
       */
    @Bean
    @ConditionalOnMissingBean
    public UploadTaskController uploadTaskController(UploadTaskManager uploadTaskManager,
                                                     FileStorageTemplate template,
                                                     FileStorageProperties properties) {
        return new UploadTaskController(uploadTaskManager, template, properties);
    }

      /**
       * 协同文档存储器（内存态，与上传任务同生命周期）。
       *
       * <p>文件资产右键「创建协同」的落库容器：快照 + 版本乐观锁 + SSE 广播。
       * 无外部依赖，直接注册单例。</p>
       *
       * @return 协同文档存储器
       */
    @Bean
    @ConditionalOnMissingBean
    public com.chua.starter.filesystem.support.collab.FileCollabStore fileCollabStore() {
        return new com.chua.starter.filesystem.support.collab.FileCollabStore();
    }

      /**
       * 协同文档定时落盘。
       *
       * <p>存储器本身是内存态（编辑零阻塞），这里按
       * {@code plugin.filesystem.collab.persist-seconds}（默认 30s）把<b>有改动</b>的整库
       * 刷到 {@code plugin.filesystem.collab.persist-path}（默认
       * {@code data/file-collab/collab-store.json}，相对进程工作目录）。
       * 启动时先装载；Bean 销毁时补一次；空闲无改动时不写任何字节。</p>
       *
       * <p>没有引入 Spring 的 {@code @Scheduled}：本模块的既有约定是用
       * {@code com.chua.common.support.task.timer.Timer}（上传任务回收、SSE 心跳都用它），
       * 免得为一个落盘任务再开一套调度基建。</p>
       *
       * @param fileCollabStore 协同文档存储器
       * @param properties      文件存储配置
       * @return 定时落盘器（已启动）
       */
    @Bean
    @ConditionalOnMissingBean
    public com.chua.starter.filesystem.support.collab.FileCollabPersistence fileCollabPersistence(
            com.chua.starter.filesystem.support.collab.FileCollabStore fileCollabStore,
            FileStorageProperties properties) {
        FileStorageProperties.CollabPersistConfig cfg = properties.getCollab() == null
                ? new FileStorageProperties.CollabPersistConfig() : properties.getCollab();
        String configured = cfg.getPersistPath();
        java.nio.file.Path file = configured == null || configured.isBlank()
                ? java.nio.file.Paths.get("data", "file-collab", "collab-store.json")
                : java.nio.file.Paths.get(configured);
        com.chua.starter.filesystem.support.collab.FileCollabPersistence persistence =
                new com.chua.starter.filesystem.support.collab.FileCollabPersistence(
                        fileCollabStore, file, cfg.getPersistSeconds(), cfg.isPersistEnable());
        persistence.start();
        return persistence;
    }

      /**
       * 协同文档控制器（{@code /v2/file/collab}）。
       *
       * @param fileCollabStore   协同文档存储器
       * @param uploadTaskManager 上传任务管理器（时间轮，SSE 心跳复用）
       * @return 协同文档控制器
       */
    @Bean
    @ConditionalOnMissingBean
    public FileCollabController fileCollabController(
            com.chua.starter.filesystem.support.collab.FileCollabStore fileCollabStore,
            UploadTaskManager uploadTaskManager) {
        return new FileCollabController(fileCollabStore, uploadTaskManager);
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
    public FileServerManager fileServerManager(FileStorageProperties properties, FileStorageTemplate template
        , ApplicationContext applicationContext) {
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