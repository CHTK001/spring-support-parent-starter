package com.chua.starter.monitor.support.config;

import com.chua.common.support.storage.FileStorage;
import com.chua.starter.filesystem.support.controller.FileDirController;
import com.chua.starter.filesystem.support.controller.FileCollabController;
import com.chua.starter.filesystem.support.controller.FileLogController;
import com.chua.starter.filesystem.support.controller.FileServerConfigController;
import com.chua.starter.filesystem.support.controller.UploadTaskController;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import com.chua.starter.filesystem.support.server.FileStorageServerLauncher;
import com.chua.starter.filesystem.support.server.embedded.FileStorageEmbeddedFilter;
import com.chua.starter.filesystem.support.task.UploadTaskManager;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 文件存储网关（embedded 内置形态挂进应用端口，standalone 才另起独立端口）。
 *
 * <p>monitor 排除了 filesystem-starter 的 {@code FileSystemAutoConfiguration}
 * （其 web 分片上传链引用 monitor 未装配的 Bean），连带它里面的两种形态装配点
 * （embedded 的 {@link FileStorageEmbeddedFilter}、standalone 的
 * {@link FileStorageServerLauncher}）也一并消失。本配置按
 * {@code plugin.filesystem.storage-server.mode} 补回其中一种：</p>
 *
 * <ul>
 *   <li><b>embedded</b>（默认）：{@link FileStorageEmbeddedFilter} 挂进宿主应用，
 *       走应用端口 + {@code path-prefix}（默认 {@code /fs}），不额外占端口；</li>
 *   <li><b>standalone</b>：{@link FileStorageServerLauncher} 另起独立 HTTP 服务，
 *       监听 {@code host:port}（默认 18082）。</li>
 * </ul>
 *
 * <p>本配置只取所需：装配 {@link FileStorageTemplate}、两种形态之一，以及
 * 前端取文件服务器地址用的 {@link FileServerConfigController}，
 * 不引入被排除的 web 分片上传链（FileObjectController / FileServerManager）。</p>
 *
 * <p><b>目录浏览控制器 {@link FileDirController} 不在这里注册</b>：它由
 * ai-starter 的 {@code AiFileDirConfiguration} 在 {@code plugin.filesystem.dir-list=true}
 * 时统一注册（monitor 已开启该开关）。monitor 再注册一份会出现两个同名
 * {@code @Bean} 方法 {@code fileDirController}，抛
 * {@code BeanDefinitionOverrideException} 直接启动失败——真机部署实测，
 * 本地单模块测试未加载 ai-starter 上下文，漏掉了该冲突。</p>
 *
 * <p>存储后端复用 {@link MonitorFileStorageConfig} 注册的本地后端 Bean，
 * 以 Bean 名 {@value #BUCKET} 注册为网关存储桶——与前端
 * {@code app.yaml otherConfig.filestorage.bucket} 及
 * {@code FileAssetServiceImpl#LOCAL_BUCKET} 对齐，且与「服务管理」
 * file-storage 节点的按 Bean 名注册桶语义一致（文件资产页与网关共用同一后端）。</p>
 *
 * <p>开关：{@code plugin.filesystem.storage-server.enable}（默认 true，
 * 留空即随应用启动，置 false 关闭网关）；形态
 * {@code plugin.filesystem.storage-server.mode}（默认 embedded，不额外占端口；
 * 置 standalone 时另起独立端口 18082，与前端文件服务器页 DEFAULT_SERVER 对齐）。</p>
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
            // 本地后端无条件作为默认 Provider：上传任务/小文件直传在不显式选
            // provider 时也走本地桶，否则「默认 Provider 不存在」导致上传 409。
            if (template.getDefaultStorageName() == null) {
                template.setDefaultStorage(BUCKET);
            }
        } else {
            log.warn("[FileStorageGateway] 本地后端 {} 不是 FileStorage 实例: {}",
                    MonitorFileStorageConfig.LOCAL_STORAGE_BEAN_NAME,
                    backend == null ? "null" : backend.getClass().getName());
        }
        return template;
    }

    /**
     * 文件服务器运行期配置接口（{@code GET /v2/file/server-config}）。
     *
     * <p>前端进文件管理页前必须先问这个接口「文件服务在哪、桶叫什么」，
     * 拿到结果再决定用嵌入式相对路径还是独立端口；拿不到就只能按
     * localStorage → app.yaml → window.location.origin 逐级回退，于是同一个页面
     * 会散出 {@code /fs/*} 与 {@code /localFileStorage} 两种请求形态——
     * 代理只要少配一个前缀就 502（本地 dev 实测：{@code /localFileStorage} 502，
     * 页面报「列表查询失败（HTTP 502）」）。</p>
     *
     * <p>该控制器原本由被排除的 {@code FileSystemAutoConfiguration} 注册，排除后
     * 接口直接 404（后端日志：{@code 资源不存在（未映射路径）: No static resource
     * v2/file/server-config}）。它只依赖两个 properties、不碰分片上传链，
     * 因此在这里单独补回，而不必解除整个 exclude。</p>
     *
     * <p>必须带 {@link ConditionalOnMissingBean}：与 {@link FileDirController}
     * 同理，将来若恢复 FileSystemAutoConfiguration 会出现两个同名 {@code @Bean}
     * 方法，抛 {@code BeanDefinitionOverrideException} 直接启动失败。</p>
     *
     * @param serverProperties  文件服务器配置（形态 / 端口 / 路径前缀）
     * @param storageProperties 文件存储配置（默认 provider = 默认存储桶）
     * @param contextPath       应用上下文路径（embedded 拼路径用）
     * @return 文件服务器配置控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public FileServerConfigController fileServerConfigController(
            FileStorageServerProperties serverProperties,
            FileStorageProperties storageProperties,
            @Value("${server.servlet.context-path:}") String contextPath) {
        return new FileServerConfigController(serverProperties, storageProperties, contextPath);
    }

    /**
     * 上传任务管理器（时间轮调度 + 虚拟线程分片合并）。
     *
     * <p>与 {@link FileServerConfigController} 同理：{@code FileSystemAutoConfiguration}
     * 整链被排除后，任务化分片上传的 {@code UploadTaskController} 与
     * {@code UploadTaskManager} 一并消失。它们只依赖本配置已装配的
     * {@link FileStorageTemplate}（{@link #fileStorageTemplate(FileStorageProperties, ApplicationContext)}）
     * 与 {@link FileStorageProperties}，不引入被排除的分片上传链的其余部分
     * （FileObjectController / FileServerManager），因此单独补回，而不必解除整个 exclude。</p>
     *
     * <p>必须带 {@link ConditionalOnMissingBean}：若将来恢复
     * {@code FileSystemAutoConfiguration}，两处同名 {@code @Bean} 方法会抛
     * {@code BeanDefinitionOverrideException} 直接启动失败——与
     * {@link FileServerConfigController} 的补注册先例一致。</p>
     *
     * <p>{@code destroyMethod} 关闭时间轮与合并线程池。</p>
     *
     * @param template   文件存储模板（本配置已装配）
     * @param properties 文件存储配置（含 taskExpireMinutes）
     * @return 上传任务管理器
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public UploadTaskManager uploadTaskManager(FileStorageTemplate template,
                                               FileStorageProperties properties) {
        return new UploadTaskManager(template, properties.getTaskExpireMinutes());
    }

    /**
     * 上传任务控制器（{@code /v2/file/upload-task}，任务化分片上传）。
     *
     * <p>补注册说明同 {@link #uploadTaskManager(FileStorageTemplate, FileStorageProperties)}：
     * 只取任务化分片上传两件套，不触碰被排除的其余文件服务链。</p>
     *
     * @param uploadTaskManager 上传任务管理器
     * @param template          文件存储模板
     * @param properties        文件存储配置
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
     * 文件操作日志控制器（{@code /v2/file/log}）。
     *
     * <p>与 {@link UploadTaskController} 同理：{@code FileSystemAutoConfiguration}
     * 整链被排除后，文件日志接口一并消失。本配置单独补回——它只依赖
     * {@link com.chua.starter.filesystem.support.log.FileLogStore} 全局单例，
     * 不触碰被排除的文件服务链。</p>
     *
     * @return 文件日志控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public FileLogController fileLogController() {
        return new FileLogController(com.chua.starter.filesystem.support.log.FileLogStore.getInstance());
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
     * 协同文档控制器（{@code /v2/file/collab}）。
     *
     * <p>与 {@link FileLogController} 同理：{@code FileSystemAutoConfiguration}
     * 整链被排除后，协同接口一并消失。本配置单独补回——它依赖本配置已装配的
     * {@code fileCollabStore} 与 {@code uploadTaskManager}（时间轮驱动 SSE 心跳）。</p>
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
     * 内置形态：把文件存储的完整协议挂进宿主应用（默认，{@code mode} 未配置即此形态）。
     *
     * <p>映射 {@code {path-prefix}/*}（默认 {@code /fs/*}），由
     * {@link FileStorageEmbeddedFilter} 去掉前缀后交给与独立服务完全相同的
     * {@code FileStorageServerFilter}，URL 规则、预览/下载/Range/系统配置行为
     * 与独立形态逐字节一致。不额外占端口，随应用端口对外服务。</p>
     *
     * @param properties 文件服务器配置
     * @param template   文件存储模板
     * @return 过滤器注册
     */
    @Bean
    @ConditionalOnProperty(prefix = FileStorageServerProperties.PRE, name = "enable",
            havingValue = "true", matchIfMissing = true)
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

    /**
     * 独立形态：另起独立 HTTP 服务（{@code plugin.filesystem.storage-server.mode=standalone}）。
     *
     * <p>仅当显式配置 {@code mode=standalone} 时装配；默认 embedded 形态不占独立端口。
     * 端口被占用时仅告警，不影响应用启动。</p>
     *
     * @param properties 文件服务器配置
     * @param template   文件存储模板
     * @return 文件存储服务器启动器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(FileStorageServerLauncher.class)
    @ConditionalOnProperty(prefix = FileStorageServerProperties.PRE, name = "enable",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = FileStorageServerProperties.PRE, name = "mode",
            havingValue = "standalone")
    public FileStorageServerLauncher fileStorageServerLauncher(FileStorageServerProperties properties,
                                                               FileStorageTemplate template) {
        FileStorageServerLauncher launcher = new FileStorageServerLauncher(properties, template);
        launcher.start();
        return launcher;
    }
}
