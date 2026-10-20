package com.chua.starter.filesystem.support.server.embedded;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.filter.CorsFilter;
import com.chua.common.support.network.server.filter.SecurityHeadersServerFilter;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.filter.ServerFilterChain;
import com.chua.common.support.network.server.filter.ServerFilterConfig;
import com.chua.starter.common.support.logger.ModuleLog;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import com.chua.starter.filesystem.support.server.FileStorageServerAssembler;
import com.chua.starter.filesystem.support.server.FileStorageServerFilter;
import com.chua.starter.filesystem.support.template.FileStorageTemplate;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 内置形态：把文件存储的完整协议挂进宿主 Spring 应用（默认形态）。
 *
 * <p>映射形如 {@code {pathPrefix}/*}（默认 {@code /fs/*}），进入过滤器后先去掉前缀，
 * 再交给与独立服务完全相同的 {@link FileStorageServerFilter}：
 * {@code /fs/local/a.txt} → {@code /local/a.txt}。因此两种形态的 URL 规则、
 * 预览/下载/Range/系统配置行为逐字节一致，前端只需把「文件服务器地址」配置成
 * {@code http://host:应用端口/fs}。</p>
 *
 * <p>过滤器链与独立服务保持一致：安全头 → 跨域 → 私有桶鉴权（可选）→ 文件存储。
 * CORS 与安全头直接复用 common-starter 的 {@link CorsFilter} /
 * {@link SecurityHeadersServerFilter}，配置由
 * {@link FileStorageServerAssembler} 从同一份 {@code plugin.filesystem.storage-server.*} 映射。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class FileStorageEmbeddedFilter extends OncePerRequestFilter {

    /**
     * 日志记录器
     */
    private static final ModuleLog MODULE_LOG = ModuleLog.of("FileSystem", FileStorageEmbeddedFilter.class);

    /**
     * 规范化后的路径前缀（空串表示根路径）
     */
    private final String pathPrefix;

    /**
     * 文件存储过滤器（与独立形态同一实现）
     */
    private final FileStorageServerFilter storageFilter;

    /**
     * 前置过滤器链（安全头 / 跨域 / 鉴权）
     */
    private final ServerFilterChain headChain;

    /**
     * 创建内置过滤器。
     *
     * @param properties 文件服务器配置
     * @param template   文件存储模板
     */
    public FileStorageEmbeddedFilter(FileStorageServerProperties properties, FileStorageTemplate template) {
        this.pathPrefix = properties.normalizedPathPrefix();
        this.storageFilter = FileStorageServerAssembler.createFilter(properties, template);

        ServerSetting setting = ServerSetting.defaults();
        FileStorageServerAssembler.applyCors(properties, setting);
        FileStorageServerAssembler.applySecurityHeaders(properties, setting);
        ServerFilterConfig config = configOf(setting);

        List<ServerFilter> head = new ArrayList<>();
        head.add(init(new SecurityHeadersServerFilter(setting.getSecurityHeaders()), config));
        head.add(init(new CorsFilter(), config));
        ServerFilter oauth = FileStorageServerAssembler.createOAuthFilter(properties, storageFilter.getSetting());
        if (oauth != null) {
            FileStorageServerAssembler.linkPrivateBucketSync(storageFilter, oauth);
            head.add(init(oauth, config));
        }

        ServerFilterChain terminal = (request, response) -> {
            // 显式转型：FileStorageServerFilter 同时实现阻塞与响应式两种 doFilter，
            // lambda 直接传入会有重载歧义。
            com.chua.common.support.network.server.filter.ReactiveFilterChain reactive =
                    (req, res) -> java.util.concurrent.CompletableFuture.completedFuture(null);
            CompletionStage<Void> stage = storageFilter.doFilter(request, response, reactive);
            stage.toCompletableFuture().join();
        };
        this.headChain = chainOf(head, terminal);
        MODULE_LOG.info("文件服务器已挂载（内置形态）: {}/* → 存储桶 {} 个",
                pathPrefix.isEmpty() ? "(根路径)" : pathPrefix, template.storageMap.size());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String rawPath = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && rawPath.startsWith(context)) {
            rawPath = rawPath.substring(context.length());
        }
        String resolved = resolvePath(pathPrefix, rawPath);
        if (resolved == null) {
            chain.doFilter(request, response);
            return;
        }
        if (resolved.isEmpty()) {
            resolved = "/";
        }

        ServletServerRequest serverRequest =
                new ServletServerRequest(request, decode(resolved), request.getQueryString());
        ServletServerResponse serverResponse = new ServletServerResponse(response);
        try {
            headChain.doFilter(serverRequest, serverResponse);
            if (!serverResponse.isEnded()) {
                // 前缀之下全部属于文件服务器：未知路径由这里兜底 404，不再落回应用路由
                serverResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");
            }
        } catch (Exception e) {
            MODULE_LOG.warn("文件存储请求处理失败 {} {}: {}", request.getMethod(), resolved, e.getMessage());
            if (!serverResponse.isEnded()) {
                serverResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "文件服务器处理失败: " + e.getMessage());
            }
        } finally {
            if (!serverResponse.isEnded()) {
                serverResponse.end();
            }
        }
    }

    /**
     * 去前缀：判断路径是否落在文件服务器的前缀之下，并返回去掉前缀后的路径。
     *
     * <p>返回 {@code null} 表示不属于文件服务器（应放行给应用自己的路由）；
     * 返回空串表示请求的正是前缀本身（等价于桶根 {@code /}）。
     * 前缀为空时任何路径都归属文件服务器。</p>
     *
     * @param prefix 规范化前缀（空串表示根路径）
     * @param path   已去掉 contextPath 的请求路径
     * @return 去前缀后的路径；不属于该前缀时返回 {@code null}
     */
    static String resolvePath(String prefix, String path) {
        if (path == null) {
            return null;
        }
        if (prefix == null || prefix.isEmpty()) {
            return path;
        }
        if (path.equals(prefix)) {
            return "";
        }
        if (path.startsWith(prefix + "/")) {
            return path.substring(prefix.length());
        }
        return null;
    }

    /**
     * 取出内置过滤器（便于测试与诊断）。
     *
     * @return 文件存储过滤器
     */
    public FileStorageServerFilter getStorageFilter() {
        return storageFilter;
    }

    /**
     * 取出规范化后的路径前缀。
     *
     * @return 前缀（空串表示根路径）
     */
    public String getPathPrefix() {
        return pathPrefix;
    }

    /**
     * 把多个过滤器串成链，末尾接 {@code tail}。
     *
     * @param filters 前置过滤器（按执行顺序）
     * @param tail    终端处理器
     * @return 过滤链
     */
    private static ServerFilterChain chainOf(List<ServerFilter> filters, ServerFilterChain tail) {
        ServerFilterChain next = tail;
        for (int i = filters.size() - 1; i >= 0; i--) {
            ServerFilter filter = filters.get(i);
            ServerFilterChain downstream = next;
            next = (request, response) -> {
                if (filter.isEnabled()) {
                    filter.doFilter(request, response, downstream);
                } else {
                    downstream.doFilter(request, response);
                }
            };
        }
        return next;
    }

    /**
     * 初始化过滤器（失败时返回原实例，由过滤器自行降级）。
     *
     * @param filter 过滤器
     * @param config 配置
     * @return 过滤器
     */
    private static ServerFilter init(ServerFilter filter, ServerFilterConfig config) {
        try {
            filter.init(config);
        } catch (Exception e) {
            MODULE_LOG.warn("过滤器 {} 初始化失败: {}", filter.getClass().getSimpleName(), e.getMessage());
        }
        return filter;
    }

    /**
     * 构造过滤器初始化配置（只暴露服务器 setting）。
     *
     * @param setting 服务器 setting
     * @return 配置
     */
    private static ServerFilterConfig configOf(ServerSetting setting) {
        return new ServerFilterConfig() {
            @Override
            public String getFilterName() {
                return "file-storage-embedded";
            }

            @Override
            public String getInitParameter(String name) {
                return null;
            }

            @Override
            public Map<String, String> getInitParameters() {
                return Map.of();
            }

            @Override
            public ServerSetting getServerSetting() {
                return setting;
            }
        };
    }

    /**
     * 解码请求路径（与 JDK 服务端 {@code URI#getPath} 语义一致）；非法 URI 回退原文。
     *
     * @param raw 原始（含百分号编码）路径
     * @return 解码后的路径
     */
    private static String decode(String raw) {
        try {
            return URI.create(raw).getPath();
        } catch (Exception e) {
            return raw;
        }
    }
}
