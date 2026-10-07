package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.storage.FileStorage;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;

import java.util.Map;
import java.util.Set;

/**
 * 文件存储服务器启动器。
 *
 * <p>使用 common-starter 的 {@link ServerBuilder} 启动一个独立 HTTP 服务，
 * 挂载 {@link FileStorageServerFilter}，并把 {@link FileStorageTemplate} 中全部
 * 提供者（provider）注册为存储桶，路径规则 {@code /{provider}/{objectKey}}。
 * 默认随应用启动（{@code plugin.filesystem.storage-server.enable=true}，端口 18080），
 * 供"文件服务器"页面直连；端口被占用时仅告警，不影响应用启动。</p>
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
     * OAuth 鉴权过滤器全限定类名（oauth-client-starter 可选依赖，反射装载：类缺失时按公共桶运行）。
     */
    private static final String OAUTH_FILTER_CLASS = "com.chua.starter.oauth.client.support.filter.OAuthServerFilter";

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
            FileStorageSetting setting = buildSetting();
            seedPrivateBuckets(setting, properties.getPrivateBuckets());
            FileStorageServerFilter filter = new FileStorageServerFilter(setting);
            int buckets = 0;
            for (Map.Entry<String, FileStorage> entry : template.storageMap.entrySet()) {
                filter.addFileStorage(entry.getKey(), entry.getValue());
                buckets++;
            }
            Server built = ServerBuilder.create()
                    .type(properties.getType())
                    .host(properties.getHost())
                    .port(properties.getPort())
                    .build();
            // 跨域（CORS）：文件服务器是独立端口网关，前端基本都是跨源访问，故默认开启。
            // 配置来源 plugin.filesystem.storage-server.cors.*（见 FileStorageServerProperties.Cors），
            // 默认开启且允许携带凭据——预览页以 credentials:'include' 发起跨域 fetch，
            // 缺 Access-Control-Allow-Credentials 会让浏览器拦掉整个响应。
            ServerSetting.CorsConfig cors = built.getSetting().getCors();
            FileStorageServerProperties.Cors corsProperties = properties.getCors();
            cors.setAllowOrigin(corsProperties.isEnable());
            cors.setAllowedOrigins(corsProperties.getAllowedOrigins());
            cors.setAllowedMethods(corsProperties.getAllowedMethods());
            cors.setAllowedHeaders(corsProperties.getAllowedHeaders());
            // 暴露自定义响应头：Content-Disposition（前端读文件名）与预览通道标识
            // （X-FileStorage-Preview / X-FileStorage-Client-Renderer，前端据此选择渲染器）
            cors.setExposeHeaders(corsProperties.getExposeHeaders());
            cors.setAllowCredentials(corsProperties.isAllowCredentials());
            // 预览统一走 iframe：显式开启安全头过滤器，但关闭 X-Frame-Options
            // 并用 CSP frame-ancestors 限定可嵌入来源（SecurityHeadersConfig 默认
            // enabled=false，此处按文件服务器需要显式开启）
            ServerSetting.SecurityHeadersConfig security = built.getSetting().getSecurityHeaders();
            security.setEnabled(true);
            security.setFrameOptions("");
            security.setFrameAncestors(properties.getFrameAncestors());
            // OAuth 鉴权过滤器（oauth 模块，反射装配）：私有桶请求校验访问令牌，公共桶放行
            ServerFilter oauthFilter = createOAuthFilter(setting);
            if (oauthFilter != null) {
                built.addFilter(oauthFilter);
                // 私有桶热更新：管理员 setConfig 后同步给 OAuth 鉴权过滤器
                filter.setConfigListener(changed -> syncPrivateBuckets(oauthFilter, changed));
            }
            built.addFilter(filter);
            built.start();
            this.server = built;
            MODULE_LOG.info("文件服务器已启动: {}:{} 存储桶 {} 个", properties.getHost(), properties.getPort(), buckets);
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

    /**
     * 反射创建 OAuth 鉴权过滤器。
     *
     * <p>oauth-client-starter 为可选依赖：类缺失或装配失败时返回 空，文件服务器按公共桶运行，
     * 不影响启动（与 {@code ServerNodeRegistry} 的插件语义一致）。</p>
     *
     * @param setting 文件存储配置（私有桶来源）
     * @return 过滤器实例；不可用时返回 空
     */
    private ServerFilter createOAuthFilter(FileStorageSetting setting) {
        try {
            Class<?> filterClass = ReflectUtils.forName(OAUTH_FILTER_CLASS);
            if (filterClass == null) {
                MODULE_LOG.info("未检测到 OAuth 鉴权过滤器（oauth-client-starter 未装配），文件服务器按公共桶运行");
                return null;
            }
            ServerFilter oauth = (ServerFilter) ReflectUtils.instantiate(filterClass);
            // init(null)：无初始化参数时过滤器自行回退环境配置 plugin.oauth.file-storage.private-buckets
            oauth.init(null);
            syncPrivateBuckets(oauth, setting);
            MODULE_LOG.info("已挂载 OAuth 鉴权过滤器: order={} 私有桶={}",
                    oauth.getOrder(),
                    setting.getPrivateBuckets() == null || setting.getPrivateBuckets().isEmpty()
                            ? "无" : String.join(",", setting.getPrivateBuckets()));
            return oauth;
        } catch (Exception e) {
            MODULE_LOG.warn("OAuth 鉴权过滤器装配失败（文件服务器按公共桶运行）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 私有桶初始值写入设置（配置项优先；留空时由 OAuth 过滤器回退环境配置）。
     *
     * @param setting 文件存储配置
     * @param csv     私有桶（逗号分隔）
     */
    private void seedPrivateBuckets(FileStorageSetting setting, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        Set<String> buckets = setting.getPrivateBuckets();
        for (String item : csv.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                buckets.add(trimmed);
            }
        }
    }

    /**
     * 同步私有桶到 OAuth 鉴权过滤器（热更新入口）。
     *
     * @param oauth   鉴权过滤器
     * @param setting 最新文件存储配置
     */
    private void syncPrivateBuckets(ServerFilter oauth, FileStorageSetting setting) {
        if (oauth == null) {
            return;
        }
        Set<String> buckets = setting.getPrivateBuckets();
        String csv = buckets == null || buckets.isEmpty() ? "" : String.join(",", buckets);
        oauth.updateConfig(Map.of("privateBuckets", csv));
    }

    /**
     * 构建文件存储行为配置
     *
     * @return FileStorageSetting
     */
    private FileStorageSetting buildSetting() {
        return FileStorageSetting.builder()
                .openPreview(properties.isOpenPreview())
                .openDownload(properties.isOpenDownload())
                .openRange(properties.isOpenRange())
                .openWatermark(properties.isOpenWatermark())
                .openWebjars(properties.isOpenWebjars())
                .openRemoteFile(properties.isOpenRemoteFile())
                .openThirdPreview(properties.isOpenThirdPreview())
                .watermark(properties.getWatermark())
                .layout(properties.isLayout())
                .layoutType(properties.getLayoutType())
                .imageFilter(properties.getImageFilter())
                .build();
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
