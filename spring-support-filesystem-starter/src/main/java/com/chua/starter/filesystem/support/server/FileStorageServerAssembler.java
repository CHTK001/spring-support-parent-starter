package com.chua.starter.filesystem.support.server;

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
 * 文件服务器装配器：内置形态（Servlet 过滤器）与独立形态（独立 HTTP 服务）共用同一套装配逻辑。
 *
 * <p>两种形态如果各写一份「建 setting / 挂桶 / 装 OAuth / 配 CORS」，迟早会漂移
 * （例如只在一边加了新的开关）。本类把这段装配收敛到一处：</p>
 *
 * <ul>
 *   <li>{@link #buildSetting(FileStorageServerProperties)} —— 行为开关；</li>
 *   <li>{@link #createFilter(FileStorageServerProperties, FileStorageTemplate)} —— 挂载全部存储桶；</li>
 *   <li>{@link #createOAuthFilter(FileStorageServerProperties, FileStorageSetting)} —— 私有桶鉴权（可选依赖，反射装配）；</li>
 *   <li>{@link #applyCors(FileStorageServerProperties, ServerSetting)} —— 跨域；</li>
 *   <li>{@link #applySecurityHeaders(FileStorageServerProperties, ServerSetting)} —— iframe 嵌入与安全头；</li>
 *   <li>{@link #linkPrivateBucketSync(FileStorageServerFilter, ServerFilter)} —— 私有桶热更新联动。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class FileStorageServerAssembler {

    /**
     * 日志记录器
     */
    private static final ModuleLog MODULE_LOG = ModuleLog.of("FileSystem", FileStorageServerAssembler.class);

    /**
     * OAuth 鉴权过滤器全限定类名（oauth-client-starter 可选依赖，反射装载：类缺失时按公共桶运行）。
     */
    private static final String OAUTH_FILTER_CLASS = "com.chua.starter.oauth.client.support.filter.OAuthServerFilter";

    /**
     * 工具类禁止实例化
     */
    private FileStorageServerAssembler() {
    }

    /**
     * 构建文件存储行为配置。
     *
     * @param properties 文件服务器配置
     * @return FileStorageSetting
     */
    public static FileStorageSetting buildSetting(FileStorageServerProperties properties) {
        return FileStorageSetting.builder()
                .openPreview(properties.isOpenPreview())
                .openDownload(properties.isOpenDownload())
                .openRange(properties.isOpenRange())
                .openWatermark(properties.isOpenWatermark())
                .openWebjars(properties.isOpenWebjars())
                .openRemoteFile(properties.isOpenRemoteFile())
                .thirdUrlPreview(properties.getThirdUrlPreview())
                .watermark(properties.getWatermark())
                .layout(properties.isLayout())
                .layoutType(properties.getLayoutType())
                .imageFilter(properties.getImageFilter())
                .fileSettingKey(properties.getFileSettingKey())
                .openPathGuard(properties.isOpenPathGuard())
                .imageFilterEnabled(properties.isImageFilterEnabled())
                .filterChain(properties.getFilterChain())
                .build();
    }

    /**
     * 创建文件存储过滤器，并把模板里的全部 provider 注册为存储桶。
     *
     * @param properties 文件服务器配置
     * @param template   文件存储模板（provider 来源）
     * @return 已挂载存储桶的过滤器
     */
    public static FileStorageServerFilter createFilter(FileStorageServerProperties properties,
                                                       FileStorageTemplate template) {
        FileStorageSetting setting = buildSetting(properties);
        seedPrivateBuckets(setting, properties.getPrivateBuckets());
        FileStorageServerFilter filter = new FileStorageServerFilter(setting);
        for (Map.Entry<String, FileStorage> entry : template.storageMap.entrySet()) {
            filter.addFileStorage(entry.getKey(), entry.getValue());
        }
        return filter;
    }

    /**
     * 反射创建 OAuth 鉴权过滤器。
     *
     * <p>oauth-client-starter 为可选依赖：类缺失或装配失败时返回 {@code null}，
     * 文件服务器按公共桶运行，不影响启动。</p>
     *
     * @param properties 文件服务器配置（私有桶来源）
     * @param setting    文件存储配置（私有桶来源）
     * @return 过滤器实例；不可用时返回 {@code null}
     */
    public static ServerFilter createOAuthFilter(FileStorageServerProperties properties, FileStorageSetting setting) {
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
     * 把「系统配置热更新私有桶」联动到 OAuth 鉴权过滤器。
     *
     * @param filter 文件存储过滤器
     * @param oauth  鉴权过滤器（可为 {@code null}）
     */
    public static void linkPrivateBucketSync(FileStorageServerFilter filter, ServerFilter oauth) {
        if (filter == null || oauth == null) {
            return;
        }
        filter.setConfigListener(changed -> syncPrivateBuckets(oauth, changed));
    }

    /**
     * 把跨域配置写入服务器 setting。
     *
     * @param properties 文件服务器配置
     * @param setting    目标服务器 setting
     */
    public static void applyCors(FileStorageServerProperties properties, ServerSetting setting) {
        properties.getCors().applyTo(setting.getCors());
    }

    /**
     * 把 iframe 嵌入与安全头配置写入服务器 setting。
     *
     * <p>预览统一走 iframe：显式开启安全头过滤器，但关闭 X-Frame-Options，
     * 改用 CSP {@code frame-ancestors} 限定可嵌入来源。</p>
     *
     * @param properties 文件服务器配置
     * @param setting    目标服务器 setting
     */
    public static void applySecurityHeaders(FileStorageServerProperties properties, ServerSetting setting) {
        ServerSetting.SecurityHeadersConfig security = setting.getSecurityHeaders();
        security.setEnabled(true);
        security.setFrameOptions("");
        security.setFrameAncestors(properties.getFrameAncestors());
    }

    /**
     * 私有桶初始值写入设置（配置项优先；留空时由 OAuth 过滤器回退环境配置）。
     *
     * @param setting 文件存储配置
     * @param csv     私有桶（逗号分隔）
     */
    private static void seedPrivateBuckets(FileStorageSetting setting, String csv) {
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
    private static void syncPrivateBuckets(ServerFilter oauth, FileStorageSetting setting) {
        if (oauth == null) {
            return;
        }
        Set<String> buckets = setting.getPrivateBuckets();
        String csv = buckets == null || buckets.isEmpty() ? "" : String.join(",", buckets);
        oauth.updateConfig(Map.of("privateBuckets", csv));
    }
}
