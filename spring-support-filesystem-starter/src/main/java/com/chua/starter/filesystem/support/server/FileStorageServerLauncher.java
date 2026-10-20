package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;

/**
 * 文件存储服务器启动器（{@link FileStorageServerProperties.Mode#STANDALONE} 形态）。
 *
 * <p>使用 common-starter 的 {@link ServerBuilder} 启动一个独立 HTTP 服务，
 * 挂载 {@link FileStorageServerFilter}，并把 {@link FileStorageTemplate} 中全部
 * 提供者（provider）注册为存储桶，路径规则 {@code /{provider}/{objectKey}}。</p>
 *
 * <p>装配细节（行为开关 / 存储桶 / OAuth / CORS / 安全头）统一由
 * {@link FileStorageServerAssembler} 提供，与内置形态（{@code FileStorageEmbeddedFilter}）
 * 共用同一份逻辑。端口被占用时仅告警，不影响应用启动。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class FileStorageServerLauncher implements AutoCloseable {

    /**
     * 日志记录器
     */
    private static final ModuleLog MODULE_LOG = ModuleLog.of("FileSystem", FileStorageServerLauncher.class);

    /**
     * 文件服务器配置
     */
    private final FileStorageServerProperties properties;

    /**
     * 文件存储模板（提供者来源）
     */
    private final FileStorageTemplate template;

    /**
     * 已启动的服务器实例
     */
    private volatile Server server;

    /**
     * 创建 文件存储服务器启动器 实例
     *
     * @param properties 文件服务器配置
     * @param template   文件存储模板
     */
    public FileStorageServerLauncher(FileStorageServerProperties properties, FileStorageTemplate template) {
        this.properties = properties;
        this.template = template;
    }

    /**
     * 启动文件服务器（幂等；端口占用时告警并跳过）。
     */
    public void start() {
        if (isRunning()) {
            return;
        }
        try {
            FileStorageServerFilter filter = FileStorageServerAssembler.createFilter(properties, template);
            int buckets = template.storageMap.size();
            Server built = ServerBuilder.create()
                    .type(properties.getType())
                    .host(properties.getHost())
                    .port(properties.getPort())
                    .build();
            // 文件服务器默认只放 10MiB 请求体，与 Spring 上传限制不一致；预览补测需要
            // 原样上传 >10MiB 样本，这里显式放大到与上传配置一致的 64MiB。
            built.getSetting().setMaxRequestSize(64L * 1024 * 1024);
            // 跨域与安全头：与内置形态共用同一套配置映射，避免两种形态语义漂移。
            FileStorageServerAssembler.applyCors(properties, built.getSetting());
            FileStorageServerAssembler.applySecurityHeaders(properties, built.getSetting());
            // OAuth 鉴权过滤器（oauth 模块，反射装配）：私有桶请求校验访问令牌，公共桶放行
            ServerFilter oauthFilter = FileStorageServerAssembler.createOAuthFilter(properties, filter.getSetting());
            if (oauthFilter != null) {
                built.addFilter(oauthFilter);
                FileStorageServerAssembler.linkPrivateBucketSync(filter, oauthFilter);
            }
            built.addFilter(filter);
            built.start();
            this.server = built;
            MODULE_LOG.info("文件服务器已启动（独立形态）: {}:{} 存储桶 {} 个", properties.getHost(), properties.getPort(), buckets);
        } catch (Exception e) {
            MODULE_LOG.warn("文件服务器启动失败（端口 {} 可能被占用）: {}", properties.getPort(), e.getMessage());
        }
    }

    /**
     * 是否正在运行
     *
     * @return true 表示已启动
     */
    public boolean isRunning() {
        Server current = this.server;
        return current != null && current.isRunning();
    }

    /**
     * 服务器访问地址
     *
     * @return http://host:port/
     */
    public String getServerUrl() {
        String host = properties.getHost() == null || properties.getHost().isBlank()
                || "0.0.0.0".equals(properties.getHost()) ? "127.0.0.1" : properties.getHost();
        return "http://" + host + ":" + properties.getPort() + "/";
    }

    @Override
    public void close() {
        Server current = this.server;
        if (current == null) {
            return;
        }
        try {
            current.stop();
            MODULE_LOG.info("文件服务器已停止: {}:{}", properties.getHost(), properties.getPort());
        } catch (Exception e) {
            MODULE_LOG.warn("文件服务器停止失败: {}", e.getMessage());
        } finally {
            this.server = null;
        }
    }
}
