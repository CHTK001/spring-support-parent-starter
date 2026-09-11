package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.server.filter.ReactiveFilterChain;
import com.chua.common.support.network.server.filter.ReactiveServerFilter;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.chua.common.support.storage.FileStorage;
import com.chua.common.support.storage.metadata.Metadata;
import com.chua.common.support.storage.request.ExistObjectRequest;
import com.chua.common.support.storage.request.ListObjectRequest;
import com.chua.common.support.storage.request.PutObjectRequest;
import com.chua.common.support.storage.result.DeleteObjectResult;
import com.chua.common.support.storage.result.ExistObjectResult;
import com.chua.common.support.storage.result.GetObjectResult;
import com.chua.common.support.storage.result.ListObjectResult;
import com.chua.common.support.storage.result.ObjectResult;
import com.chua.common.support.storage.result.PutObjectResult;
import com.chua.filestorage.support.preview.FileStoragePreviewProvider;
import com.chua.filestorage.support.preview.PreviewResult;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件存储服务器过滤器。
 *
 * <p>基于 {@link ServerFilter} SPI 的 REST 文件存储网关，将 HTTP 请求映射到
 * {@link FileStorage} 操作。路径规则：{@code /{storageName}/{objectKey}}，
 * 其中 storageName 通过 {@link #addFileStorage(String, FileStorage)} 注册；
 * 未注册的路径将放行给后续过滤器链。</p>
 *
 * <p>支持的 HTTP 方法：</p>
 * <ul>
 *   <li>{@code GET /{name}} — 列出对象（JSON）</li>
 *   <li>{@code GET /{name}/{key}} — 下载/预览对象（支持 Range 分段、远程文件透传）</li>
 *   <li>{@code HEAD /{name}/{key}} — 检查对象是否存在</li>
 *   <li>{@code PUT|POST /{name}/{key}} — 上传对象（请求体为原始文件内容）</li>
 *   <li>{@code DELETE /{name}/{key}} — 删除对象</li>
 * </ul>
 *
 * <p>行为开关由 {@link FileStorageSetting} 控制：
 * openDownload（允许附件下载）、openPreview（允许内联预览）、openRange（支持分段下载）、
 * openWatermark（附加水印响应头）、openRemoteFile（允许以 http(s) 开头的 Key 远程拉取）。</p>
 *
 * @since 2024/12/28
 * @author CH
 */
@Slf4j
public class FileStorageServerFilter implements ServerFilter, ReactiveServerFilter {

    /**
     * 存储映射
     */
    private final Map<String, FileStorage> storageMap = new ConcurrentHashMap<>();

    /**
     * 文件存储设置
     */
    private final FileStorageSetting setting;

    /**
     * SPI 预览提供者缓存（懒加载，classpath 存在 utils-support-filestorage-starter 时可用）
     */
    private volatile List<FileStoragePreviewProvider> previewProviderCache;

    /**
     * 并发去重：key → 正在进行的 SPI 预览 Future，避免同一文件重复转换
     */
    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> inflightMap = new ConcurrentHashMap<>();

    /**
     * 一次性访问链接：token → 条目（阅后即焚）
     */
    private final ConcurrentHashMap<String, OneTimeEntry> oneTimeMap = new ConcurrentHashMap<>();

    /**
     * 一次性链接默认有效期（毫秒）：10 分钟
     */
    private static final long ONE_TIME_TTL_MILLIS = 10 * 60 * 1000;

    /**
     * 一次性链接清理阈值：最近清理后新增多少条触发一次清扫
     */
    private static final int ONE_TIME_CLEAN_THRESHOLD = 128;

    /**
     * 上一次清扫后新增的一次性链接计数
     */
    private volatile int oneTimeSinceClean;

    /**
     * 系统配置访问令牌（每次服务启动随机生成，访问 /config 时校验）
     */
    private final String token;

    /**
     * 创建 FileStorageServerFilter 实例
     * @param setting setting
     */
    public FileStorageServerFilter(FileStorageSetting setting) {
        this.setting = setting == null ? FileStorageSetting.builder().build() : setting;
        this.token = generateToken();
    }

    /**
     * 获取系统配置访问令牌。
     *
     * @return 令牌
     */
    public String getToken() {
        return token;
    }

    /**
     * 生成随机访问令牌（每次服务启动不同）。
     *
     * @return 32 位十六进制令牌
     */
    private static String generateToken() {
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 添加存储实例
     *
     * @param name    存储名称
     * @param storage 存储实例
     */
    public void addFileStorage(String name, FileStorage storage) {
        storageMap.put(name, storage);
    }

    /**
     * 移除存储实例
     *
     * @param name 存储名称
     */
    public void removeFileStorage(String name) {
        storageMap.remove(name);
    }

    @Override
    /** Do过滤 */
    public void doFilter(ServerRequest request, ServerResponse response, com.chua.common.support.network.server.filter.ServerFilterChain chain) throws Exception {
        // 简单透传
        chain.doFilter(request, response);
    }

    @Override
    /** 绑定路径模式：null 表示 Access Filter，每次请求都触发 */
    public String supportPath() {
        return null;
    }

    @Override
    /** 过滤器执行顺序：数值越小越先执行 */
    public int getOrder() {
        return 100;
    }

    @Override
    /** 响应式Do过滤 */
    public CompletionStage<Void> doFilter(ServerRequest request, ServerResponse response, ReactiveFilterChain chain) {
        // 统一 CORS 支持（独立部署时浏览器跨域访问列表/上传/删除必需）
        applyCorsHeaders(request, response);

        // OPTIONS 预检请求直接放行
        if (request.getMethod() == HttpMethod.OPTIONS) {
            response.setStatus(204)
                    .setHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, OPTIONS")
                    .setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
                    .setHeader("Access-Control-Max-Age", "3600")
                    .end();
            return CompletableFuture.completedFuture(null);
        }

        String path = request.getPath();
        List<String> segments = splitPath(path);
        if (segments.isEmpty()) {
            return passthrough(request, response, chain);
        }
        String bucket = segments.get(0);

        // 一次性访问链接：GET /ot/{token}（消费）/ POST /ot?bucket=&key=（创建）
        if ("ot".equalsIgnoreCase(bucket) && storageMap.get(bucket) == null) {
            return handleOneTime(request, response, segments);
        }

        // 系统配置端点：GET /config?token=xxx（未注册存储名时才接管）
        if ("config".equalsIgnoreCase(bucket) && storageMap.get(bucket) == null) {
            if (request.getMethod() != HttpMethod.GET) {
                return error(response, 405, "Method Not Allowed");
            }
            return handleConfig(request, response);
        }

        FileStorage storage = storageMap.get(bucket);
        if (storage == null) {
            // 未注册的存储路径放行，交由后续链路处理
            return passthrough(request, response, chain);
        }

        // 远程文件透传：路径形如 /{bucket}/http:/host/path 或 /{bucket}/https:/host/path
        // （框架返回的路径已解码，远程地址被拆散为多段，此处按前缀重组还原）
        if (setting.isOpenRemoteFile() && segments.size() > 2
                && ("http:".equals(segments.get(1)) || "https:".equals(segments.get(1)))) {
            String remoteUrl = segments.get(1) + "//" + String.join("/", segments.subList(2, segments.size()));
            return handleRemoteFile(request, response, remoteUrl);
        }

        // 提取对象 Key（第二段之后的所有路径段），并做 URL 解码与穿越防护
        String rawKey = segments.size() > 1 ? String.join("/", segments.subList(1, segments.size())) : "";
        String key = sanitizeKey(rawKey);
        if (key == null) {
            return error(response, 400, "Invalid object key");
        }

        HttpMethod method = request.getMethod();
        try {
            if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                if (key.isEmpty()) {
                    if (method == HttpMethod.HEAD) {
                        return error(response, 400, "HEAD requires object key");
                    }
                    return handleList(storage, request, bucket, response);
                }
                return method == HttpMethod.GET
                        ? handleGet(storage, request, bucket, response, key)
                        : handleExist(storage, response, key);
            }
            if (method == HttpMethod.PUT || method == HttpMethod.POST) {
                return handlePut(storage, request, response, key);
            }
            if (method == HttpMethod.DELETE) {
                return handleDelete(storage, response, key);
            }
            return error(response, 405, "Method Not Allowed: " + method);
        } catch (Exception e) {
            log.error("文件存储过滤器处理失败: {} {} - {}", method, path, e.getMessage(), e);
            return error(response, 500, "Internal Error: " + e.getMessage());
        }
    }

    /**
     * 发送错误响应并返回已完成阶段。
     *
     * @param response 响应
     * @param status   HTTP 状态码
     * @param message  错误信息
     * @return 已完成的异步阶段
     */
    private CompletionStage<Void> error(ServerResponse response, int status, String message) {
        response.sendError(status, message);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 处理系统配置查看请求：GET /config?token=xxx。
     *
     * <p>token 校验失败返回 403；成功渲染完整配置页（设置项、注册存储、
     * SPI 预览提供者、运行时环境）。</p>
     *
     * @param request  请求
     * @param response 响应
     * @return 异步阶段
     */
    private CompletionStage<Void> handleConfig(ServerRequest request, ServerResponse response) {
        Map<String, String> params = request.getParams();
        String tokenParam = params == null ? null : params.get("token");
        if (tokenParam == null || tokenParam.isEmpty()) {
            return renderConfigLogin(response);
        }
        if (!token.equals(tokenParam)) {
            return error(response, 403, "Forbidden: invalid token");
        }
        String html = buildConfigPage();
        response.setStatus(200)
                .setContentType("text/html;charset=UTF-8")
                .setBody(html.getBytes(StandardCharsets.UTF_8))
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 渲染配置页登录入口（未携带 token 时）。
     */
    private CompletionStage<Void> renderConfigLogin(ServerResponse response) {
        String html = "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>系统配置</title><style>"
                + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f5f6f8;"
                + "display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;color:#24292f}"
                + ".card{background:#fff;border:1px solid #e1e4e8;border-radius:12px;padding:2rem;width:340px;"
                + "box-shadow:0 4px 24px rgba(0,0,0,.06)}"
                + "h1{font-size:1.2rem;margin:0 0 .25rem}.sub{color:#6e7781;font-size:.85rem;margin-bottom:1.25rem}"
                + "input{width:100%;box-sizing:border-box;padding:.6rem .75rem;border:1px solid #d0d7de;"
                + "border-radius:8px;font-size:.9rem;margin-bottom:.75rem}"
                + "button{width:100%;background:#0969da;color:#fff;border:none;padding:.6rem;border-radius:8px;"
                + "font-size:.9rem;cursor:pointer}"
                + "button:hover{background:#0858c0}.hint{color:#8b949e;font-size:.75rem;margin-top:.75rem}"
                + "</style></head><body>"
                + "<div class=\"card\"><h1>\u2699\uFE0F 系统配置</h1>"
                + "<div class=\"sub\">请输入访问令牌</div>"
                + "<input type=\"password\" id=\"t\" placeholder=\"访问令牌\">"
                + "<button onclick=\"location.href='/config?token='+encodeURIComponent(document.getElementById('t').value)\">"
                + "\u786E\u8BA4</button>"
                + "<div class=\"hint\">令牌由服务启动时随机生成，查看启动日志获取。</div>"
                + "</div></body></html>";
        response.setStatus(200)
                .setContentType("text/html;charset=UTF-8")
                .setBody(html.getBytes(StandardCharsets.UTF_8))
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 构建配置页 HTML：展示全部配置项。
     */
    private String buildConfigPage() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        sb.append("<title>\u2699\uFE0F 系统配置</title><style>");
        sb.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f5f6f8;");
        sb.append("color:#24292f;margin:0;padding:2rem 1.5rem}");
        sb.append("h1{font-size:1.4rem;margin:0 0 .25rem}.sub{color:#6e7781;font-size:.9rem;margin-bottom:1.5rem}");
        sb.append("h2{font-size:1rem;color:#0969da;margin:1.5rem 0 .5rem}");
        sb.append("table{width:100%;max-width:820px;border-collapse:collapse;background:#fff;border:1px solid #e1e4e8;");
        sb.append("border-radius:10px;overflow:hidden}");
        sb.append("th,td{padding:.55rem .9rem;text-align:left;font-size:.85rem;border-bottom:1px solid #f0f2f4}");
        sb.append("th{background:#f6f8fa;font-weight:600;color:#57606a;width:240px}");
        sb.append("tr:last-child td{border-bottom:none}");
        sb.append("code{background:#f0f2f4;padding:.1rem .35rem;border-radius:4px;font-size:.8rem}");
        sb.append("a{color:#0969da;text-decoration:none}");
        sb.append("</style></head><body>");
        sb.append("<h1>\u2699\uFE0F 系统配置</h1>");
        sb.append("<div class=\"sub\">文件存储服务器运行时配置（token 已校验）</div>");

        // 1. 基础设置
        sb.append("<h2>\u57FA\u7840\u8BBE\u7F6E (FileStorageSetting)</h2><table>");
        appendConfigRow(sb, "openPreview", String.valueOf(setting.isOpenPreview()));
        appendConfigRow(sb, "openDownload", String.valueOf(setting.isOpenDownload()));
        appendConfigRow(sb, "openRange", String.valueOf(setting.isOpenRange()));
        appendConfigRow(sb, "openWatermark", String.valueOf(setting.isOpenWatermark()));
        appendConfigRow(sb, "openWebjars", String.valueOf(setting.isOpenWebjars()));
        appendConfigRow(sb, "openRemoteFile", String.valueOf(setting.isOpenRemoteFile()));
        appendConfigRow(sb, "watermark", setting.getWatermark());
        appendConfigRow(sb, "layout", String.valueOf(setting.isLayout()));
        appendConfigRow(sb, "layoutType", setting.getLayoutType());
        appendConfigRow(sb, "访问令牌(token)", "<code>" + escapeHtml(token) + "</code>");
        sb.append("</table>");

        // 2. 存储桶
        sb.append("<h2>\u6CE8\u518C\u5B58\u50A8 (buckets)</h2><table>");
        if (storageMap.isEmpty()) {
            appendConfigRow(sb, "无", "-");
        } else {
            for (Map.Entry<String, FileStorage> e : storageMap.entrySet()) {
                appendConfigRow(sb, e.getKey(),
                        e.getValue() == null ? "null" : e.getValue().getClass().getName());
            }
        }
        sb.append("</table>");

        // 3. SPI 预览提供者
        sb.append("<h2>\u9884\u89C8\u63D0\u4F9B\u8005 (FileStoragePreviewProvider)</h2><table>");
        List<FileStoragePreviewProvider> providers = previewProviders();
        if (providers.isEmpty()) {
            appendConfigRow(sb, "无", "classpath 未包含 utils-support-filestorage-starter");
        } else {
            for (FileStoragePreviewProvider p : providers) {
                appendConfigRow(sb, p.getClass().getSimpleName(), p.getClass().getName());
            }
        }
        sb.append("</table>");

        // 4. 运行时环境
        sb.append("<h2>\u8FD0\u884C\u65F6\u73AF\u5883</h2><table>");
        appendConfigRow(sb, "Java Version", System.getProperty("java.version"));
        appendConfigRow(sb, "Java Vendor", System.getProperty("java.vendor"));
        appendConfigRow(sb, "OS", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        appendConfigRow(sb, "user.dir", System.getProperty("user.dir"));
        appendConfigRow(sb, "maxMemory", formatSize(Runtime.getRuntime().maxMemory()));
        sb.append("</table>");

        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendConfigRow(StringBuilder sb, String key, String value) {
        sb.append("<tr><th>").append(escapeHtml(key)).append("</th><td>");
        if (value == null) {
            sb.append("-");
        } else {
            sb.append(escapeHtml(value));
        }
        sb.append("</td></tr>");
    }

    // ==================== 一次性访问链接（阅后即焚） ====================

    /**
     * 处理一次性访问链接。
     *
     * <ul>
     *   <li>{@code POST /ot?bucket=xxx&key=yyy&expires=600} — 创建一次性链接（expires 为秒数，可选），
     *       返回 JSON {@code {"url":"/ot/{token}"}}</li>
     *   <li>{@code GET /ot/{token}} — 消费一次性链接，返回对象内容并立即销毁（仅可访问一次）</li>
     * </ul>
     *
     * @param request  请求
     * @param response 响应
     * @param segments 路径段
     * @return 异步阶段
     */
    private CompletionStage<Void> handleOneTime(ServerRequest request, ServerResponse response, List<String> segments) {
        HttpMethod method = request.getMethod();
        try {
            if (method == HttpMethod.POST) {
                // 创建一次性链接
                Map<String, String> params = request.getParams();
                String bucket = params == null ? null : params.get("bucket");
                String key = params == null ? null : params.get("key");
                if (bucket == null || bucket.isEmpty() || key == null || key.isEmpty()) {
                    return error(response, 400, "Missing bucket or key");
                }
                FileStorage storage = storageMap.get(bucket);
                if (storage == null) {
                    return error(response, 404, "Storage not found: " + bucket);
                }
                // 校验对象存在
                ExistObjectRequest existReq = ExistObjectRequest.builder()
                        .fileName(key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key)
                        .filePath(key.contains("/") ? key.substring(0, key.lastIndexOf('/')) : "")
                        .build();
                ExistObjectResult exist = storage.existObject(existReq);
                if (!exist.isExists()) {
                    return error(response, 404, "Object not found: " + key);
                }

                long ttl = parseOneTimeExpiry(params == null ? null : params.get("expires"));
                String token = generateOneTimeToken();
                oneTimeMap.put(token, new OneTimeEntry(bucket, key, System.currentTimeMillis(), ttl));
                maybeCleanOneTime();

                String json = "{\"token\":\"" + token + "\",\"url\":\"/ot/" + token + "\",\"expires\":\""
                        + ttl + "ms\"}";
                response.setStatus(200)
                        .setContentType("application/json;charset=UTF-8")
                        .setBody(json)
                        .end();
                return CompletableFuture.completedFuture(null);
            }
            if (method == HttpMethod.GET && segments.size() == 2) {
                // 消费一次性链接（原子移除，阅后即焚）
                String token = segments.get(1);
                OneTimeEntry entry = oneTimeMap.remove(token);
                if (entry == null) {
                    return error(response, 410, "Link expired or already consumed");
                }
                if (System.currentTimeMillis() - entry.createTime > entry.ttlMillis) {
                    return error(response, 410, "Link expired");
                }
                FileStorage storage = storageMap.get(entry.bucket);
                if (storage == null) {
                    return error(response, 404, "Storage not found: " + entry.bucket);
                }
                GetObjectResult get = storage.getObject(entry.key);
                if (get == null || get.getResultCode() != ObjectResult.ResultCode.SUCCESS || get.getInputStream() == null) {
                    return error(response, 404, "Object not found: " + entry.key);
                }
                byte[] data;
                try (InputStream in = get.getInputStream()) {
                    data = in.readAllBytes();
                } catch (Exception e) {
                    return error(response, 500, "Read failed");
                }
                String fileName = entry.key.contains("/")
                        ? entry.key.substring(entry.key.lastIndexOf('/') + 1) : entry.key;
                response.setStatus(200)
                        .setHeader("X-OneTime", "true")
                        .setHeader("Cache-Control", "no-store, no-cache, must-revalidate")
                        .setContentType(resolveContentType(fileName))
                        .setBody(data)
                        .end();
                return CompletableFuture.completedFuture(null);
            }
            return error(response, 405, "Method Not Allowed");
        } catch (Exception e) {
            log.error("一次性链接处理失败: {}", e.getMessage(), e);
            return error(response, 500, "Internal Error: " + e.getMessage());
        }
    }

    /**
     * 生成一次性访问令牌。
     *
     * @return 16 字节十六进制令牌
     */
    private static String generateOneTimeToken() {
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 解析一次性链接过期时长。
     *
     * <p>支持秒数（expires=60）或带单位的时长（30s/5m/2h/1d）；
     * 未传或非法值时使用默认 {@link #ONE_TIME_TTL_MILLIS}。</p>
     *
     * @param expires 原始参数
     * @return 过期毫秒数
     */
    private long parseOneTimeExpiry(String expires) {
        if (expires == null || expires.isEmpty()) {
            return ONE_TIME_TTL_MILLIS;
        }
        String v = expires.trim().toLowerCase();
        try {
            if (v.endsWith("s")) {
                return Math.max(1000, Long.parseLong(v.substring(0, v.length() - 1)) * 1000);
            }
            if (v.endsWith("m")) {
                return Math.max(1000, Long.parseLong(v.substring(0, v.length() - 1)) * 60_000);
            }
            if (v.endsWith("h")) {
                return Math.max(1000, Long.parseLong(v.substring(0, v.length() - 1)) * 3_600_000);
            }
            if (v.endsWith("d")) {
                return Math.max(1000, Long.parseLong(v.substring(0, v.length() - 1)) * 86_400_000);
            }
            return Math.max(1000, Long.parseLong(v) * 1000);
        } catch (NumberFormatException e) {
            return ONE_TIME_TTL_MILLIS;
        }
    }

    /**
     * 惰性清扫过期的一次性链接（避免每次创建都全量遍历）。
     */
    private void maybeCleanOneTime() {
        if (++oneTimeSinceClean < ONE_TIME_CLEAN_THRESHOLD) {
            return;
        }
        oneTimeSinceClean = 0;
        long now = System.currentTimeMillis();
        oneTimeMap.entrySet().removeIf(e ->
                now - e.getValue().createTime > ONE_TIME_TTL_MILLIS);
    }

    /**
     * 一次性访问条目。
     */
    private static final class OneTimeEntry {
        /** 存储桶 */
        final String bucket;
        /** 对象 Key */
        final String key;
        /** 创建时间戳 */
        final long createTime;
        /** 自定义过期时长（毫秒） */
        final long ttlMillis;

        OneTimeEntry(String bucket, String key, long createTime) {
            this(bucket, key, createTime, ONE_TIME_TTL_MILLIS);
        }

        OneTimeEntry(String bucket, String key, long createTime, long ttlMillis) {
            this.bucket = bucket;
            this.key = key;
            this.createTime = createTime;
            this.ttlMillis = ttlMillis;
        }
    }

    /**
     * 为响应追加 CORS 头（回显 Origin，支持任意来源跨域）。
     *
     * <p>无 Origin 头的请求（同源/直连工具）不做任何处理。</p>
     *
     * @param request  请求
     * @param response 响应
     */
    private void applyCorsHeaders(ServerRequest request, ServerResponse response) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isEmpty()) {
            return;
        }
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Vary", "Origin");
    }

    /**
     * 放行请求至后续过滤器链。
     *
     * @param request  请求
     * @param response 响应
     * @param chain    过滤器链
     * @return 异步阶段
     */
    private CompletionStage<Void> passthrough(ServerRequest request, ServerResponse response, ReactiveFilterChain chain) {
        try {
            return chain.doFilter(request, response);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 处理对象列表请求（存储桶根目录）。
     *
     * <p>layout 开启时返回 HTML 文件列表页面；关闭时目录访问直接返回 404。</p>
     *
     * @param storage  存储实例
     * @param bucket   存储名
     * @param response 响应
     * @return 异步阶段
     */
    private CompletionStage<Void> handleList(FileStorage storage, ServerRequest request, String bucket, ServerResponse response) {
        if (!setting.isLayout()) {
            return error(response, 404, "Not Found");
        }
        applyWatermarkHeader(response);
        ListObjectResult result = storage.listObject(ListObjectRequest.builder()
                .limit(Integer.MAX_VALUE)
                .build());
        if (result.getResultCode() != ObjectResult.ResultCode.SUCCESS) {
            return error(response, 500, "List Objects Failed");
        }
        return writeList(bucket, "", result, request, response);
    }

/**
     * 写出对象列表响应。
     *
     * <p>layout 开启时：</p>
     * <ul>
     *   <li>{@code ?format=json}、{@code Accept: application/json} 或
     *       {@code X-Requested-With: XMLHttpRequest} → JSON（供 Vue API）</li>
     *   <li>SYSTEM 布局：浏览器直接访问 → 重定向到 {@code /fs/?bucket=X&dir=Y}，由 Vue 独立 UI 处理</li>
     *   <li>SIMPLE 布局：服务端渲染的简单列表</li>
     * </ul>
     *
     * @param bucket 存储名
     * @param dir    目录路径
     * @param result 列表结果
     * @param request 请求
     * @param response 响应
     * @return 异步阶段
     */
    private CompletionStage<Void> writeList(String bucket, String dir, ListObjectResult result, ServerRequest request, ServerResponse response) {
        if (!setting.isLayout()) {
            return error(response, 404, "Not Found");
        }
        // 非 HTML 请求 → JSON：format=json(目录列表) 或 format=config(bucket 配置)
        Map<String, String> params = request.getParams();
        if (params != null && "config".equalsIgnoreCase(params.get("format"))) {
            return writeBucketConfig(bucket, request, response);
        }
        if (wantsJson(request)) {
            return writeJsonList(bucket, dir, result, response);
        }
        return renderLayoutPage(bucket, dir, result.getMetadata(), response);
    }

    /**
     * 返回 bucket 公开配置 JSON（供 Vue 前端渲染用，无需 token）。
     *
     * <p>包含存储类型、功能开关、布局类型等，不含敏感字段（如完整 token）。
     * 用法：{@code GET /{bucket}?format=config}</p>
     */
    private CompletionStage<Void> writeBucketConfig(String bucket, ServerRequest request, ServerResponse response) {
        FileStorage storage = storageMap.get(bucket);
        if (storage == null) {
            return error(response, 404, "Storage not found: " + bucket);
        }
        String storageType = storage.getClass().getSimpleName();
        String wm = setting.getWatermark();

        String json = "{\"bucket\":\"" + escapeJson(bucket) + "\""
                + ",\"storage\":\"" + escapeJson(storageType) + "\""
                + ",\"openPreview\":" + setting.isOpenPreview()
                + ",\"openDownload\":" + setting.isOpenDownload()
                + ",\"openRange\":" + setting.isOpenRange()
                + ",\"openWatermark\":" + setting.isOpenWatermark()
                + ",\"openWebjars\":" + setting.isOpenWebjars()
                + ",\"openRemoteFile\":" + setting.isOpenRemoteFile()
                + ",\"watermark\":" + (wm == null ? "null" : "\"" + escapeJson(wm) + "\"")
                + ",\"layout\":" + setting.isLayout()
                + ",\"layoutType\":\"" + escapeJson(setting.getLayoutType() == null ? "" : setting.getLayoutType()) + "\""
                + ",\"oneTimeTtlMs\":" + ONE_TIME_TTL_MILLIS
                + "}";
        response.setStatus(200)
                .setContentType("application/json;charset=UTF-8")
                .setBody(json)
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 判断请求是否期望 JSON 响应（Vue API 调用 vs 浏览器导航）。
     *
     * @param request 请求
     * @return true 表示返回 JSON
     */
    private boolean wantsJson(ServerRequest request) {
        Map<String, String> params = request.getParams();
        if (params != null) {
            String format = params.get("format");
            if ("json".equalsIgnoreCase(format) || "config".equalsIgnoreCase(format)) {
                return true;
            }
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return true;
        }
        String xrw = request.getHeader("X-Requested-With");
        return xrw != null && xrw.contains("XMLHttpRequest");
    }

    /**
     * 返回文件级公开配置 JSON（`?config=true`，与 preview/download 平级）。
     *
     * <p>包含文件名、大小、内容类型、预览类型、功能开关等，不读文件体。
     * 用法：{@code GET /{bucket}/{key}?config=true}</p>
     */
    private CompletionStage<Void> writeObjectConfig(String bucket, String key, GetObjectResult result, ServerResponse response) {
        String fileName = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
        String filePath = key.contains("/") ? key.substring(0, key.lastIndexOf('/')) : "";
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            ext = fileName.substring(dot + 1).toLowerCase();
        }
        String kind = resolvePreviewKind(ext);
        long size = -1;
        long lastModified = -1;
        Metadata meta = result.getMetadata();
        if (meta != null) {
            size = meta.getSize();
            lastModified = meta.getLastModified();
        }
        if (size < 0 && result.getInputStream() != null) {
            try {
                size = result.getInputStream().available();
            } catch (Exception ignored) {
            }
        }

        String json = "{\"bucket\":\"" + escapeJson(bucket) + "\""
                + ",\"key\":\"" + escapeJson(key) + "\""
                + ",\"fileName\":\"" + escapeJson(fileName) + "\""
                + ",\"filePath\":\"" + escapeJson(filePath) + "\""
                + ",\"extension\":\"" + escapeJson(ext) + "\""
                + ",\"contentType\":\"" + escapeJson(resolveContentType(fileName)) + "\""
                + ",\"kind\":\"" + kind + "\""
                + ",\"size\":" + size
                + ",\"lastModified\":" + lastModified
                + ",\"openPreview\":" + setting.isOpenPreview()
                + ",\"openDownload\":" + setting.isOpenDownload()
                + ",\"openRange\":" + setting.isOpenRange()
                + "}";
        response.setStatus(200)
                .setContentType("application/json;charset=UTF-8")
                .setBody(json)
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 根据扩展名推断预览类型（与前端 kindOf 保持一致）。
     */
    private static String resolvePreviewKind(String ext) {
        String e = ext == null ? "" : ext.toLowerCase();
        if (e.matches("png|jpg|jpeg|gif|svg|webp|bmp|ico")) return "image";
        if (e.matches("txt|log|json|xml|html|css|js|ts|md|yaml|yml|csv")) return "text";
        if ("pdf".equals(e)) return "pdf";
        if (e.matches("xlsx|xls|docx|doc|pptx|ppt|odt|ods|odp|vsd|dwg|dgn|step|stp|iges|igs|brep|xlsb|xlt|xltx|xltm|xlam|xlsxml|dotx|dotm|potx|potm|xmind|rtf|epub")) return "office";
        if (e.matches("mp4|webm|ogg|mov|avi|mkv|flv|wmv|m4v|3gp")) return "video";
        if (e.matches("mp3|wav|flac|m4a")) return "audio";
        if (e.matches("ttf|otf|woff|woff2|eot")) return "font";
        if (e.matches("epub|rtf|eml|ics|vcf|fbx|blend|shp")) return "text";
        return "unknown";
    }

    /**
     * 输出 JSON 格式的目录列表（供 Vue API 使用，不渲染 HTML 页面）。
     */
    private CompletionStage<Void> writeJsonList(String bucket, String dir, ListObjectResult result, ServerResponse response) {
        StringBuilder json = new StringBuilder();
        json.append("{\"bucket\":\"").append(escapeJson(bucket)).append("\"")
                .append(",\"path\":\"").append(escapeJson(dir == null ? "" : dir)).append("\"")
                .append(",\"count\":");
        List<Metadata> items = result.getMetadata();
        json.append(items == null ? 0 : items.size());
        if (items != null && !items.isEmpty()) {
            json.append(",\"items\":[");
            for (int i = 0; i < items.size(); i++) {
                Metadata metadata = items.get(i);
                if (i > 0) json.append(",");
                json.append("{\"name\":\"").append(escapeJson(metadata.getName())).append("\"")
                        .append(",\"size\":").append(metadata.getSize())
                        .append(",\"directory\":").append(metadata.isDirectory())
                        .append(",\"lastModified\":").append(metadata.getLastModified())
                        .append("}");
            }
            json.append("]");
        }
        json.append("}");
        response.setStatus(200)
                .setContentType("application/json;charset=UTF-8")
                .setBody(json.toString())
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 渲染文件列表页面。
     *
     * <p>统一返回 Vue 独立构建壳页面（classpath {@code static/index.html}），
     * SYSTEM/SIMPLE 布局均由前端渲染，通过 REST API（{@code ?format=json/config}）完成交互。
     * {@link FileStorageSetting#getLayoutType()} 保留作为前端展示的布局偏好，
     * 由 Vue 在渲染时决定网格/列表等呈现方式。</p>
     *
     * @param bucket 存储名
     * @param dir    当前目录路径
     * @param items  列表项
     * @param response 响应
     * @return 异步阶段
     */
    private CompletionStage<Void> renderLayoutPage(String bucket, String dir, List<Metadata> items, ServerResponse response) {
        String html = readVueShell();
        if (html == null) {
            return error(response, 500, "Vue 前端资源缺失（static/index.html）");
        }
        response.setStatus(200)
                .setContentType("text/html;charset=UTF-8")
                .setBody(html.getBytes(StandardCharsets.UTF_8))
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 读取 Vue 前端壳页面（classpath static/index.html），带懒缓存。
     *
     * @return HTML 内容，缺失返回 null
     */
    private volatile String vueShellCache;

    private String readVueShell() {
        String cached = vueShellCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (vueShellCache == null) {
                try (InputStream in = FileStorageServerFilter.class.getResourceAsStream("/static/index.html")) {
                    if (in == null) {
                        return null;
                    }
                    vueShellCache = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return null;
                }
            }
            return vueShellCache;
        }
    }

    /**
     * 解析配置的布局类型，非法值回退为 SIMPLE。
     */
    private FileStorageSetting.LayoutType resolveLayoutType() {
        try {
            return FileStorageSetting.LayoutType.valueOf(
                    setting.getLayoutType() == null ? "SIMPLE" : setting.getLayoutType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FileStorageSetting.LayoutType.SIMPLE;
        }
    }

    // ==================== 布局渲染 ====================

    private String buildSimpleLayoutHtml(String bucket, String dir, List<Metadata> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        sb.append("<title>").append(escapeAttr(bucket)).append("</title>");
        sb.append("<style>");
        sb.append("html,body{margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;");
        sb.append("background:#f5f6f8;color:#24292f;line-height:1.5}");
        sb.append(".topbar{background:#fff;border-bottom:1px solid #e1e4e8;padding:.75rem 1rem;");
        sb.append("display:flex;align-items:center;gap:.75rem;position:sticky;top:0;z-index:10;");
        sb.append("box-shadow:0 1px 2px rgba(0,0,0,.04)}");
        sb.append(".topbar .logo{font-size:1.1rem;font-weight:600;color:#0969da}");
        sb.append(".topbar .crumb{flex:1;color:#57606a;font-size:.9rem;overflow:hidden;");
        sb.append("text-overflow:ellipsis;white-space:nowrap}");
        sb.append(".topbar .crumb a{color:#0969da;text-decoration:none}");
        sb.append(".panel{background:#fff;border:1px solid #e1e4e8;border-radius:8px;");
        sb.append("margin:.75rem 1rem;overflow:hidden}");
        sb.append(".entry{display:flex;align-items:center;gap:.6rem;padding:.55rem .9rem;");
        sb.append("border-top:1px solid #f0f2f4;text-decoration:none;color:#24292f}");
        sb.append(".entry:first-child{border-top:none}");
        sb.append(".entry:hover{background:#f6f8fa}");
        sb.append(".entry .icon{width:22px;text-align:center;font-size:1.05rem;flex-shrink:0}");
        sb.append(".entry .name{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}");
        sb.append(".entry.dir .name{font-weight:500;color:#0969da}");
        sb.append(".entry .meta{color:#6e7781;font-size:.8rem;white-space:nowrap}");
        sb.append(".up .name{color:#57606a;font-style:italic}");
        sb.append(".empty{color:#8b949e;text-align:center;padding:2rem;font-size:.9rem}");
        sb.append("</style></head><body>");
        renderBreadcrumb(sb, bucket, dir);
        renderEntriesSimple(sb, bucket, dir, items);
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * SYSTEM 布局：类 macOS Finder 的文件管理器。
     *
     * <p>顶部半透明毛玻璃工具栏（交通灯 + 标题 + 列表/大图视图切换 + 搜索 + 配置入口）、
     * 左侧边栏（收藏 + 存储桶）、路径栏面包屑、列表/大图双视图、
     * 行右键菜单（预览/下载/一次性链接/复制/删除）与预览对话框。</p>
     *
     * <p>使用 text block 模板 + 占位符替换渲染，避免字符串拼接。</p>
     */
    private String buildSystemLayoutHtml(String bucket, String dir, List<Metadata> items) {
        String pathbar = buildFinderPathbar(bucket, dir);
        String sidebar = buildFinderSidebar(bucket);
        String rows = buildFinderRows(bucket, dir, items);
        String script = buildFinderScript(bucket);
        return SYSTEM_LAYOUT_TEMPLATE
                .replace("{{TITLE}}", escapeAttr(bucket))
                .replace("{{BUCKET}}", escapeAttr(bucket))
                .replace("{{BUCKET_HREF}}", encodePathSeg(bucket))
                .replace("{{PATHBAR}}", pathbar)
                .replace("{{SIDEBAR}}", sidebar)
                .replace("{{ROWS}}", rows)
                .replace("{{SCRIPT}}", script);
    }

    /**
     * Finder 布局模板（text block）。
     */
    private static final String SYSTEM_LAYOUT_TEMPLATE = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{{TITLE}}</title>
<style>
:root{--accent:#0a84ff;--text:#1d1d1f;--muted:#86868b;--line:rgba(0,0,0,.08)}
*{margin:0;padding:0;box-sizing:border-box}
html,body{height:100%}
body{font-family:-apple-system,BlinkMacSystemFont,'SF Pro Text','Segoe UI',Roboto,sans-serif;background:#f5f5f7;color:var(--text);display:flex;flex-direction:column;overflow:hidden}
.toolbar{flex-shrink:0;display:flex;align-items:center;gap:12px;padding:10px 16px;background:rgba(250,250,252,.72);backdrop-filter:saturate(180%) blur(20px);-webkit-backdrop-filter:saturate(180%) blur(20px);border-bottom:1px solid var(--line)}
.tf-lights{display:flex;gap:8px}
.tf-lights i{width:12px;height:12px;border-radius:50%;display:block}
.tf-lights i:nth-child(1){background:#ff5f57}.tf-lights i:nth-child(2){background:#febc2e}
.tf-lights i:nth-child(3){background:#28c840}
.tf-title{font-size:13px;font-weight:600;white-space:nowrap}
.tf-btn{display:flex;gap:4px;align-items:center;border:none;background:transparent;padding:5px;border-radius:6px;cursor:pointer;color:var(--muted)}
.tf-btn:hover{background:rgba(0,0,0,.07)}
.tf-btn.active{color:var(--accent);background:rgba(10,132,255,.12)}
.tf-btn svg{width:18px;height:18px;stroke:currentColor;fill:none;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
.tf-search{margin-left:auto;display:flex;align-items:center;gap:8px}
.tf-search input{border:none;outline:none;background:rgba(120,120,128,.12);border-radius:8px;padding:5px 12px;font-size:12px;width:160px;font-family:inherit}
.tf-search input:focus{background:#fff;box-shadow:0 0 0 3px rgba(10,132,255,.25)}
.tf-search a{color:var(--muted);text-decoration:none;font-size:16px}
.tf-search a:hover{color:var(--accent)}
.body{flex:1;display:flex;overflow:hidden}
.sidebar{width:180px;flex-shrink:0;padding:12px 8px;overflow-y:auto;background:rgba(255,255,255,.45);border-right:1px solid var(--line)}
.side-group{font-size:11px;color:var(--muted);padding:10px 10px 4px;letter-spacing:.02em}
.side-item{display:flex;align-items:center;gap:8px;padding:5px 10px;border-radius:6px;font-size:13px;cursor:pointer;color:var(--text);text-decoration:none;margin-bottom:1px}
.side-item:hover{background:rgba(0,0,0,.05)}
.side-item.active{background:rgba(10,132,255,.14);color:#0040dd;font-weight:500}
.side-item svg{flex-shrink:0}
.content{flex:1;display:flex;flex-direction:column;overflow:hidden;background:#fff}
.pathbar{flex-shrink:0;display:flex;align-items:center;gap:4px;padding:8px 16px;background:rgba(250,250,252,.6);border-bottom:1px solid var(--line);font-size:12px}
.pathbar a{color:var(--accent);text-decoration:none;padding:2px 6px;border-radius:5px}
.pathbar a:hover{background:rgba(10,132,255,.1)}
.pathbar .sep{color:#c7c7cc}
.list{flex:1;overflow-y:auto;padding:6px 10px 18px}
.lrow{display:flex;align-items:center;gap:10px;padding:7px 10px;border-radius:8px;cursor:pointer;text-decoration:none;color:var(--text);font-size:13px;min-height:38px}
.lrow:hover{background:rgba(0,0,0,.045)}
.lrow.selected{background:rgba(10,132,255,.16)}
.lrow .l-ico{width:26px;text-align:center;flex-shrink:0;font-size:17px}
.lrow .l-name{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.lrow .l-size{width:90px;text-align:right;color:var(--muted);font-size:12px;flex-shrink:0}
.lrow .l-time{width:150px;color:var(--muted);font-size:12px;flex-shrink:0}
.lrow.up{color:var(--muted);font-size:12.5px}
.lrow.up .l-ico{opacity:.7}
.empty{color:var(--muted);text-align:center;padding:3rem 0;font-size:13px}
.list.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:10px;align-content:start;padding:14px}
.grid .lrow{flex-direction:column;gap:6px;min-height:0;padding:12px 8px;text-align:center;justify-content:flex-start;border-radius:10px}
.grid .lrow:hover{background:rgba(0,0,0,.05)}
.grid .lrow.selected{background:rgba(10,132,255,.18);outline:1.5px solid var(--accent)}
.grid .lrow .l-ico{width:auto;font-size:40px;line-height:1}
.grid .lrow .l-name{flex:none;width:100%;font-size:12px;line-height:1.3;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.grid .lrow .l-size,.grid .lrow .l-time{width:auto;text-align:center;font-size:11px}
.grid .lrow .l-time{display:none}
.ctx{position:fixed;z-index:999;min-width:160px;background:rgba(250,250,252,.92);backdrop-filter:saturate(180%) blur(20px);-webkit-backdrop-filter:saturate(180%) blur(20px);border:1px solid var(--line);border-radius:10px;padding:5px;box-shadow:0 8px 30px rgba(0,0,0,.18);display:none;font-size:13px}
.ctx.show{display:block}
.ctx-item{display:flex;align-items:center;gap:8px;padding:6px 10px;border-radius:6px;cursor:pointer}
.ctx-item:hover{background:rgba(10,132,255,.12)}
.ctx-item.danger{color:#e02424}.ctx-item.danger:hover{background:rgba(224,36,36,.1)}
.ctx-sep{height:1px;background:var(--line);margin:4px 6px}
.pv-mask{position:fixed;inset:0;background:rgba(0,0,0,.5);z-index:1000;display:none;align-items:center;justify-content:center;backdrop-filter:blur(4px)}
.pv-mask.show{display:flex}
.pv-dialog{background:#fff;border-radius:12px;max-width:88vw;max-height:88vh;width:900px;display:flex;flex-direction:column;overflow:hidden;box-shadow:0 20px 60px rgba(0,0,0,.3)}
.pv-head{flex-shrink:0;display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-bottom:1px solid var(--line);font-size:14px}
.pv-head b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding-right:12px}
.pv-head button{border:none;background:transparent;font-size:15px;cursor:pointer;color:#86868b;padding:4px 8px;border-radius:6px}
.pv-head button:hover{background:rgba(0,0,0,.06)}
.pv-body{flex:1;overflow:auto;background:#f5f5f7}
.pv-body iframe{width:100%;height:72vh;border:none;background:#fff}
.pv-body img{max-width:100%;display:block;margin:0 auto}
.pv-body video{max-width:100%}.pv-body audio{width:100%;padding:20px}
.pv-body pre{padding:16px;font-family:Consolas,monospace;font-size:13px;white-space:pre-wrap;word-break:break-all;color:#24292f;background:#fff;margin:0}
.ot-row{display:flex;gap:8px;padding:8px 4px}
.ot-row input{flex:1;padding:.55rem .75rem;border:1px solid #d0d7de;border-radius:8px;font-size:13px;background:#fff;color:#24292f}
.ot-row button{background:#0a84ff;color:#fff;border:none;border-radius:8px;padding:0 16px;cursor:pointer;font-size:13px}
::-webkit-scrollbar{width:8px;height:8px}::-webkit-scrollbar-thumb{background:#c8c8cd;border-radius:4px}
</style>
</head>
<body>
<div class="toolbar">
  <div class="tf-lights"><i></i><i></i><i></i></div>
  <div class="tf-title">{{BUCKET}}</div>
  <button class="tf-btn active" id="viewList" title="标准列表"><svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="4" rx="1"/><rect x="3" y="10" width="18" height="4" rx="1"/><rect x="3" y="16" width="18" height="4" rx="1"/></svg></button>
  <button class="tf-btn" id="viewGrid" title="大图模式"><svg viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg></button>
  <div class="tf-search">
    <input type="search" id="search" placeholder="搜索当前目录">
    <a href="/config" title="系统配置">⚙️</a>
  </div>
</div>
<div class="body">
  <div class="sidebar">{{SIDEBAR}}</div>
  <div class="content" data-bucket="{{BUCKET}}">
    <div class="pathbar">{{PATHBAR}}</div>
    <div class="list" id="list">{{ROWS}}</div>
  </div>
</div>
<div class="ctx" id="ctxMenu"></div>
<div class="pv-mask" id="pvMask" onclick="if(event.target===this)closePv()">
  <div class="pv-dialog">
    <div class="pv-head"><b id="pvTitle"></b><button onclick="closePv()">✕</button></div>
    <div class="pv-body" id="pvBody"></div>
  </div>
</div>
<div class="pv-mask" id="otMask" onclick="if(event.target===this)closeOt()">
  <div class="pv-dialog" style="max-width:520px">
    <div class="pv-head"><b>一次性链接</b><button onclick="closeOt()">✕</button></div>
    <div class="pv-body">
      <p style="color:#86868b;font-size:13px;padding:0 4px">链接只能访问一次，访问后即销毁，10 分钟内有效。</p>
      <div class="ot-row"><input id="otUrl" readonly><button onclick="copyOt()">复制</button></div>
    </div>
  </div>
</div>
<script>{{SCRIPT}}</script>
</body>
</html>
""";

    /**
     * 构建路径栏（面包屑）。
     */
    private String buildFinderPathbar(String bucket, String dir) {
        StringBuilder sb = new StringBuilder();
        sb.append("<a href=\"/").append(encodePathSeg(bucket)).append("\">").append(escapeAttr(bucket)).append("</a>");
        if (dir != null && !dir.isEmpty()) {
            String[] segments = dir.split("/");
            StringBuilder acc = new StringBuilder();
            for (String seg : segments) {
                acc.append(seg);
                sb.append("<span class=\"sep\">\u203A</span>");
                sb.append("<a href=\"/").append(encodePathSeg(bucket)).append("/")
                        .append(encodePathFull(acc.toString())).append("/\">")
                        .append(escapeAttr(seg)).append("</a>");
                acc.append("/");
            }
        }
        return sb.toString();
    }

    /**
     * 构建左侧边栏：收藏 + 存储桶。
     */
    private String buildFinderSidebar(String bucket) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"side-group\">\u6536\u85CF</div>");
        sb.append("<a class=\"side-item active\" href=\"/").append(encodePathSeg(bucket)).append("\">");
        sb.append(macFolderSvg()).append("<span>").append(escapeAttr(bucket)).append("</span></a>");
        sb.append("<div class=\"side-group\">\u5176\u4ED6\u5B58\u50A8\u6876</div>");
        return sb.toString();
    }

    /**
     * 构建文件列表行（标准列表 / 大图网格共用）。
     */
    private String buildFinderRows(String bucket, String dir, List<Metadata> items) {
        StringBuilder sb = new StringBuilder();
        if (dir != null && !dir.isEmpty()) {
            int lastSlash = dir.lastIndexOf('/');
            String parentHref = lastSlash < 0
                    ? "/" + encodePathSeg(bucket) + "/"
                    : "/" + encodePathSeg(bucket) + "/" + encodePathFull(dir.substring(0, lastSlash)) + "/";
            sb.append("<a class=\"lrow up\" href=\"").append(parentHref).append("\" data-nav=\"parent\">");
            sb.append("<span class=\"l-ico\">\u21A9\uFE0F</span>");
            sb.append("<span class=\"l-name\">.. \u8FD4\u56DE\u4E0A\u7EA7</span>");
            sb.append("<span class=\"l-size\"></span><span class=\"l-time\"></span></a>");
        }
        if (items == null || items.isEmpty()) {
            sb.append("<div class=\"empty\">");
            sb.append(dir == null || dir.isEmpty() ? "\u8BE5\u5B58\u50A8\u4E3A\u7A7A" : "\u8BE5\u76EE\u5F55\u4E3A\u7A7A");
            sb.append("</div>");
        } else {
            List<Metadata> sorted = items.stream()
                    .sorted((a, b) -> {
                        if (a.isDirectory() != b.isDirectory()) {
                            return a.isDirectory() ? -1 : 1;
                        }
                        return a.getName().compareToIgnoreCase(b.getName());
                    }).toList();
            for (Metadata m : sorted) {
                String href = buildHref(bucket, dir, m);
                String objKey = (dir == null || dir.isEmpty()) ? m.getName() : dir + "/" + m.getName();
                sb.append("<a class=\"lrow").append(m.isDirectory() ? " dir" : "").append("\" href=\"");
                sb.append(href).append("\"");
                sb.append(" data-name=\"").append(escapeAttr(m.getName())).append("\"");
                sb.append(" data-href=\"").append(escapeAttr(href)).append("\"");
                sb.append(" data-type=\"").append(m.isDirectory() ? "dir" : "file").append("\"");
                sb.append(" data-size=\"").append(m.getSize()).append("\"");
                sb.append(" data-key=\"").append(escapeAttr(objKey)).append("\">");
                sb.append("<span class=\"l-ico\">").append(fileIcon(m)).append("</span>");
                sb.append("<span class=\"l-name\">").append(escapeHtml(m.getName())).append("</span>");
                sb.append("<span class=\"l-size\">").append(m.isDirectory() ? "" : formatSize(m.getSize())).append("</span>");
                sb.append("<span class=\"l-time\">").append(m.getLastModified() > 0 ? formatTime(m.getLastModified()) : "").append("</span>");
                sb.append("</a>");
            }
        }
        return sb.toString();
    }

    /**
     * Finder 前端脚本：视图切换、右键菜单（预览对话框/下载/一次性链接/复制/删除）、搜索过滤。
     */
    private String buildFinderScript(String bucket) {
        return """
(function(){
var list=document.getElementById('list');if(!list)return;
var menu=document.getElementById('ctxMenu');
var cur=null;
function findParent(el){while(el&&!el.classList.contains('lrow'))el=el.parentElement;return el;}
function pickRow(el){var r=findParent(el);if(r)showMenu(r);}
function rowHref(r){return r.dataset?r.dataset.href:'';}
function rowName(r){return r.dataset?r.dataset.name:'';}
function rowKey(r){return r.dataset?r.dataset.key:'';}
function rowType(r){return r.dataset?r.dataset.type:'file';}
function showMenu(r){cur=r;var rows=document.querySelectorAll('.lrow');
rows.forEach(function(x){x.classList.remove('selected')});r.classList.add('selected');
var type=rowType(r),href=rowHref(r),name=rowName(r);
var html='';
if(type==='dir'){
  html+='<div class="ctx-item" data-act="open">📁 打开</div>';
}else{
  html+='<div class="ctx-item" data-act="preview">👀 预览</div>';
  html+='<div class="ctx-item" data-act="download">⬇️ 下载</div>';
  html+='<div class="ctx-item" data-act="onetime">🔗 一次性链接</div>';
  html+='<div class="ctx-item" data-act="open">📄 打开</div>';
}
html+='<div class="ctx-sep"></div>';
html+='<div class="ctx-item" data-act="copy">📎 复制链接</div>';
html+='<div class="ctx-item danger" data-act="delete">🗑️ 删除</div>';
menu.innerHTML=html;
menu.style.display='block';
var mw=menu.offsetWidth,mh=menu.offsetHeight;
var x=window.event.clientX||0,y=window.event.clientY||0;
if(x+mw>window.innerWidth)x=window.innerWidth-mw-8;
if(y+mh>window.innerHeight)y=window.innerHeight-mh-8;
menu.style.left=x+'px';menu.style.top=y+'px';}
document.addEventListener('contextmenu',function(e){
var r=findParent(e.target);
if(r&&!(r.dataset&&r.dataset.nav==='parent')){
  e.preventDefault();showMenu(r);return false;}});
document.addEventListener('click',function(e){
if(e.target.closest('.ctx'))return;
if(e.target.closest('.pv-mask'))return;
menu.style.display='none';});
document.addEventListener('keydown',function(e){if(e.key==='Escape'){menu.style.display='none';closePv();closeOt();}});
function kindOf(name){var ext=(name.split('.').pop()||'').toLowerCase();
if(['png','jpg','jpeg','gif','svg','webp','bmp','ico'].indexOf(ext)>=0)return 'image';
if(['mp4','webm','ogg','mov','avi','mkv','flv','wmv','m4v','3gp'].indexOf(ext)>=0)return 'video';
if(['mp3','wav','flac','m4a'].indexOf(ext)>=0)return 'audio';
if(['pdf'].indexOf(ext)>=0)return 'pdf';
if(['txt','md','log','json','xml','csv','html','js','ts','java','py','c','cpp','go','sql'].indexOf(ext)>=0)return 'text';
return 'other';}
function openPreview(href,name){
var body=document.getElementById('pvBody'),title=document.getElementById('pvTitle');
title.textContent=name;body.innerHTML='<div style="padding:2rem;text-align:center;color:#86868b">加载中…</div>';
document.getElementById('pvMask').classList.add('show');
var kind=kindOf(name);
if(kind==='image'){body.innerHTML='<img src="'+href+'">';}
else if(kind==='video'){body.innerHTML='<video src="'+href+'" controls autoplay></video>';}
else if(kind==='audio'){body.innerHTML='<audio src="'+href+'" controls autoplay></audio>';}
else if(kind==='text'){
  fetch(href).then(function(r){return r.text()}).then(function(t){body.innerHTML='<pre>'+t.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')+'</pre>';}).catch(function(){body.innerHTML='<pre>加载失败</pre>';});
}else{
  var sep=href.indexOf('?')<0?'?':'&';
  body.innerHTML='<iframe src="'+href+sep+'preview=true"></iframe>';
}}
function closePv(){document.getElementById('pvMask').classList.remove('show');document.getElementById('pvBody').innerHTML='';}
function closeOt(){document.getElementById('otMask').classList.remove('show');}
function copyOt(){var i=document.getElementById('otUrl');i.select();try{document.execCommand('copy');}catch(e){}
var b=document.getElementById('otCopyBtn');if(b){b.textContent='已复制';setTimeout(function(){b.textContent='复制';},1500);}}
function openOnetime(r){
var bucket=document.querySelector('.content').dataset.bucket,key=rowKey(r);
fetch('/ot?bucket='+encodeURIComponent(bucket)+'&key='+encodeURIComponent(key),{method:'POST'})
.then(function(res){return res.json()})
.then(function(data){
  var i=document.getElementById('otUrl');i.value=location.origin+data.url;
  document.getElementById('otMask').classList.add('show');})
.catch(function(e){alert('创建一次性链接失败: '+e);});}
menu.addEventListener('click',function(e){
var item=e.target.closest('.ctx-item');if(!item)return;
var act=item.dataset.act;var r=cur;var href=rowHref(r),name=rowName(r);
menu.style.display='none';
if(act==='open'){window.location.href=href;}
else if(act==='preview'){openPreview(href,name);}
else if(act==='download'){var ds=href.indexOf('?')<0?'?':'&';window.open(href+ds+'download=true','_blank');}
else if(act==='onetime'){openOnetime(r);}
else if(act==='copy'){try{navigator.clipboard.writeText(location.origin+href).then(function(){},function(){});}catch(err){}}
else if(act==='delete'){if(!confirm('确认删除 '+name+' ?'))return;fetch(href,{method:'DELETE'}).then(function(res){if(res.ok){location.reload();}else{alert('删除失败 HTTP '+res.status);}});}});
var viewMode=localStorage.getItem('filestorage.view')||'list';
var btnList=document.getElementById('viewList'),btnGrid=document.getElementById('viewGrid');
function applyView(m){viewMode=m;localStorage.setItem('filestorage.view',m);list.classList.toggle('grid',m==='grid');if(btnList&&btnGrid){btnList.classList.toggle('active',m!=='grid');btnGrid.classList.toggle('active',m==='grid');}}
if(btnList)btnList.addEventListener('click',function(){applyView('list')});
if(btnGrid)btnGrid.addEventListener('click',function(){applyView('grid')});
applyView(viewMode);
var rows=document.querySelectorAll('.lrow');
rows.forEach(function(r){r.addEventListener('click',function(){rows.forEach(function(x){x.classList.remove('selected')});r.classList.add('selected');});});
var search=document.getElementById('search');
if(search){search.addEventListener('input',function(){var q=search.value.toLowerCase();rows.forEach(function(r){if(!r.dataset||r.dataset.nav==='parent')return;var n=(r.dataset.name||'').toLowerCase();r.style.display=n.indexOf(q)>=0?'':'none';});});}
})();
""";
    }

    /**
     * macOS 风格蓝色文件夹 SVG（内联，避免外部依赖）。
     */
    private String macFolderSvg() {
        return "<svg width=\"16\" height=\"16\" viewBox=\"0 0 32 32\" fill=\"none\">"
                + "<path d=\"M4 8.5C4 6.6 5.6 5 7.5 5H13l2.5 2.5H24.5C26.4 7.5 28 9.1 28 11v12.5c0 1.9-1.6 3.5-3.5 3.5h-17A3.5 3.5 0 0 1 4 23.5V8.5Z\" fill=\"#7FB3F7\"/>"
                + "<path d=\"M4 13h24v10.5c0 1.9-1.6 3.5-3.5 3.5h-17A3.5 3.5 0 0 1 4 23.5V13Z\" fill=\"#4A90E2\"/>"
                + "</svg>";
    }

    private void renderBreadcrumb(StringBuilder sb, String bucket, String dir) {
        sb.append("<div class=\"topbar\">");
        sb.append("<span class=\"logo\">\uD83D\uDCC1 文件存储</span>");
        sb.append("<div class=\"crumb\"><a href=\"/").append(encodePathSeg(bucket)).append("\">");
        sb.append(escapeAttr(bucket)).append("</a>");
        if (dir != null && !dir.isEmpty()) {
            String[] segments = dir.split("/");
            StringBuilder acc = new StringBuilder();
            for (String seg : segments) {
                acc.append(seg);
                sb.append("<span class=\"sep\">/</span>");
                sb.append("<a href=\"/").append(encodePathSeg(bucket)).append("/")
                        .append(encodePathFull(acc.toString())).append("/\">")
                        .append(escapeAttr(seg)).append("</a>");
                acc.append("/");
            }
        }
        sb.append("</div></div>");
    }

    private void renderEntriesSimple(StringBuilder sb, String bucket, String dir, List<Metadata> items) {
        sb.append("<div class=\"panel\">");
        if (dir != null && !dir.isEmpty()) {
            int lastSlash = dir.lastIndexOf('/');
            String parentHref = lastSlash < 0
                    ? "/" + encodePathSeg(bucket) + "/"
                    : "/" + encodePathSeg(bucket) + "/" + encodePathFull(dir.substring(0, lastSlash)) + "/";
            sb.append("<a class=\"entry up\" href=\"").append(parentHref).append("\">");
            sb.append("<span class=\"icon\">\u21E7</span><span class=\"name\">..</span><span class=\"meta\"></span></a>");
        }
        if (items == null || items.isEmpty()) {
            sb.append("<div class=\"empty\">\u7A7A\u76EE\u5F55</div>");
        } else {
            List<Metadata> sorted = items.stream()
                    .sorted((a, b) -> {
                        if (a.isDirectory() != b.isDirectory()) {
                            return a.isDirectory() ? -1 : 1;
                        }
                        return a.getName().compareToIgnoreCase(b.getName());
                    }).toList();
            for (Metadata m : sorted) {
                String href = buildHref(bucket, dir, m);
                sb.append("<a class=\"entry").append(m.isDirectory() ? " dir" : "").append("\" href=\"");
                sb.append(href).append("\">");
                sb.append("<span class=\"icon\">").append(fileIcon(m)).append("</span>");
                sb.append("<span class=\"name\">").append(escapeHtml(m.getName())).append("</span>");
                sb.append("<span class=\"meta\">");
                if (!m.isDirectory() && m.getSize() > 0) {
                    sb.append(formatSize(m.getSize()));
                }
                if (m.getLastModified() > 0) {
                    sb.append(" &middot; ").append(formatTime(m.getLastModified()));
                }
                sb.append("</span></a>");
            }
        }
        sb.append("</div>");
    }

    private String buildHref(String bucket, String dir, Metadata m) {
        StringBuilder sb = new StringBuilder("/");
        sb.append(encodePathSeg(bucket));
        if (dir != null && !dir.isEmpty()) {
            sb.append("/").append(encodePathFull(dir));
        }
        sb.append("/").append(encodePathSeg(m.getName()));
        if (m.isDirectory()) {
            sb.append("/");
        }
        return sb.toString();
    }

    private String fileIcon(Metadata m) {
        if (m.isDirectory()) {
            return "\uD83D\uDCC1";
        }
        String name = m.getName().toLowerCase();
        if (name.matches(".*\\.(png|jpe?g|gif|svg|webp|bmp|ico)$")) {
            return "\uD83D\uDDBC\uFE0F";
        }
        if (name.matches(".*\\.(mp4|mov|webm|avi|mkv|3gp)$")) {
            return "\uD83C\uDFA5";
        }
        if (name.matches(".*\\.(mp3|wav|flac|ogg|m4a)$")) {
            return "\uD83C\uDFB5";
        }
        if (name.endsWith(".pdf")) {
            return "\uD83D\uDCC4";
        }
        if (name.matches(".*\\.(zip|tar|gz|rar|7z|bz2|xz)$")) {
            return "\uD83D\uDCE6";
        }
        if (name.matches(".*\\.(docx?|xlsx?|pptx?|csv)$")) {
            return "\uD83D\uDCC3";
        }
        if (name.matches(".*\\.(txt|md|log|json|xml|html|css|java|py|js|ts|go|c|h|cpp|sql|sh)$")) {
            return "\uD83D\uDD8C";
        }
        return "\uD83D\uDCC4";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double size = bytes;
        int i = -1;
        do {
            size /= 1024;
            i++;
        } while (size >= 1024 && i < units.length - 1);
        return String.format("%.1f %s", size, units[i]);
    }

    private String formatTime(long millis) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(millis);
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalDateTime dt = java.time.LocalDateTime.ofInstant(instant, zone);
        return String.format("%04d-%02d-%02d %02d:%02d",
                dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(),
                dt.getHour(), dt.getMinute());
    }

    private String encodePathSeg(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    private String encodePathFull(String path) {
        String[] parts = path.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append("/");
            }
            if (!parts[i].isEmpty()) {
                sb.append(encodePathSeg(parts[i]));
            }
        }
        return sb.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 处理对象下载/预览请求。
     *
     * <p>当 Key 对应对象不存在而作为目录存在时（或路径以 {@code /} 结尾），
     * 自动回退为该目录的对象列表 JSON，实现类资源管理器的路径式目录浏览。</p>
     *
     * <p>支持 Range 分段（openRange）、附件下载（openDownload + download=true 参数）、
     * 内联预览（openPreview）、水印响应头（openWatermark）、远程文件拉取（openRemoteFile）。</p>
     *
     * @param storage  存储实例
     * @param request  请求
     * @param bucket   存储名
     * @param response 响应
     * @param key      对象 Key
     * @return 异步阶段
     */
    private CompletionStage<Void> handleGet(FileStorage storage, ServerRequest request, String bucket, ServerResponse response, String key) {
        applyWatermarkHeader(response);
        // 路径以 / 结尾视为明确的目录访问
        boolean directoryIntent = request.getPath().endsWith("/");

        // 远程文件透传：Key 为完整 http(s) 地址时直接拉取
        if (setting.isOpenRemoteFile() && (key.startsWith("http://") || key.startsWith("https://"))) {
            return handleRemoteFile(request, response, key);
        }

        GetObjectResult result = storage.getObject(key);
        if ((result.getResultCode() != ObjectResult.ResultCode.SUCCESS || result.getInputStream() == null)
                && !directoryIntent) {
            // 对象不存在时尝试按目录回退列出
            ListObjectResult list = storage.listObject(ListObjectRequest.builder()
                    .filePath(key)
                    .limit(Integer.MAX_VALUE)
                    .build());
            if (list.getResultCode() == ObjectResult.ResultCode.SUCCESS
                    && list.getMetadata() != null && !list.getMetadata().isEmpty()) {
                return writeList(bucket, key, list, request, response);
            }
            return error(response, 404, "Object Not Found: " + key);
        }
        if (directoryIntent) {
            // 显式目录访问：直接列出（空目录返回空列表而非 404）
            ListObjectResult list = storage.listObject(ListObjectRequest.builder()
                    .filePath(key)
                    .limit(Integer.MAX_VALUE)
                    .build());
            if (list.getResultCode() == ObjectResult.ResultCode.SUCCESS) {
                return writeList(bucket, key, list, request, response);
            }
            return error(response, 404, "Directory Not Found: " + key);
        }

        // config 访问模式（与 preview/download 平级）：返回文件级配置 JSON
        if (boolParam(request, "config")) {
            return writeObjectConfig(bucket, key, result, response);
        }

        byte[] data;
        try (InputStream in = result.getInputStream()) {
            data = in.readAllBytes();
        } catch (Exception e) {
            log.error("读取对象失败: {} - {}", key, e.getMessage(), e);
            return error(response, 500, "Read Object Failed");
        }

        // 访问模式收敛：显式 preview/download 参数优先，其次按设置降级
        // 参数支持标志式写法（?preview 等价 ?preview=true）
        boolean wantPreview = boolParam(request, "preview");
        boolean wantDownload = boolParam(request, "download");
        boolean download;
        if (!setting.isOpenPreview() && !setting.isOpenDownload()) {
            // 严格模式：预览与下载均关闭 → 拒绝所有请求
            return error(response, 403, "Access Denied: preview/download disabled");
        } else if (wantPreview && setting.isOpenPreview()) {
            // 显式 ?preview=true 且 openPreview 已启用 → 内联预览
            download = false;
        } else if (wantDownload && setting.isOpenDownload()) {
            // 显式 ?download=true → 附件下载
            download = true;
        } else if (!wantPreview && !wantDownload) {
            // 无 ?preview 也无 ?download → 默认 inline（不强制下载，不触发 SPI）
            download = false;
        } else if (setting.isOpenDownload()) {
            // ?preview=false 或 openPreview 未启用 → 退回下载
            download = true;
        } else {
            // ?download=false 且 openPreview 已启用 → inline
            download = false;
        }
        String fileName = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
        response.setHeader("Content-Disposition", buildDisposition(download, fileName));

        // 显式 ?preview=true 时才尝试 SPI 预览（xlsx/docx/ppt 等 Office 文档转 HTML）
        if (!download && wantPreview) {
            byte[] previewPage = trySpiPreview(data, fileName);
            if (previewPage != null) {
                response.setStatus(200)
                        .setHeader("X-FileStorage-Preview", "spi")
                        .setContentType("text/html;charset=UTF-8")
                        .setBody(previewPage)
                        .end();
                return CompletableFuture.completedFuture(null);
            }
            // JSON Hero 内联预览：.json 文件通过 iframe 嵌入 jsonhero.io 渲染
            if (fileName.toLowerCase().endsWith(".json")) {
                String json = new String(data, StandardCharsets.UTF_8);
                String encoded = java.util.Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
                String heroUrl = "https://jsonhero.io/new?j=" + encoded;
                String html = "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                        + "<title>JSON Hero - " + escapeJson(fileName) + "</title>"
                        + "<style>html,body{margin:0;padding:0;height:100%;overflow:hidden}"
                        + "iframe{width:100%;height:100%;border:none}</style>"
                        + "</head><body>"
                        + "<iframe src=\"" + escapeAttr(heroUrl) + "\" allowfullscreen></iframe>"
                        + "</body></html>";
                response.setStatus(200)
                        .setHeader("X-FileStorage-Preview", "jsonhero")
                        .setContentType("text/html;charset=UTF-8")
                        .setBody(html.getBytes(StandardCharsets.UTF_8))
                        .end();
                return CompletableFuture.completedFuture(null);
            }
        }

        // Range 分段下载
        String range = request.getHeader("Range");
        if (setting.isOpenRange() && range != null && range.startsWith("bytes=")) {
            long[] offsets = parseRange(range, data.length);
            if (offsets != null) {
                byte[] slice = Arrays.copyOfRange(data, (int) offsets[0], (int) offsets[1]);
                response.setStatus(206)
                        .setHeader("Content-Range", "bytes " + offsets[0] + "-" + (offsets[1] - 1) + "/" + data.length)
                        .setHeader("Accept-Ranges", "bytes")
                        .setContentType(resolveContentType(fileName))
                        .setBody(slice)
                        .end();
                return CompletableFuture.completedFuture(null);
            }
        }

        response.setStatus(200)
                .setHeader("Accept-Ranges", setting.isOpenRange() ? "bytes" : "none")
                .setContentType(resolveContentType(fileName))
                .setBody(data)
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 处理远程文件拉取。
     *
     * @param request  请求
     * @param response 响应
     * @param remoteUrl 远程地址
     * @return 异步阶段
     */
    /**
     * 处理远程文件拉取。
     *
     * <p>使用直连方式（不走系统代理），避免本机代理拦截内网地址导致拉取失败。</p>
     *
     * @param request  请求
     * @param response 响应
     * @param remoteUrl 远程地址
     * @return 异步阶段
     */
    private CompletionStage<Void> handleRemoteFile(ServerRequest request, ServerResponse response, String remoteUrl) {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                    URI.create(remoteUrl).toURL().openConnection(java.net.Proxy.NO_PROXY);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            byte[] data;
            try (InputStream in = connection.getInputStream()) {
                data = in.readAllBytes();
            }
            String fileName = remoteUrl.substring(remoteUrl.lastIndexOf('/') + 1);
            response.setStatus(200)
                    .setContentType(resolveContentType(fileName))
                    .setBody(data)
                    .end();
        } catch (Exception e) {
            log.warn("远程文件拉取失败: {} - {}", remoteUrl, e.getMessage());
            response.sendError(502, "Remote Fetch Failed: " + e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 处理存在性检查请求。
     *
     * @param storage  存储实例
     * @param response 响应
     * @param key      对象 Key
     * @return 异步阶段
     */
    private CompletionStage<Void> handleExist(FileStorage storage, ServerResponse response, String key) {
        ExistObjectResult result = storage.existObject(ExistObjectRequest.builder()
                .fileName(key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key)
                .filePath(key.contains("/") ? key.substring(0, key.lastIndexOf('/')) : "")
                .build());
        boolean exists = result.isExists();
        response.setStatus(exists ? 200 : 404)
                .setContentType("application/json;charset=UTF-8")
                .setBody("{\"exists\":" + exists + "}")
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 处理对象上传请求。
     *
     * @param storage  存储实例
     * @param request  请求
     * @param response 响应
     * @param key      对象 Key
     * @return 异步阶段
     */
    private CompletionStage<Void> handlePut(FileStorage storage, ServerRequest request, ServerResponse response, String key) {
        if (key.isEmpty()) {
            return error(response, 400, "Object key is required");
        }
        byte[] body = request.getBody();
        if (body == null || body.length == 0) {
            return error(response, 400, "Empty request body");
        }
        String filePath = "";
        String fileName = key;
        if (key.contains("/")) {
            filePath = key.substring(0, key.lastIndexOf('/'));
            fileName = key.substring(key.lastIndexOf('/') + 1);
        }
        PutObjectResult result = storage.putObject(PutObjectRequest.builder()
                .fileName(fileName)
                .filePath(filePath)
                .content(body)
                .build());
        if (result.getResultCode() != ObjectResult.ResultCode.SUCCESS) {
            return error(response, 500, "Put Object Failed: "
                        + (result.getMessage() == null ? "" : result.getMessage()));
        }
        String url = result.getUrl() != null ? result.getUrl() : result.getKey();
        response.setStatus(200)
                .setContentType("application/json;charset=UTF-8")
                .setBody("{\"status\":\"SUCCESS\",\"key\":\"" + escapeJson(key) + "\",\"url\":\""
                        + escapeJson(url == null ? "" : url) + "\"}")
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 处理对象删除请求。
     *
     * @param storage  存储实例
     * @param response 响应
     * @param key      对象 Key
     * @return 异步阶段
     */
    private CompletionStage<Void> handleDelete(FileStorage storage, ServerResponse response, String key) {
        DeleteObjectResult result = storage.deleteObject(key);
        if (result.getResultCode() != ObjectResult.ResultCode.SUCCESS) {
            return error(response, 500, "Delete Object Failed");
        }
        response.setStatus(200)
                .setContentType("application/json;charset=UTF-8")
                .setBody("{\"status\":\"SUCCESS\",\"deleted\":\"" + escapeJson(key) + "\"}")
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 依据设置追加水印响应头。
     *
     * @param response 响应
     */
    private void applyWatermarkHeader(ServerResponse response) {
        if (setting.isOpenWatermark() && setting.getWatermark() != null) {
            response.setHeader("X-Watermark", setting.getWatermark());
        }
    }

    /**
     * 解析 Range 请求头。
     *
     * @param range     Range 头值（bytes=start-end 格式）
     * @param totalSize 数据总长度
     * @return [start, end] 区间（end 为排他上界），格式非法返回 null
     */
    private long[] parseRange(String range, int totalSize) {
        try {
            String spec = range.substring("bytes=".length());
            String[] parts = spec.split("-", 2);
            long start = parts[0].isEmpty() ? -1 : Long.parseLong(parts[0].trim());
            long end = parts.length > 1 && !parts[1].isEmpty()
                    ? Long.parseLong(parts[1].trim()) : totalSize - 1;
            if (start == -1) {
                // 后缀区间：bytes=-N 表示最后 N 字节
                start = Math.max(0, totalSize - end);
                end = totalSize - 1;
            }
            if (start < 0 || start >= totalSize || end < start) {
                return null;
            }
            return new long[]{start, Math.min(end, totalSize - 1) + 1};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 规范化对象 Key。
     *
     * <p>执行 URL 解码，拒绝包含 {@code ..}、{@code :} 等危险片段的路径穿越 Key。
     * http(s) 开头的远程地址仅在 openRemoteFile 开启时放行。</p>
     *
     * @param rawKey 原始 Key
     * @return 规范化后的 Key，非法时返回 null
     */
    private String sanitizeKey(String rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            return "";
        }
        try {
            String decoded = URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name());
            // 远程地址仅受 openRemoteFile 开关控制
            if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                return setting.isOpenRemoteFile() ? decoded : null;
            }
            for (String segment : decoded.split("/")) {
                if ("..".equals(segment) || segment.contains(":")) {
                    return null;
                }
            }
            return decoded;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析布尔查询参数（标志式语义）。
     *
     * <p>{@code ?preview}、{@code ?preview=true}、{@code ?preview=1} 均视为 true；
     * {@code ?preview=false}、{@code ?preview=0} 视为 false；参数缺省为 false。</p>
     *
     * @param request 请求
     * @param name    参数名
     * @return 参数开启状态
     */
    private boolean boolParam(ServerRequest request, String name) {
        Map<String, String> params = request.getParams();
        if (!params.containsKey(name)) {
            return false;
        }
        String value = params.get(name);
        if (value == null || value.isEmpty() || "true".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        return false;
    }

    /**
     * 尝试通过 SPI 预览提供者转换文件为 HTML 预览页（带并发去重）。
     *
     * <p>classpath 中存在 utils-support-filestorage-starter 时，可预览
     * xlsx/xls/docx/doc/pptx/ppt（Univer）、odt/ods/odp（OpenDocument）、vsd（Visio）等；
     * 无可用提供者或转换失败时返回 null，回退为原始字节响应。</p>
     *
     * <p>并发去重：同一文件的并发请求会共享同一个转换 Future，
     * 避免重复调用 SPI 提供者浪费 CPU/IO。</p>
     *
     * @param content  文件内容
     * @param fileName 文件名（用于推断扩展名）
     * @return HTML 页面字节，无法预览返回 null
     */
    private byte[] trySpiPreview(byte[] content, String fileName) {
        List<FileStoragePreviewProvider> providers = previewProviders();
        if (providers.isEmpty()) {
            return null;
        }
        String ext = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";

        // 构建去重 key：内容首 1KB + 长度 + 扩展名（兼顾性能与唯一性）
        String dedupKey = buildDedupKey(content, ext);

        CompletableFuture<byte[]> future = inflightMap.computeIfAbsent(dedupKey, k -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    for (FileStoragePreviewProvider provider : providers) {
                        if (!provider.supports(ext, "")) {
                            continue;
                        }
                        try {
                            PreviewResult result = provider.preview(content, ext, "");
                            if (result == null) {
                                continue;
                            }
                            return wrapPreviewPage(result).getBytes(StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            log.warn("SPI 预览提供者 {} 转换失败: {}",
                                    provider.getClass().getSimpleName(), e.getMessage());
                        }
                    }
                    return null;
                } finally {
                    inflightMap.remove(k);
                }
            });
        });

        try {
            return future.get(5, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("等待 SPI 预览转换完成超时或失败: fileName={}, error={}", fileName, e.getMessage());
            future.cancel(true);
            inflightMap.remove(dedupKey);
            return null;
        }
    }

    /**
     * 获取 SPI 预览提供者列表（懒加载并缓存）。
     *
     * @return 提供者列表（可能为空）
     */
    private List<FileStoragePreviewProvider> previewProviders() {
        List<FileStoragePreviewProvider> cached = previewProviderCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (previewProviderCache == null) {
                List<FileStoragePreviewProvider> loaded = new java.util.ArrayList<>();
                try {
                    loaded.addAll(com.chua.common.support.spi.ServiceProvider
                            .of(FileStoragePreviewProvider.class).getNewExtensions(null));
                } catch (Throwable t) {
                    log.warn("SPI 预览提供者加载失败（缺少 utils-support-filestorage-starter 依赖时忽略）: {}", t.getMessage());
                }
                previewProviderCache = loaded;
            }
            return previewProviderCache;
        }
    }

    /**
     * 将预览结果组装为完整 HTML 页面。
     *
     * @param result 预览结果
     * @return 完整 HTML 字符串
     */
    private String wrapPreviewPage(PreviewResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        if (result.getEmbeddedCss() != null) {
            sb.append("<style>").append(result.getEmbeddedCss()).append("</style>");
        }
        if (result.getCssUrls() != null) {
            for (String url : result.getCssUrls()) {
                sb.append("<link rel=\"stylesheet\" href=\"").append(escapeAttr(url)).append("\">");
            }
        }
        sb.append("</head><body>");
        if (result.getHtmlContent() != null) {
            sb.append(result.getHtmlContent());
        }
        if (result.getEmbeddedJs() != null) {
            sb.append("<script>").append(result.getEmbeddedJs()).append("</script>");
        }
        if (result.getJsUrls() != null) {
            for (String url : result.getJsUrls()) {
                sb.append("<script src=\"").append(escapeAttr(url)).append("\"></script>");
            }
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * 转义 HTML 属性中的特殊字符。
     *
     * @param s 原始字符串
     * @return 转义后的字符串
     */
    private static String escapeAttr(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    /**
     * 构建 Content-Disposition 头。
     *
     * <p>ASCII 文件名直接使用 {@code filename}；含非 ASCII 字符时按 RFC 5987
     * 追加 {@code filename*=UTF-8''} 编码形式，同时提供 ASCII 回退，
     * 避免非法响应头导致服务端异常。</p>
     *
     * @param download true 表示附件下载（attachment），false 表示内联预览（inline）
     * @param fileName 文件名
     * @return Content-Disposition 头值
     */
    private String buildDisposition(boolean download, String fileName) {
        String type = download ? "attachment" : "inline";
        boolean ascii = fileName.chars().allMatch(c -> c > 31 && c < 127);
        if (ascii) {
            return type + "; filename=\"" + fileName + "\"";
        }
        try {
            String encoded = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
            return type + "; filename=\"" + type + "\"" + "; filename*=UTF-8''" + encoded;
        } catch (Exception e) {
            return type;
        }
    }

    /**
     * 按扩展名推断 Content-Type。
     *
     * @param fileName 文件名
     * @return Content-Type
     */
    private String resolveContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".md")) {
            return "text/plain;charset=UTF-8";
        }
        if (lower.endsWith(".json")) {
            return "application/json;charset=UTF-8";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html;charset=UTF-8";
        }
        if (lower.endsWith(".xml")) {
            return "application/xml;charset=UTF-8";
        }
        if (lower.endsWith(".csv")) {
            return "text/csv;charset=UTF-8";
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return "text/yaml;charset=UTF-8";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (lower.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (lower.endsWith(".css")) {
            return "text/css;charset=UTF-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript;charset=UTF-8";
        }
        if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".m4v") || lower.endsWith(".3gp")) {
            return "video/mp4";
        }
        if (lower.endsWith(".webm")) {
            return "video/webm";
        }
        if (lower.endsWith(".avi") || lower.endsWith(".wmv") || lower.endsWith(".flv")) {
            return "video/x-msvideo";
        }
        if (lower.endsWith(".mkv") || lower.endsWith(".webm")) {
            return "video/x-matroska";
        }
        if (lower.endsWith(".ogg")) {
            return "audio/ogg";
        }
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lower.endsWith(".flac")) {
            return "audio/flac";
        }
        if (lower.endsWith(".m4a")) {
            return "audio/mp4";
        }
        if (lower.endsWith(".ttf")) {
            return "font/ttf";
        }
        if (lower.endsWith(".otf")) {
            return "font/otf";
        }
        if (lower.endsWith(".woff")) {
            return "font/woff";
        }
        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (lower.endsWith(".eot")) {
            return "application/vnd.ms-fontobject";
        }
        if (lower.endsWith(".epub")) {
            return "application/epub+zip";
        }
        if (lower.endsWith(".rtf")) {
            return "application/rtf";
        }
        if (lower.endsWith(".eml")) {
            return "message/rfc822";
        }
        if (lower.endsWith(".ics")) {
            return "text/calendar";
        }
        if (lower.endsWith(".vcf")) {
            return "text/vcard";
        }
        if (lower.endsWith(".fbx")) {
            return "application/octet-stream";
        }
        if (lower.endsWith(".blend")) {
            return "application/octet-stream";
        }
        if (lower.endsWith(".shp")) {
            return "application/octet-stream";
        }
        return "application/octet-stream";
    }

    /**
     * 拆分请求路径为段列表。
     *
     * @param path 请求路径
     * @return 非空的路径段列表
     */
    private List<String> splitPath(String path) {
        if (path == null) {
            return List.of();
        }
        return Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isEmpty())
                .toList();
    }

    /**
     * 转义 JSON 字符串中的特殊字符。
     *
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 构建 SPI 预览去重键。
     *
     * <p>取内容首 1KB + 总长度 + 扩展名的组合作为键，
     * 兼顾性能（避免全量哈希）与唯一性（不同内容大概率不同键）。</p>
     *
     * @param content 文件内容
     * @param ext     文件扩展名
     * @return 去重键
     */
    private static String buildDedupKey(byte[] content, String ext) {
        int sampleLen = Math.min(content.length, 1024);
        long hash = 0xcbf29ce484222325L; // FNV-1a 初始值
        for (int i = 0; i < sampleLen; i++) {
            hash ^= content[i];
            hash *= 0x100000001b3L;
        }
        return Long.toHexString(hash) + ":" + content.length + ":" + ext;
    }
}
