package com.chua.starter.filesystem.support.server;

import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.server.filter.ReactiveFilterChain;
import com.chua.common.support.network.server.filter.ReactiveServerFilter;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.annotations.SpiDescribe;
import com.chua.cad.support.dwg.DwgHeaderInfo;
import com.chua.cad.support.dwg.DwgHeaderParser;
import com.chua.cad.support.dxf.DxfDocument;
import com.chua.cad.support.dxf.DxfParser;
import com.chua.cad.support.dxf.DxfSvgRenderer;
import com.chua.common.support.reflection.ReflectUtils;
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
import com.chua.filestorage.support.preview.ClientPreviewSupport;
import com.chua.filestorage.support.preview.FileStoragePreviewProvider;
import com.chua.filestorage.support.preview.PreviewResult;
import com.chua.filestorage.support.preview.provider.ArchivePreviewProvider;
import com.chua.common.support.file.converter.ImageOperation;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.filestorage.support.operation.FileOperationSetting;
import com.chua.filestorage.support.spi.FileStorageFileSetting;
import javax.imageio.ImageIO;
import java.util.Locale;
import com.chua.filestorage.support.spi.FileStorageFileSetting;
import com.chua.filestorage.support.spi.FileStorageFilterSetting;
import com.chua.filestorage.support.utils.MimeTypeUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 文件存储服务器过滤器。
 *
 * <p>基于 {@link ServerFilter} SPI 的 REST 文件存储网关，将 HTTP 请求映射到
 * {@link FileStorage} 操作。路径规则：{@code /{storageName}/{objectKey}}，
 * 其中 storage名称 通过 {@link #addFileStorage(String, FileStorage)} 注册；
 * 未注册的路径将放行给后续过滤器链。</p>
 *
 * <p>支持的 HTTP 方法：</p>
 * <ul>
 *   <li>{@code GET /{name}} — 列出对象（JSON）</li>
 *   <li>{@code GET /{name}/{key}} — 下载/预览对象（支持 Range 分段、远程文件透传）</li>
 *   <li>{@code HEAD /{name}/{key}} — 检查对象是否存在</li>
 *   <li>{@code PUT|POST /{name}/{key}} — 上传对象（请求体为原始文件内容）</li>
 *   <li>{@code DELETE /{name}/{key}} — 删除对象（目录递归删除）</li>
 *   <li>{@code POST /{name}?action=mkdir|mkfile|rename|copy|move|unzip} — 文件管理动作（KodExplorer 风格）</li>
 *   <li>{@code GET /{name}?action=zip&sources=} — 打包下载 ZIP</li>
 *   <li>{@code POST /{name}?action=setConfig&token=} — 管理员动态修改行为开关（实时生效）</li>
 * </ul>
 *
 * <p>行为开关由 {@link FileStorageSetting} 控制：
 * 打开download（允许附件下载）、打开preview（允许内联预览）、打开范围（支持分段下载）、
 * 打开watermark（附加水印响应头）、打开远程文件（允许以 http(s) 开头的 键 远程拉取）。</p>
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
     * 配置热更新回调（用于把私有桶变更同步给 OAuth 鉴权过滤器）
     */
    private volatile java.util.function.Consumer<FileStorageSetting> configListener;

    /**
     * SPI 预览提供者缓存（懒加载，类路径 存在 utils-support-filestorage-starter 时可用）
     */
    private volatile List<FileStoragePreviewProvider> previewProviderCache;

    /**
     * 并发去重：键 → 正在进行的 SPI 预览 期货，避免同一文件重复转换
     */
    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> inflightMap = new ConcurrentHashMap<>();

    /**
     * 一次性访问链接：令牌 → 条目（阅后即焚）
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
     * 文件管理动作：新建目录
     */
    private static final String ACTION_MKDIR = "mkdir";

    /**
     * 文件管理动作：新建文件
     */
    private static final String ACTION_MKFILE = "mkfile";

    /**
     * 文件管理动作：重命名
     */
    private static final String ACTION_RENAME = "rename";

    /**
     * 文件管理动作：复制（粘贴）
     */
    private static final String ACTION_COPY = "copy";

    /**
     * 文件管理动作：剪切（移动粘贴）
     */
    private static final String ACTION_MOVE = "move";

    /**
     * 文件管理动作：打包下载
     */
    private static final String ACTION_ZIP = "zip";

    /**
     * 文件管理动作：解压
     */
    private static final String ACTION_UNZIP = "unzip";

    /**
     * 文件管理动作：管理员动态修改行为配置（实时生效）
     */
    private static final String ACTION_SET_CONFIG = "setConfig";

    /**
     * 动作配置令牌参数名（setConfig 管理员校验）
     */
    private static final String PARAM_TOKEN = "token";

    /** 图片处理期间暂存当前请求（FileStorageFileSetting SPI 只接受 ServerRequest） */
    private ServerRequest currentOpsRequest;

    /**
     * 图片滤镜 SPI 接口及已知子接口类名（具体滤镜分散注册在多级接口下，
     * 用 Class.forName 加载以避免 utils-support-image-starter 缺失时编译/类加载失败）
     */
    private static final String[] IMAGE_FILTER_SPI_CLASSES = {
            "com.chua.common.support.image.filter.ImageFilter",
            "com.chua.image.support.filter.AbstractImageFilter",
            "com.chua.image.support.filter.AbstractImagePointFilter",
            "com.chua.image.support.filter.ImageGaussianBlurFilter"
    };

    /**
     * 列表查询上限（管理动作递归遍历使用）
     */
    private static final int LIST_LIMIT = Integer.MAX_VALUE;

    /**
     * 复制/移动多源键分隔符（换行符，合法文件名不可能包含）
     */
    private static final String SOURCES_SEPARATOR = "\n";

    /**
     * 同名冲突时追加的副本后缀（KodExplorer / Windows 资源管理器风格）
     */
    private static final String COPY_SUFFIX = " - 副本";

    /**
     * 多对象打包下载的默认压缩包名
     */
    private static final String DEFAULT_ZIP_NAME = "download.zip";

    /**
     * ZIP 媒体类型
     */
    private static final String CONTENT_TYPE_ZIP = "application/zip;charset=UTF-8";

    /**
     * 空字节内容（新建空文件/目录占位）
     */
    private static final byte[] EMPTY_BYTES = new byte[0];

    /**
     * 系统配置访问令牌（每次服务启动随机生成，访问 /配置 时校验）
     */
    private final String token;

    /**
     * 创建 文件storage服务端过滤器 实例
     * @param setting setting
     */
    public FileStorageServerFilter(FileStorageSetting setting) {
        this.setting = setting == null ? FileStorageSetting.builder().build() : setting;
        this.token = generateToken();
        log.info("文件服务器管理员令牌: {}（系统配置弹窗/配置页使用，每次启动随机生成）", token);
    }

    /**
     * 注册配置热更新回调（setConfig 成功后触发，用于同步 OAuth 鉴权过滤器的私有桶）。
     *
     * @param configListener 回调（入参为最新配置）
     */
    public void setConfigListener(java.util.function.Consumer<FileStorageSetting> configListener) {
        this.configListener = configListener;
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
    /**
     * 执行过滤
    */
    public void doFilter(ServerRequest request, ServerResponse response, com.chua.common.support.network.server.filter.ServerFilterChain chain) throws Exception {
        // 简单透传
        chain.doFilter(request, response);
    }

    @Override
    /**
     * 绑定路径模式：空 表示 Access 过滤器，每次请求都触发
    */
    public String supportPath() {
        return null;
    }

    @Override
    /**
     * 过滤器执行顺序：数值越小越先执行
    */
    public int getOrder() {
        return 100;
    }

    @Override
    /**
     * 响应式执行过滤
    */
    public CompletionStage<Void> doFilter(ServerRequest request, ServerResponse response, ReactiveFilterChain chain) {
        // CORS 由框架 CorsFilter 统一负责（其 order 最小，最先执行）：开关与白名单来自
        // plugin.filesystem.storage-server.cors.*（默认开启、允许携带凭据），
        // 由 FileStorageServerLauncher 注入。此处不再重复设置，避免两处真相。
        // 这里只兜底处理「非预检」的裸 OPTIONS（真正的预检带 Access-Control-Request-Method，
        // 已在框架 CorsFilter 里 204 结束，不会走到这里）。
        if (request.getMethod() == HttpMethod.OPTIONS) {
            response.setStatus(204)
                    .setHeader("Allow", "GET, HEAD, POST, PUT, DELETE, OPTIONS")
                    .end();
            return CompletableFuture.completedFuture(null);
        }

        String path = request.getPath();
        List<String> segments = splitPath(path);
        if (segments.isEmpty()) {
            return passthrough(request, response, chain);
        }
        String bucket = segments.getFirst();

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

        // 预览页同源静态资源：各 preview provider 生成的 HTML 引用 /preview-vendor/**
        // （Univer 表格 / pdf.js / highlight.js / mammoth 等前端库），按约定由本服务以
        // classpath:/static/preview-vendor 同源提供，避免依赖公网 CDN。
        // 必须放在下面"未注册存储放行"之前——否则会被当成 bucket=preview-vendor 一路 passthrough
        // 出去，最终 404，预览整条链路（样式/脚本全部加载不到）就废了。
        if (PREVIEW_VENDOR_PREFIX.equalsIgnoreCase(bucket)) {
            return servePreviewVendor(request, response, path);
        }

        FileStorage storage = storageMap.get(bucket);
        if (storage == null) {
            // 未注册的存储路径放行，交由后续链路处理
            return passthrough(request, response, chain);
        }

 // 远程文件透传：路径形如 /{bucket}/http:/主机/路径 或 /{bucket}/https:/主机/路径
        // （框架返回的路径已解码，远程地址被拆散为多段，此处按前缀重组还原）
        if (setting.isOpenRemoteFile() && segments.size() > 2
                && ("http:".equals(segments.get(1)) || "https:".equals(segments.get(1)))) {
            String remoteUrl = segments.get(1) + "//" + String.join("/", segments.subList(2, segments.size()));
            return handleRemoteFile(request, response, remoteUrl);
        }

 // 提取对象 键（第二段之后的所有路径段），并做 URL 解码与穿越防护
        String rawKey = segments.size() > 1 ? String.join("/", segments.subList(1, segments.size())) : "";
        String key = sanitizeKey(rawKey);
        if (key == null) {
            return error(response, 400, "Invalid object key");
        }

        // 文件管理动作（KodExplorer 风格）：mkdir/mkfile/rename/copy/move/zip/unzip
        // 参数走 query：POST /{bucket}?action=mkdir、GET /{bucket}?action=zip&sources=...
        Map<String, String> params = request.getParams();
        String action = params == null ? null : params.get("action");
        if (action != null && !action.isEmpty()) {
            return handleAction(storage, request, response, action, params);
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
     * 处理系统配置查看请求：获取 /配置?令牌=xxx。
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
     * 渲染配置页登录入口（未携带 令牌 时）。
     * @param response 响应
     * @return render配置login的结果
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
     * @return 构建配置page的结果
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
        appendConfigRow(sb, "showFormat", String.valueOf(setting.isShowFormat()));
        appendConfigRow(sb, "showSize", String.valueOf(setting.isShowSize()));
        appendConfigRow(sb, "showTime", String.valueOf(setting.isShowTime()));
        appendConfigRow(sb, "imageFilter", setting.getImageFilter());
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

    /**
     * 追加配置row。
     * @param sb sb
     * @param key 键
     * @param value 值
     */
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
     * 处理访问链接。
     *
     * <p>创建参数：{@code POST /ot?bucket=xxx&key=yyy&type=once|expiry&expires=600}</p>
     * <ul>
     *   <li>{@code type=once}（默认）— 一次性，访问即销毁，无论过期时间</li>
     *   <li>{@code type=expiry} — 限时链接，有效期内可多次访问，到期失效</li>
     *   <li>{@code expires} — 有效期（秒），默认 {@link #ONE_TIME_TTL_MILLIS}</li>
     * </ul>
     * <p>消费：{@code GET /ot/{token}} — 返回文件内容（一次性模式同时销毁）</p>
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
                // 创建链接
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
                ExistObjectRequest existReq = ExistObjectRequest.builder()
                        .fileName(key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key)
                        .filePath(key.contains("/") ? key.substring(0, key.lastIndexOf('/')) : "")
                        .build();
                ExistObjectResult exist = storage.existObject(existReq);
                if (!exist.isExists()) {
                    return error(response, 404, "Object not found: " + key);
                }
 // 读取 类型 和 expires
                String type = params == null ? "once" : params.getOrDefault("type", "once");
                boolean once = !"expiry".equalsIgnoreCase(type);
                long ttl = parseOneTimeExpiry(params == null ? null : params.get("expires"));
                String token = generateOneTimeToken();
                oneTimeMap.put(token, new OneTimeEntry(bucket, key, System.currentTimeMillis(), ttl, once));
                maybeCleanOneTime();

                String json = "{\"token\":\"" + token + "\",\"url\":\"/ot/" + token + "\""
                        + ",\"type\":\"" + (once ? "once" : "expiry") + "\""
                        + ",\"expires\":\"" + ttl + "ms\"}";
                response.setStatus(200)
                        .setContentType("application/json;charset=UTF-8")
                        .setBody(json)
                        .end();
                return CompletableFuture.completedFuture(null);
            }
            if (method == HttpMethod.GET && segments.size() == 2) {
                // 消费链接
                String token = segments.get(1);
                OneTimeEntry entry = oneTimeMap.get(token);
                if (entry == null) {
                    return error(response, 410, "Link expired or already consumed");
                }
                // 检查过期
                if (System.currentTimeMillis() - entry.createTime > entry.ttlMillis) {
                    oneTimeMap.remove(token);
                    return error(response, 410, "Link expired");
                }
                // 一次性模式：访问后立即销毁
                if (entry.once) {
                    oneTimeMap.remove(token);
                }
 // 限时模式：不移除，保持 映射 中供多次访问

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
                        .setHeader("X-OneTime", entry.once ? "once" : "expiry")
                        .setHeader("Cache-Control", "no-store, no-cache, must-revalidate")
                        .setContentType(resolveContentType(fileName))
                        .setBody(data)
                        .end();
                return CompletableFuture.completedFuture(null);
            }
            return error(response, 405, "Method Not Allowed");
        } catch (Exception e) {
            log.error("链接处理失败: {}", e.getMessage(), e);
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
     * 链接条目（支持一次性 / 限时两种模式）。
     */
    private static final class OneTimeEntry {
        /**
         * 存储桶
        */
        final String bucket;
        /**
         * 对象 键
        */
        final String key;
        /**
         * 创建时间戳
        */
        final long createTime;
        /**
         * 自定义过期时长（毫秒）
        */
        final long ttlMillis;
        /**
         * true=一次性（访问即销毁），false=限时（到期失效，可多次访问）
        */
        final boolean once;

        OneTimeEntry(String bucket, String key, long createTime) {
            this(bucket, key, createTime, ONE_TIME_TTL_MILLIS, true);
        }

        OneTimeEntry(String bucket, String key, long createTime, long ttlMillis) {
            this(bucket, key, createTime, ttlMillis, true);
        }

        OneTimeEntry(String bucket, String key, long createTime, long ttlMillis, boolean once) {
            this.bucket = bucket;
            this.key = key;
            this.createTime = createTime;
            this.ttlMillis = ttlMillis;
            this.once = once;
        }
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
     * @param request 请求
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
        Map<String, String> params = request.getParams();
 // ?配置（标志式）→ 返回 bucket 公开配置 JSON
        if (params != null && params.containsKey("config") && !"false".equalsIgnoreCase(params.get("config"))) {
            return writeBucketConfig(bucket, response);
        }
        // ?tree=true → 目录树 JSON（KodExplorer 式左侧文件树）
        if (params != null && ("true".equalsIgnoreCase(params.get("tree")))) {
            FileStorage storage = storageMap.get(bucket);
            if (storage == null) {
                return error(response, 404, "Storage not found: " + bucket);
            }
            return writeTree(storage, bucket, response);
        }
        // ?format=capabilities（Vue 管理配置：真实 SPI 能力列表）
        if (params != null && "capabilities".equalsIgnoreCase(params.get("format"))) {
            return writeCapabilities(response);
        }
        // ?format=json（Vue API 目录列表）
        if (params != null && "json".equalsIgnoreCase(params.get("format"))) {
            return writeJsonList(bucket, dir, result, response);
        }
        // 浏览器导航 → 按布局类型分流：SYSTEM=Vue 独立构建；SIMPLE=服务端简单列表
        return renderLayoutPage(bucket, dir, result.getMetadata(), response);
    }

    /**
     * 执行 URL 上的文件处理操作。
     *
     * <p>顺序：自动旋正 → 裁剪 → 旋转 → 翻转 → 缩放 → 灰度 → 模糊 → 锐化 → 水印 → 质量编码。
     * 每步独立降级：失败只 warn 并跳过；空结果视为「这步把图画没了」而保留上一步，
     * 绝不返回 0 字节。</p>
     *
     * @param data     原始字节
     * @param fileName 文件名
     * @param request  请求
     * @return 处理后的字节；非图片或无操作时返回 {@code null}（交回原流程）
     */
    private byte[] applyFileOperations(byte[] data, String fileName, ServerRequest request) {
        String ext = simpleExtOf(fileName);
        if (ext == null || !isImageExt(ext)) {
            return null;
        }
        FileOperationSetting ops = parseFileOps();
        if (ops == null || !ops.hasOperation()) {
            return null;
        }
        ImageOperation imageOperation = ServiceProvider.of(ImageOperation.class).getExtension();
        if (imageOperation == null) {
            log.warn("[FileStorage] 未找到 ImageOperation SPI，跳过图片处理");
            return null;
        }
        String format = opOutputFormat(fileName, request);
        if (format == null) {
            return null;
        }
        byte[] cur = data;
        try {
            if (Boolean.TRUE.equals(ops.getAutoOrient())) {
                cur = keep(cur, imageOperation.autoOrient(cur, format).block());
            }
            byte[] c1 = cropOf(imageOperation, cur, format, ops.getCrop());
            if (c1 != null) {
                cur = c1;
            }
            if (ops.getRotate() != null) {
                cur = keep(cur, imageOperation.rotate(cur, format, ops.getRotate()).block());
            }
            if (ops.getFlip() != null && !ops.getFlip().isBlank()) {
                cur = keep(cur, imageOperation.flip(cur, format, ops.getFlip()).block());
            }
            byte[] c2 = resizeOf(imageOperation, cur, format, ops.getSize());
            if (c2 != null) {
                cur = c2;
            }
            if (Boolean.TRUE.equals(ops.getGrayscale())) {
                cur = keep(cur, imageOperation.filter(cur, format, "gray", null).block());
            }
            if (ops.getBlur() != null && ops.getBlur() > 0) {
                ImageOperation.FilterParams p = ImageOperation.FilterParams.builder().radius(ops.getBlur()).build();
                cur = keep(cur, imageOperation.filter(cur, format, "blur", p).block());
            }
            if (ops.getSharpen() != null && ops.getSharpen() > 0) {
                ImageOperation.FilterParams p = ImageOperation.FilterParams.builder().radius(ops.getSharpen()).build();
                cur = keep(cur, imageOperation.filter(cur, format, "sharpen", p).block());
            }
            if (ops.getWatermarkText() != null && !ops.getWatermarkText().isBlank()) {
                ImageOperation.WatermarkParams p = ImageOperation.WatermarkParams.builder()
                        .text(ops.getWatermarkText()).opacity(0.5f).position("center").build();
                cur = keep(cur, imageOperation.watermark(cur, format, p).block());
            }
        } catch (Exception e) {
            log.warn("[FileStorage] 图片处理失败，回退原图: {}", e.getMessage());
            return null;
        }
        return cur;
    }

    private byte[] keep(byte[] before, byte[] after) {
        return (after == null || after.length == 0) ? before : after;
    }

    private byte[] resizeOf(ImageOperation op, byte[] bytes, String format, String size) {
        if (size == null || size.isBlank()) {
            return null;
        }
        String expr = size.trim();
        Double scale = null;
        Integer width = null;
        Integer height = null;
        if (expr.endsWith("%")) {
            try {
                scale = Double.parseDouble(expr.substring(0, expr.length() - 1)) / 100.0d;
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            String[] parts = expr.split("[xX*\\s]+");
            try {
                if (parts.length >= 2) {
                    width = Integer.valueOf(parts[0]);
                    height = Integer.valueOf(parts[1]);
                } else {
                    width = Integer.valueOf(parts[0]);
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return op.resize(bytes, format, width, height, scale).block();
    }

    private byte[] cropOf(ImageOperation op, byte[] bytes, String format, String crop) {
        if (crop == null || crop.isBlank()) {
            return null;
        }
        String[] parts = crop.split("[,\\s]+");
        if (parts.length < 4) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int w = Integer.parseInt(parts[2]);
            int h = Integer.parseInt(parts[3]);
            if (w <= 0 || h <= 0) {
                return null;
            }
            return op.crop(bytes, format, x, y, w, h).block();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private FileOperationSetting parseFileOps() {
        try {
            return ServiceProvider.of(FileStorageFileSetting.class).getNewExtension("jdk").parse(currentOpsRequest);
        } catch (Exception e) {
            return null;
        }
    }

    /** 常用图片扩展名（决定是否走图片处理管线） */
    private static boolean isImageExt(String ext) {
        return switch (ext) {
            case "jpg", "jpeg", "png", "gif", "bmp", "webp", "wbmp" -> true;
            default -> false;
        };
    }

    private static String normFormat(String f) {
        if (f == null || f.isBlank()) {
            return null;
        }
        String s = f.trim().toLowerCase(Locale.ROOT);
        if ("original".equals(s) || "none".equals(s)) {
            return null;
        }
        s = switch (s) {
            case "jpg" -> "jpeg";
            case "tif" -> "tiff";
            default -> s;
        };
        return ImageIO.getImageWritersByFormatName(s).hasNext() ? s : null;
    }

    private String opOutputFormat(String fileName, ServerRequest request) {
        Map<String, String> params = request.getParams();
        String requested = params == null ? null : normFormat(params.get("format"));
        String ext = simpleExtOf(fileName);
        return requested != null ? requested : normFormat(ext == null ? "jpeg" : ext);
    }

    /**
     * 递归构建目录树 JSON（深度受限，避免超大桶卡死）。
     *
     * <p>{@code GET /{bucket}?tree=true} — 返回全量目录树：
     * {@code [{name,path,children:[...]}]}，仅含目录。</p>
     * @param storage storage
     * @param bucket bucket
     * @param response 响应
     * @return 写入树的结果
     */
    private CompletionStage<Void> writeTree(FileStorage storage, String bucket, ServerResponse response) {
        StringBuilder root = new StringBuilder();
        root.append("[");
        buildTreeBranch(storage, bucket, "", root, 0);
        root.append("]");
        response.setStatus(200)
                .setContentType("application/json;charset=UTF-8")
                .setBody(root.toString())
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 递归构建树的一个分支（追加到 sb）。
     * @param storage storage
     * @param bucket bucket
     * @param parentPath 父路径
     * @param sb sb
     * @param depth 深度
     */
    private void buildTreeBranch(FileStorage storage, String bucket, String parentPath, StringBuilder sb, int depth) {
        if (depth > 12) {
            return;
        }
        ListObjectRequest req = ListObjectRequest.builder()
                .filePath(parentPath)
                .limit(500)
                .build();
        ListObjectResult list = storage.listObject(req);
        if (list.getResultCode() != ObjectResult.ResultCode.SUCCESS || list.getMetadata() == null) {
            return;
        }
        boolean first = true;
        for (Metadata m : list.getMetadata()) {
            if (!m.isDirectory()) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            first = false;
            String childPath = parentPath == null || parentPath.isEmpty()
                    ? m.getName() : parentPath + "/" + m.getName();
            sb.append("{\"name\":\"").append(escapeJson(m.getName())).append("\"")
                    .append(",\"path\":\"").append(escapeJson(childPath)).append("\"")
                    .append(",\"children\":[");
            buildTreeBranch(storage, bucket, childPath, sb, depth + 1);
            sb.append("]}");
        }
    }

    /**
     * 返回 bucket 公开配置 JSON（供 Vue 前端渲染用，无需 令牌）。
     *
     * <p>包含存储类型、功能开关、布局类型等，不含敏感字段（如完整 token）。
     * 用法：{@code GET /{bucket}?格式化=配置}</p>
     * @param bucket bucket
     * @param response 响应
     * @return 写入bucket配置的结果
     */
    private CompletionStage<Void> writeBucketConfig(String bucket, ServerResponse response) {
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
                + ",\"showFormat\":" + setting.isShowFormat()
                + ",\"showSize\":" + setting.isShowSize()
                + ",\"showTime\":" + setting.isShowTime()
                + ",\"imageFilter\":\"" + escapeJson(setting.getImageFilter() == null ? "NONE" : setting.getImageFilter()) + "\""
                + ",\"private\":" + (setting.getPrivateBuckets() != null && setting.getPrivateBuckets().contains(bucket))
                + ",\"privateBuckets\":" + privateBucketsJson()
                + ",\"oneTimeTtlMs\":" + ONE_TIME_TTL_MILLIS
                + "}";
        response.setStatus(200)
                .setContentType("application/json;charset=UTF-8")
                .setBody(json)
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 私有桶集合的 JSON 数组文本。
     *
     * @return JSON 数组，如 ["local"]
     */
    private String privateBucketsJson() {
        java.util.Set<String> buckets = setting.getPrivateBuckets();
        if (buckets == null || buckets.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String bucket : buckets) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(escapeJson(bucket)).append('"');
            first = false;
        }
        return sb.append(']').toString();
    }

    /**
     * 输出 SPI 真实支持的能力列表（供 Vue 管理配置动态渲染）。
     *
     * <p>用法：{@code GET /{bucket}?format=capabilities}，返回结构：</p>
     * <pre>{@code
     * {
     *   "fileSettingCapabilities": [{"spi":"JDK","capabilities":["size","format"]}],
     *   "filterSettingCapabilities": [{"spi":"JDK","capabilities":["grayscale"]}],
     *   "imageFilters": [{"name":"GRAY","label":"灰度化图像滤镜"}]
     * }
     * }</pre>
     *
     * <p>三类能力均通过 ServiceLoader SPI 实时发现，前端不硬编码。</p>
     *
     * @param response 响应
     * @return 写入能力列表的结果
     */
    private CompletionStage<Void> writeCapabilities(ServerResponse response) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{");
        // 文件级 URL 参数能力（FileStorageFileSetting SPI）
        sb.append("\"fileSettingCapabilities\":[");
        appendFileSettingGroups(sb);
        sb.append("]");
        // 全局滤镜能力（FileStorageFilterSetting SPI）
        sb.append(",\"filterSettingCapabilities\":[");
        appendFilterSettingGroups(sb);
        sb.append("]");
        // 图片滤镜实现（ImageFilter SPI 及子 SPI，含 @SpiDescribe 中文标签）
        sb.append(",\"imageFilters\":[");
        appendImageFilters(sb);
        sb.append("]");
        sb.append("}");
        response.setStatus(200)
                .setContentType("application/json;charset=UTF-8")
                .setBody(sb.toString())
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 追加文件级 URL 参数能力分组（一个 SPI 实现对应一个分组）。
     *
     * @param sb JSON 构造器
     */
    private void appendFileSettingGroups(StringBuilder sb) {
        try {
            ServiceProvider<FileStorageFileSetting> provider = ServiceProvider.of(FileStorageFileSetting.class);
            Set<String> names = provider.getExtensions();
            boolean firstGroup = true;
            for (String name : names) {
                FileStorageFileSetting instance = provider.getExtension(name);
                if (instance == null) {
                    continue;
                }
                if (!firstGroup) {
                    sb.append(",");
                }
                firstGroup = false;
                sb.append("{\"spi\":\"").append(escapeJson(name)).append("\",\"capabilities\":[");
                appendStringArray(sb, instance.capabilities());
                sb.append("]}");
            }
        } catch (Throwable t) {
            log.warn("FileStorageFileSetting SPI 能力加载失败（缺少 utils-support-filestorage-starter 依赖时忽略）: {}",
                    t.getMessage());
        }
    }

    /**
     * 追加全局滤镜能力分组（一个 SPI 实现对应一个分组）。
     *
     * @param sb JSON 构造器
     */
    private void appendFilterSettingGroups(StringBuilder sb) {
        try {
            ServiceProvider<FileStorageFilterSetting> provider = ServiceProvider.of(FileStorageFilterSetting.class);
            Set<String> names = provider.getExtensions();
            boolean firstGroup = true;
            for (String name : names) {
                FileStorageFilterSetting instance = provider.getExtension(name);
                if (instance == null) {
                    continue;
                }
                if (!firstGroup) {
                    sb.append(",");
                }
                firstGroup = false;
                sb.append("{\"spi\":\"").append(escapeJson(name)).append("\",\"capabilities\":[");
                appendStringArray(sb, instance.capabilities());
                sb.append("]}");
            }
        } catch (Throwable t) {
            log.warn("FileStorageFilterSetting SPI 能力加载失败（缺少 utils-support-filestorage-starter 依赖时忽略）: {}",
                    t.getMessage());
        }
    }

    /**
     * 追加图片滤镜实现列表。
     *
     * <p>具体滤镜分散注册在 {@link #IMAGE_FILTER_SPI_CLASSES} 列出的多级 SPI 接口下，
     * 逐级 ServiceProvider 发现并按扩展名去重；标签优先取 {@link SpiDescribe} 的中文描述。</p>
     *
     * @param sb JSON 构造器
     */
    private void appendImageFilters(StringBuilder sb) {
        Set<String> seenFilters = resolveImageFilterNames();
        int index = 0;
        for (String name : seenFilters) {
            if (index > 0) {
                sb.append(",");
            }
            index++;
            sb.append("{\"name\":\"").append(escapeJson(name)).append("\"");
            sb.append(",\"label\":\"").append(escapeJson(resolveFilterLabel(name))).append("\"}");
        }
    }

    /**
     * 收集全部图片滤镜 SPI 扩展名（跨 {@link #IMAGE_FILTER_SPI_CLASSES} 多级接口去重）。
     *
     * @return 扩展名集合（SPI 名称统一为大写，保持发现顺序）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Set<String> resolveImageFilterNames() {
        Set<String> seenFilters = new LinkedHashSet<>();
        for (String spiClassName : IMAGE_FILTER_SPI_CLASSES) {
            try {
                Class<?> spiType = ReflectUtils.forName(spiClassName);
                if (spiType == null) {
                    log.debug("图片滤镜 SPI 接口未找到[{}]", spiClassName);
                    continue;
                }
                ServiceProvider provider = ServiceProvider.of((Class) spiType);
                seenFilters.addAll(provider.getExtensions());
            } catch (Throwable t) {
                log.warn("图片滤镜 SPI 实现加载失败[{}]: {}", spiClassName, t.getMessage());
            }
        }
        return seenFilters;
    }

    /**
     * 解析滤镜中文标签：在多级 SPI 接口下查找该扩展名对应的实现实例，
     * 优先读取实现类上的 {@link SpiDescribe} 注解，缺失时回退为扩展名。
     *
     * @param name 扩展名
     * @return 中文标签或扩展名
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String resolveFilterLabel(String name) {
        for (String spiClassName : IMAGE_FILTER_SPI_CLASSES) {
            try {
                Class<?> spiType = ReflectUtils.forName(spiClassName);
                if (spiType == null) {
                    continue;
                }
                ServiceProvider provider = ServiceProvider.of((Class) spiType);
                Object instance = provider.getExtension(name);
                if (instance != null) {
                    SpiDescribe describe = instance.getClass().getAnnotation(SpiDescribe.class);
                    if (describe != null && !describe.value().isEmpty()) {
                        return describe.value();
                    }
                    return name;
                }
            } catch (Throwable t) {
                log.debug("滤镜标签解析失败[{}/{}]: {}", spiClassName, name, t.getMessage());
            }
        }
        return name;
    }

    /**
     * 向 JSON 构造器追加字符串数组元素（不含外层方括号，null 安全）。
     *
     * @param sb JSON 构造器
     * @param values 字符串列表
     */
    private void appendStringArray(StringBuilder sb, List<String> values) {
        if (values == null) {
            return;
        }
        boolean first = true;
        for (String value : values) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(escapeJson(value)).append("\"");
        }
    }

    /**
     * 返回文件级公开配置 JSON（`?config=true`，与 preview/download 平级）。
     *
     * <p>包含文件名、大小、内容类型、预览类型、功能开关等，不读文件体。
     * 用法：{@code GET /{bucket}/{键}?配置=true}</p>
     * @param bucket bucket
     * @param key 键
     * @param result 结果
     * @param response 响应
     * @return 写入对象配置的结果
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
                + ",\"private\":" + (setting.getPrivateBuckets() != null && setting.getPrivateBuckets().contains(bucket))
                + "}";
        response.setStatus(200)
                .setContentType("application/json;charset=UTF-8")
                .setBody(json)
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 根据扩展名推断预览类型（与前端 种类的 保持一致）。
     * @param ext ext
     * @return resolvepreview种类的结果
     */
    private static String resolvePreviewKind(String ext) {
        String e = ext == null ? "" : ext.toLowerCase();
        if (e.matches("png|jpg|jpeg|gif|svg|webp|bmp|ico")) {
            return "image";
        }
        if (e.matches("txt|log|json|xml|html|css|js|ts|md|yaml|yml|csv")) {
            return "text";
        }
        if ("pdf".equals(e)) {
            return "pdf";
        }
        if (e.matches("xlsx|xls|docx|doc|pptx|ppt|odt|ods|odp|vsd|dwg|dgn|step|stp|iges|igs|brep|xlsb|xlt|xltx|xltm|xlam|xlsxml|dotx|dotm|potx|potm|xmind|rtf|epub")) {
            return "office";
        }
        if (e.matches("mp4|webm|ogg|mov|avi|mkv|flv|wmv|m4v|3gp")) {
            return "video";
        }
        if (e.matches("mp3|wav|flac|m4a")) {
            return "audio";
        }
        if (e.matches("ttf|otf|woff|woff2|eot")) {
            return "font";
        }
        if (e.matches("epub|rtf|eml|ics|vcf|fbx|blend|shp")) {
            return "text";
        }
        return "unknown";
    }

    /**
     * 输出 JSON 格式的目录列表（供 Vue API 使用，不渲染 HTML 页面）。
     * @param bucket bucket
     * @param dir dir
     * @param result 结果
     * @param response 响应
     * @return 写入json列表的结果
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
                if (i > 0) {
                    json.append(",");
                }
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
     * 写出压缩包条目清单 JSON（{@code ?format=archive}）。
     *
     * <p>与 {@code ?preview=true} 的服务端渲染是<b>并列</b>的两条通道而非替代关系：
     * 服务端渲染把整个浏览器页面交给 iframe（独立文档，拿不到主应用的
     * Element Plus / koi 主题变量）；本接口只回数据，由主应用内的 Vue 组件
     * 渲染，才能真正跟随系统主题与暗色模式。</p>
     *
     * <p>结构：</p>
     * <pre>{@code
     * {
     *   "name": "施工图3-2-804.zip",
     *   "extension": "zip",
     *   "archiveSize": 2867200,
     *   "fileCount": 15, "dirCount": 2, "totalSize": 8912896,
     *   "compressRate": 68,
     *   "entries": [
     *     {"path":"现场照片/IMG_1.jpg","name":"IMG_1.jpg","directory":false,
     *      "size":243558,"compressedSize":201234,"modified":1753936260000}
     *   ]
     * }
     * }</pre>
     *
     * @param fileName 压缩包文件名
     * @param data     压缩包字节内容
     * @param response 响应
     * @return 异步阶段
     */
    private CompletionStage<Void> writeArchiveEntries(String fileName, byte[] data, ServerResponse response) {
        String ext = simpleExtOf(fileName);
        ArchivePreviewProvider.ArchiveScan scan;
        try {
            scan = ArchivePreviewProvider.scan(data, ext);
        } catch (Exception e) {
            log.warn("压缩包条目解析失败[{}]: {}", fileName, e.getMessage());
            return error(response, 422, "Unprocessable Entity: 压缩包解析失败");
        }

        long archiveSize = data.length;
        long totalSize = scan.totalSize();
        // 压缩率 = 压缩后体积 / 解压后总体积（%），是压缩包语境下的通行口径。
        // totalSize 为 0（空包）时无法计算，如实回 0，不做除零兜底成 100%。
        int compressRate = totalSize > 0 ? (int) Math.round(archiveSize * 100.0d / totalSize) : 0;

        StringBuilder json = new StringBuilder(1024 + scan.entries().size() * 160);
        json.append("{\"name\":\"").append(escapeJson(fileName)).append("\"")
                .append(",\"extension\":\"").append(escapeJson(ext)).append("\"")
                .append(",\"archiveSize\":").append(archiveSize)
                .append(",\"fileCount\":").append(scan.fileCount())
                .append(",\"dirCount\":").append(scan.dirCount())
                .append(",\"totalSize\":").append(totalSize)
                .append(",\"compressRate\":").append(compressRate)
                .append(",\"entries\":[");
        List<ArchivePreviewProvider.ArchiveEntryItem> entries = scan.entries();
        for (int i = 0; i < entries.size(); i++) {
            ArchivePreviewProvider.ArchiveEntryItem item = entries.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{\"path\":\"").append(escapeJson(item.path())).append("\"")
                    .append(",\"name\":\"").append(escapeJson(item.name())).append("\"")
                    .append(",\"directory\":").append(item.directory())
                    .append(",\"size\":").append(Math.max(0L, item.size()))
                    // compressedSize 为 -1 表示归档未记录该值；原样透出由前端显示为 "-"
                    .append(",\"compressedSize\":").append(item.compressedSize())
                    .append(",\"modified\":").append(item.modified())
                    .append("}");
        }
        json.append("]}");
        response.setStatus(200)
                .setHeader("X-FileStorage-Preview", "archive-json")
                .setContentType("application/json;charset=UTF-8")
                .setBody(json.toString())
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 写出 CAD 图纸信息 JSON（{@code ?format=cad}）。
     *
     * <p>与 {@code ?preview=true} 的服务端渲染并列的第二条通道：后者把整张页面交给
     * iframe（独立文档，读不到主应用的 Element Plus / koi 主题变量）；本接口只回
     * 结构化数据 + 内联 SVG，由主应用内的 Vue 面板渲染，从而跟随系统主题与暗色模式。</p>
     *
     * <p>DXF 返回完整图形（纯 Java 解析转 SVG）；DWG 只返回头部信息——其图元几何
     * 无法在 Java 内解析，这不是实现取舍而是格式的硬约束（Autodesk 私有二进制格式）。
     * 两条路径结构不同，故用 {@code renderable} 明确告知前端能否出图。</p>
     *
     * <p>结构：</p>
     * <pre>{@code
     * {
     *   "name": "施工图.dxf", "extension": "dxf",
     *   "size": 204800, "renderable": true,
     *   "svg": "<svg .../>",          // 仅 renderable=true 时有值
     *   "acadVersion": "AC1027", "units": "米",
     *   "entityCount": 5, "layerCount": 4, "layers": {"0": 2, "WALL": 1},
     *   "width": 100, "height": 50,
     *   "unsupported": ["INSERT"],   // 跳过的实体类型，如实告知用户
     *   "versionLabel": "AutoCAD 2013-2017", "versionCode": "AC1027"
     * }
     * }</pre>
     *
     * @param fileName 文件名
     * @param data     文件字节
     * @param response 响应
     * @return 异步阶段
     */
    private CompletionStage<Void> writeCadInfo(String fileName, byte[] data, ServerResponse response) {
        String ext = simpleExtOf(fileName);
        StringBuilder json = new StringBuilder(4096);
        json.append("{\"name\":\"").append(escapeJson(fileName)).append("\"")
                .append(",\"extension\":\"").append(escapeJson(ext)).append("\"")
                .append(",\"size\":").append(data.length);

        try {
            if ("dxf".equals(ext)) {
                appendDxfJson(json, data);
            } else if ("dwg".equals(ext)) {
                appendDwgJson(json, data);
            } else {
                json.append(",\"renderable\":false")
                        .append(",\"error\":\"暂不支持的 CAD 格式：.").append(escapeJson(ext)).append("\"}");
            }
        } catch (Exception e) {
            log.warn("CAD 图纸解析失败[{}]: {}", fileName, e.getMessage());
            return error(response, 422, "Unprocessable Entity: CAD 图纸解析失败");
        }

        json.append("}");
        response.setStatus(200)
                .setHeader("X-FileStorage-Preview", "cad-json")
                .setContentType("application/json;charset=UTF-8")
                .setBody(json.toString())
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 追加 DXF 字段（图形 + 统计）
     *
     * @param json  JSON 构造器
     * @param data  DXF 字节
     * @throws IOException 解析失败
     */
    private void appendDxfJson(StringBuilder json, byte[] data) throws IOException {
        DxfDocument doc = DxfParser.create().parse(data);
        String svg = DxfSvgRenderer.create().render(doc, 1600);
        json.append(",\"renderable\":true")
                // SVG 以 JSON 字符串内联：省一次请求，且描边走 currentColor，
                // 前端只需把 SVG 塞进容器即可继承主题色（暗色模式零成本适配）
                .append(",\"svg\":\"").append(escapeJson(svg)).append("\"")
                .append(",\"acadVersion\":\"").append(escapeJson(doc.header().acadVersion())).append("\"")
                .append(",\"units\":\"").append(escapeJson(doc.header().unitsLabel())).append("\"")
                .append(",\"entityCount\":").append(doc.entities().size())
                .append(",\"layerCount\":").append(doc.layers().size())
                .append(",\"layers\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : doc.layers().entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append("\"").append(escapeJson(e.getKey())).append("\":").append(e.getValue());
        }
        json.append("}")
                .append(",\"width\":").append(fmtDouble(doc.width()))
                .append(",\"height\":").append(fmtDouble(doc.height()))
                .append(",\"unsupported\":[");
        List<String> unsupported = doc.unsupportedEnts();
        for (int i = 0; i < unsupported.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("\"").append(escapeJson(unsupported.get(i))).append("\"");
        }
        json.append("]");
    }

    /**
     * 追加 DWG 字段（仅头部信息，不含图形）
     *
     * @param json JSON 构造器
     * @param data DWG 字节
     * @throws IOException 解析失败
     */
    private void appendDwgJson(StringBuilder json, byte[] data) throws IOException {
        DwgHeaderInfo info = DwgHeaderParser.create().parse(data);
        json.append(",\"renderable\":false")
                .append(",\"versionLabel\":\"").append(escapeJson(info.versionLabel())).append("\"")
                .append(",\"versionCode\":\"").append(escapeJson(info.versionCode())).append("\"")
                .append(",\"acadRelease\":\"").append(escapeJson(info.acadRelease())).append("\"")
                .append(",\"maintenanceVersion\":").append(info.maintenanceVersion())
                .append(",\"unsupported\":[]");
    }

    /**
     * 格式化浮点数：NaN / 无限值归零，保留最多 4 位小数。
     *
     * <p>图形范围退化为一点（无图元）时会算出 NaN，直接写进 JSON 会让前端
     * JSON.parse 失败，因此这里统一兜底为 0。</p>
     *
     * @param v 数值
     * @return 可安全写入 JSON 的数值文本
     */
    private String fmtDouble(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "0";
        }
        if (v == Math.rint(v) && Math.abs(v) < 1e12) {
            return Long.toString((long) v);
        }
        String s = String.format(Locale.ENGLISH, "%.4f", v).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * 渲染文件列表页面。
     *
     * <p>按 {@link FileStorageSetting#getLayoutType()} 分流：</p>
     * <ul>
     *   <li>SYSTEM — 返回 Vue 独立构建壳页面（classpath {@code static/index.html}），
     *       交互由前端通过 REST API 完成（{@code ?format=json} / {@code ?config}）</li>
     *   <li>SIMPLE — 服务端渲染的简单文件列表树</li>
     * </ul>
     *
     * @param bucket 存储名
     * @param dir    当前目录路径
     * @param items  列表项
     * @param response 响应
     * @return 异步阶段
     */
    private CompletionStage<Void> renderLayoutPage(String bucket, String dir, List<Metadata> items, ServerResponse response) {
        if (resolveLayoutType() == FileStorageSetting.LayoutType.SYSTEM) {
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
 // 简单：服务端渲染简单列表
        String html = buildSimpleLayoutHtml(bucket, dir, items);
        response.setStatus(200)
                .setContentType("text/html;charset=UTF-8")
                .setBody(html.getBytes(StandardCharsets.UTF_8))
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 读取 Vue 前端壳页面（类路径 静态/索引.HTML），带懒缓存。
     *
     * @return HTML 内容，缺失返回 空
     */
    private volatile String vueShellCache;

    /**
     * 读取vueshell。
     * @return 读取vueshell的结果
     */
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
     * 解析配置的布局类型，非法值回退为 简单。
     * @return resolvelayout类型的结果
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

    /**
     * 构建简单layouthtml。
     * @param bucket bucket
     * @param dir dir
     * @param items items
     * @return 构建简单layouthtml的结果
     */
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
     * 系统 布局：类 macOS 查找 的文件管理器。
     *
     * <p>顶部半透明毛玻璃工具栏（交通灯 + 标题 + 列表/大图视图切换 + 搜索 + 配置入口）、
     * 左侧边栏（收藏 + 存储桶）、路径栏面包屑、列表/大图双视图、
     * 行右键菜单（预览/下载/一次性链接/复制/删除）与预览对话框。</p>
     *
     * <p>使用 text block 模板 + 占位符替换渲染，避免字符串拼接。</p>
     * @param bucket bucket
     * @param dir dir
     * @param items items
     * @return 构建系统layouthtml的结果
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
     * 查找 布局模板（文本 block）。
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
     * @param bucket bucket
     * @param dir dir
     * @return 构建查找pathbar的结果
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
     * @param bucket bucket
     * @return 构建查找sidebar的结果
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
     * @param bucket bucket
     * @param dir dir
     * @param items items
     * @return 构建查找rows的结果
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
     * @param bucket bucket
     * @return 构建查找script的结果
     */
    private String buildFinderScript(String bucket) {
        return """
(function(){
var list=document.getElementById('list');if(!list)return;
var menu=document.getElementById('ctxMenu');
var cur=null;
function findParent(el) {
    while(el&&!el.classList.contains('lrow'))el=el.parentElement;
    return el;
}
function pickRow(el) {
    var r=findParent(el);
    if (r) {
        showMenu(r);
    }
}
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
if(x+mw>window.innerWidth) {
    x=window.innerWidth-mw-8;
}
if(y+mh>window.innerHeight) {
    y=window.innerHeight-mh-8;
}
menu.style.left=x+'px';menu.style.top=y+'px';}
document.addEventListener('contextmenu',function(e){
var r=findParent(e.target);
if(r&&!(r.dataset&&r.dataset.nav==='parent')){
  e.preventDefault();showMenu(r);return false;}});
document.addEventListener('click',function(e){
if(e.target.closest('.ctx')) {
    return;
}
if(e.target.closest('.pv-mask')) {
    return;
}
menu.style.display='none';});
document.addEventListener('keydown',function(e){if(e.key==='Escape'){menu.style.display='none';closePv();closeOt();}});
function kindOf(name){var ext=(name.split('.').pop()||'').toLowerCase();
if(['png','jpg','jpeg','gif','svg','webp','bmp','ico'].indexOf(ext)>=0) {
    return 'image';
}
if(['mp4','webm','ogg','mov','avi','mkv','flv','wmv','m4v','3gp'].indexOf(ext)>=0) {
    return 'video';
}
if(['mp3','wav','flac','m4a'].indexOf(ext)>=0) {
    return 'audio';
}
if(['pdf'].indexOf(ext)>=0) {
    return 'pdf';
}
if(['txt','md','log','json','xml','csv','html','js','ts','java','py','c','cpp','go','sql'].indexOf(ext)>=0) {
    return 'text';
}
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
function closePv() {
    document.getElementById('pvMask').classList.remove('show');
    document.getElementById('pvBody').innerHTML='';
}
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
if(btnList) {
    btnList.addEventListener('click',function(){applyView('list')});
}
if(btnGrid) {
    btnGrid.addEventListener('click',function(){applyView('grid')});
}
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
     *
     * @return mac 文件夹 SVG 字符串
     */
    private String macFolderSvg() {
        return "<svg width=\"16\" height=\"16\" viewBox=\"0 0 32 32\" fill=\"none\">"
                + "<path d=\"M4 8.5C4 6.6 5.6 5 7.5 5H13l2.5 2.5H24.5C26.4 7.5 28 9.1 28 11v12.5c0 1.9-1.6 3.5-3.5 3.5h-17A3.5 3.5 0 0 1 4 23.5V8.5Z\" fill=\"#7FB3F7\"/>"
                + "<path d=\"M4 13h24v10.5c0 1.9-1.6 3.5-3.5 3.5h-17A3.5 3.5 0 0 1 4 23.5V13Z\" fill=\"#4A90E2\"/>"
                + "</svg>";
    }

    /**
     * 渲染Breadcrumb。
     *
     * @param sb 方法入参 sb
     * @param bucket 方法入参 bucket
     * @param dir 目录，不允许为 null
     */
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
                        /**
                         * renderentries简单。
                         * @param sb sb
                         * @param bucket bucket
                         * @param dir dir
                         * @param items items
                         * @param m m
                         * @return 构建href的结果
                         */
                        .append(escapeAttr(seg)).append("</a>");
                acc.append("/");
            }
        }
        sb.append("</div></div>");
    /**
     * renderentries简单。
     * @param sb sb
     * @param bucket bucket
     * @param dir dir
     * @param items items
     */
    }

    /**
     * 渲染EntriesSimple。
     *
     * @param sb 方法入参 sb
     * @param bucket 方法入参 bucket
     * @param dir 目录，不允许为 null
     * @param items 方法入参 items
     */
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

    /**
     * 构建Href。
     *
     * @param bucket 方法入参 bucket
     * @param dir 目录，不允许为 null
     * @param m 方法入参 m
     * @return 结果字符串
     */
    private String buildHref(String bucket, String dir, Metadata m) {
        StringBuilder sb = new StringBuilder("/");
        sb.append(encodePathSeg(bucket));
        if (dir != null && !dir.isEmpty()) {
            sb.append("/").append(encodePathFull(dir));
        }
        sb.append("/").append(encodePathSeg(m.getName()));
        if (m.isDirectory()) {
            sb.append("/");
        /**
         * 文件icon。
         * @param m m
         * @return 文件icon的结果
         */
        }
        return sb.toString();
    }

    /**
     * 文件Icon。
     *
     * @param m 方法入参 m
     * @return 结果字符串
     */
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
        /**
         * 格式化大小。
         * @param bytes bytes
         * @return 格式化大小的结果
         * @param millis millis
         */
        }
        return "\uD83D\uDCC4";
    }

    /**
     * 格式化大小。
     *
     * @param bytes 字节数组，不允许为 null
     * @return 结果字符串
     */
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

    /**
     * 格式化时间。
     *
     * @param millis 毫秒数，不允许为 null
     * @return 结果字符串
     */
    private String formatTime(long millis) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(millis);
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalDateTime dt = java.time.LocalDateTime.ofInstant(instant, zone);
        return String.format("%04d-%02d-%02d %02d:%02d",
                dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(),
                /**
                 * encode路径seg。
                 * @param s s
                 * @return encode路径seg的结果
                 * @param path 路径
                 */
                dt.getHour(), dt.getMinute());
    }

    /**
     * 编码路径Seg。
     *
     * @param s 方法入参 s
     * @return 结果字符串
     */
    private String encodePathSeg(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * 编码路径Full。
     *
     * @param path 路径，不允许为 null
     * @return 结果字符串
     */
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

    /**
     * escapeHtml。
     *
     * @param s 方法入参 s
     * @return 结果字符串
     */
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

    /** 预览页同源静态资源的前缀（preview provider 生成的 HTML 按此路径引用前端库） */
    private static final String PREVIEW_VENDOR_PREFIX = "preview-vendor";

    /** 预览静态资源在 classpath 下的根（随本模块打包在 resources/static 下） */
    private static final String PREVIEW_VENDOR_CLASSPATH = "static/preview-vendor/";

    /**
     * 提供预览页同源静态资源。
     *
     * <p>{@code UniverPreviewProvider} / {@code PdfViewerPreviewProvider} / {@code CodePreviewProvider}
     * 生成的预览 HTML 会引用 {@code /preview-vendor/**}（Univer 表格、pdf.js、highlight.js、mammoth 等
     * 前端库），并约定由宿主服务**同源**提供，从而不依赖公网 CDN。这些文件随本模块打包在
     * {@code resources/static/preview-vendor} 下，这里按请求路径从 classpath 取出返回。</p>
     *
     * <p>路径收敛：只读取 {@code static/preview-vendor/} 下的文件，并拦截 {@code ..} 穿越，
     * 避免把 classpath 上任意资源变成可下载的静态文件。</p>
     *
     * @param request  请求
     * @param response 响应
     * @param path     请求路径，形如 {@code /preview-vendor/univer/sheets-core.css}
     * @return 异步阶段
     */
    private CompletionStage<Void> servePreviewVendor(ServerRequest request, ServerResponse response, String path) {
        if (request.getMethod() != HttpMethod.GET && request.getMethod() != HttpMethod.HEAD) {
            return error(response, 405, "Method Not Allowed");
        }
        String relative = path.startsWith("/") ? path.substring(1) : path;
        if (relative.contains("..")) {
            return error(response, 400, "Invalid path");
        }
        String resource = PREVIEW_VENDOR_CLASSPATH + relative.substring(PREVIEW_VENDOR_PREFIX.length() + 1);
        try (InputStream in = FileStorageServerFilter.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return error(response, 404, "Not Found: " + path);
            }
            byte[] data = in.readAllBytes();
            response.setStatus(200)
                    .setHeader("Cache-Control", "public, max-age=86400")
                    .setContentType(resolveContentType(relative))
                    .setBody(data)
                    .end();
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("预览静态资源读取失败: {} - {}", path, e.getMessage(), e);
            return error(response, 500, "Internal Error: " + e.getMessage());
        }
    }

    /**
     * 处理对象下载/预览请求。
     *
     * <p>当 Key 对应对象不存在而作为目录存在时（或路径以 {@code /} 结尾），
     * 自动回退为该目录的对象列表 JSON，实现类资源管理器的路径式目录浏览。</p>
     *
     * <p>支持 Range 分段（openRange）、附件下载（openDownload + download=true 参数）、
     * 内联预览（打开preview）、水印响应头（打开watermark）、远程文件拉取（打开远程文件）。</p>
     *
     * @param storage  存储实例
     * @param request  请求
     * @param bucket   存储名
     * @param response 响应
     * @param key      对象 键
     * @return 异步阶段
     */
    private CompletionStage<Void> handleGet(FileStorage storage, ServerRequest request, String bucket, ServerResponse response, String key) {
        applyWatermarkHeader(response);
        // 目录访问意图：路径以 / 结尾，或显式 ?format=json（前端 Vue API 拉目录列表的统一约定）。
        // ?format=json 必须一并识别——否则 GET /{bucket}/{子目录}?format=json 会落到下面的
        // "读对象"分支，读内容时抛"是一个目录"，最终把"点进文件夹"变成 500。
        boolean directoryIntent = request.getPath().endsWith("/") || wantsJsonListing(request);

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
            // 空目录列举结果为空：通过存在性探测确认目录本身存在，返回空列表而非 404
            if (list.getResultCode() == ObjectResult.ResultCode.SUCCESS && objectExists(storage, key)) {
                return writeList(bucket, key, list, request, response);
            }
            return error(response, 404, "Object Not Found: " + key);
        }
        if (directoryIntent) {
            // 显式目录访问：直接列出。
            // 空目录要返回空列表而非 404（前端据此显示"空目录"）；但"列表为空"同时也可能是
            // 路径根本不存在，故必须再用存在性探测区分，否则访问不存在的目录会被当成空目录。
            ListObjectResult list = storage.listObject(ListObjectRequest.builder()
                    .filePath(key)
                    .limit(Integer.MAX_VALUE)
                    .build());
            if (list.getResultCode() == ObjectResult.ResultCode.SUCCESS
                    && ((list.getMetadata() != null && !list.getMetadata().isEmpty())
                    || objectExists(storage, key))) {
                return writeList(bucket, key, list, request, response);
            }
            return error(response, 404, "Directory Not Found: " + key);
        }

 // 配置 访问模式（与 preview/download 平级）：返回文件级配置 JSON
        if (boolParam(request, "config")) {
            return writeObjectConfig(bucket, key, result, response);
        }

        byte[] data;
        try (InputStream in = result.getInputStream()) {
            data = in.readAllBytes();
        } catch (Exception e) {
            // 读不出内容，多半是因为该 key 其实是目录（部分存储对目录的 getObject 也返回 SUCCESS）。
            // 兜底再尝试按目录列出：只有列出【非空子项】才认定为目录——空结果不足以区分
            // "空目录"与"文件本身损坏"，此时如实报错，避免把真实读取失败伪装成空目录。
            ListObjectResult fallback = storage.listObject(ListObjectRequest.builder()
                    .filePath(key)
                    .limit(Integer.MAX_VALUE)
                    .build());
            if (fallback.getResultCode() == ObjectResult.ResultCode.SUCCESS
                    && fallback.getMetadata() != null && !fallback.getMetadata().isEmpty()) {
                return writeList(bucket, key, fallback, request, response);
            }
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

        // 压缩包结构查询（?format=archive）：返回条目清单 JSON，供主应用内的 Vue 组件渲染。
        // 与 ?preview=true 是并列的另一条通道——后者把整个页面交给 iframe（独立文档，
        // 拿不到主应用的 Element Plus / koi 主题变量），本接口只回数据。
        // 放在访问模式判定之前：它是纯数据查询，与 openPreview / openDownload 开关无关。
        if (stringParam(request, "format", "archive")) {
            return writeArchiveEntries(fileName, data, response);
        }

        // CAD 图纸查询（?format=cad）：DXF 出图形，DWG 只出头信息
        if (stringParam(request, "format", "cad")) {
            return writeCadInfo(fileName, data, response);
        }

        response.setHeader("Content-Disposition", buildDisposition(download, fileName));

        // 显式 ?preview=true 时按渲染通道分流处理
        if (!download && wantPreview) {
            this.currentOpsRequest = request;
            // 图片处理（?preview&size=/crop=/rotate=/flip=/grayscale=/blur=/sharpen=/watermarkText=/quality=）：
            // 此前 spring 侧只上报能力（?format=capabilities），从不执行，URL 参数全部静默失效。
            byte[] imageProcessed = applyFileOperations(data, fileName, request);
            if (imageProcessed != null) {
                response.setStatus(200)
                        .setContentType(MimeTypeUtils.getMimeType(opOutputFormat(fileName, request)))
                        .setHeader("X-FileStorage-Preview", "image-op")
                        .setBody(imageProcessed)
                        .end();
                return CompletableFuture.completedFuture(null);
            }
            // 压缩包下钻：?preview&inner=包内路径 时提取条目并按条目类型预览
            String innerPath = request.getParams().get("inner");
            if (innerPath != null && !innerPath.isEmpty()) {
                CompletionStage<Void> innerStage = writeInnerEntryPreview(data, fileName, innerPath, response);
                if (innerStage != null) {
                    return innerStage;
                }
            }
            // 客户端直渲染：docx / xlsx / xls / pdf / pptx 直接回吐原始字节，
            // 由前端组件在浏览器内解析，避免服务端 LibreOffice / Aspose 转换开销。
            // 必须早于 trySpiPreview，否则 Univer / 旧版 Office 提供者会抢先在服务端渲染
            String clientRenderer = resolveClientRenderer(request, fileName, data);
            if (clientRenderer != null) {
                response.setStatus(200)
                        .setHeader(ClientPreviewSupport.HEADER_PREVIEW, ClientPreviewSupport.CHANNEL_CLIENT)
                        .setHeader(ClientPreviewSupport.HEADER_CLIENT_RENDERER, clientRenderer)
                        .setContentType(MimeTypeUtils.getMimeTypeFromFilename(fileName))
                        .setBody(data)
                        .end();
                return CompletableFuture.completedFuture(null);
            }
            // 服务端渲染：SPI 提供者（Univer / 打开文档 / Visio 等）
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

 // 范围 分段下载
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
     * @param key      对象 键
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
     * @param key      对象 键
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
     * <p>对象直接删除；目录递归列出全部子对象后自底向上删除
     * （本地文件系统不允许删除非空目录）。</p>
     *
     * @param storage  存储实例
     * @param response 响应
     * @param key      对象 键
     * @return 异步阶段
     */
    private CompletionStage<Void> handleDelete(FileStorage storage, ServerResponse response, String key) {
        if (key.isEmpty()) {
            return error(response, 400, "Object key is required");
        }
        try {
            deleteRecursively(storage, key);
        } catch (FileActionException e) {
            return error(response, e.getStatus(), e.getMessage());
        }
        response.setStatus(200)
                .setContentType("application/json;charset=UTF-8")
                .setBody("{\"status\":\"SUCCESS\",\"deleted\":\"" + escapeJson(key) + "\"}")
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /* ================== 文件管理动作（KodExplorer 风格） ================== */

    /**
     * 文件动作业务异常：携带 HTTP 状态码，由动作入口统一转换为错误响应。
     *
     * @author CH
     */
    private static final class FileActionException extends RuntimeException {

        /**
         * HTTP 状态码
         */
        private final int status;

        /**
         * 创建动作异常
         *
         * @param status  HTTP 状态码
         * @param message 错误信息
         */
        private FileActionException(int status, String message) {
            super(message);
            this.status = status;
        }

        /**
         * 获取 HTTP 状态码
         *
         * @return HTTP 状态码
         */
        private int getStatus() {
            return status;
        }
    }

    /**
     * 文件管理动作统一入口。
     *
     * <p>参数约定（query，框架已做 URL 解码）：</p>
     * <ul>
     *   <li>{@code mkdir/mkfile}：{@code path} 父目录（可空）、{@code name} 名称</li>
     *   <li>{@code rename}：{@code path}、{@code name} 原名称、{@code newName} 新名称</li>
     *   <li>{@code copy/move}：{@code sources} 源键（多个以换行分隔）、{@code target} 目标目录</li>
     *   <li>{@code zip}：{@code sources} 源键（GET，直接返回 application/zip 流）</li>
     *   <li>{@code unzip}：{@code path}、{@code name} 压缩包名、{@code target} 解压目标目录（可空，默认当前目录）</li>
     * </ul>
     *
     * @param storage  存储实例
     * @param request  请求
     * @param response 响应
     * @param action   动作名称
     * @param params   查询参数
     * @return 异步阶段
     */
    private CompletionStage<Void> handleAction(FileStorage storage, ServerRequest request, ServerResponse response,
                                               String action, Map<String, String> params) {
        try {
            switch (action) {
                case ACTION_MKDIR:
                    requirePost(request);
                    return doCreate(storage, response, params, true);
                case ACTION_MKFILE:
                    requirePost(request);
                    return doCreate(storage, response, params, false);
                case ACTION_RENAME:
                    requirePost(request);
                    return doRename(storage, response, params);
                case ACTION_COPY:
                    requirePost(request);
                    return doCopyOrMove(storage, response, params, false);
                case ACTION_MOVE:
                    requirePost(request);
                    return doCopyOrMove(storage, response, params, true);
                case ACTION_ZIP:
                    return doZip(storage, response, params);
                case ACTION_UNZIP:
                    requirePost(request);
                    return doUnzip(storage, response, params);
                case ACTION_SET_CONFIG:
                    return doSetConfig(request, response, params);
                default:
                    return error(response, 400, "Unsupported action: " + action);
            }
        } catch (FileActionException e) {
            return error(response, e.getStatus(), e.getMessage());
        } catch (Exception e) {
            log.error("文件管理动作执行失败: action={} - {}", action, e.getMessage(), e);
            return error(response, 500, "Action Failed: " + e.getMessage());
        }
    }

    /**
     * 校验动作请求必须为 POST。
     *
     * @param request 请求
     */
    private void requirePost(ServerRequest request) {
        if (request.getMethod() != HttpMethod.POST) {
            throw new FileActionException(405, "Method Not Allowed: action requires POST");
        }
    }

    /**
     * 管理员动态修改行为配置（实时生效，无需重启）。
     *
     * <p>请求体为 JSON，支持部分更新（缺省字段保持原值）：
     * {@code {"openPreview":true,"openDownload":true,"openRange":true,"openWatermark":false,
     * "openWebjars":true,"openRemoteFile":false,"layout":true,"layoutType":"SYSTEM","watermark":"文本"}}。
     * 必须通过 {@code ?token=} 携带系统配置访问令牌（{@link #getToken()}，每次服务启动随机生成），
     * 令牌不符返回 403。修改直接写入共享的 {@link FileStorageSetting} 实例，
     * 由于所有请求均实时读取该实例，新配置对后续请求立即生效。</p>
     *
     * @param request  请求（读取 JSON 请求体）
     * @param response 响应
     * @param params   查询参数（token）
     * @return 异步阶段
     */
    private CompletionStage<Void> doSetConfig(ServerRequest request, ServerResponse response,
                                              Map<String, String> params) {
        requirePost(request);
        String provided = params.get(PARAM_TOKEN);
        boolean tokenValid = provided != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
        if (!tokenValid) {
            throw new FileActionException(403, "Forbidden: invalid admin token");
        }
        byte[] raw = request.getBody();
        if (raw == null || raw.length == 0) {
            throw new FileActionException(400, "Empty request body");
        }
        String body = new String(raw, StandardCharsets.UTF_8);
        Boolean flag;
        if ((flag = extractBoolean(body, "openPreview")) != null) {
            setting.setOpenPreview(flag);
        }
        if ((flag = extractBoolean(body, "openDownload")) != null) {
            setting.setOpenDownload(flag);
        }
        if ((flag = extractBoolean(body, "openRange")) != null) {
            setting.setOpenRange(flag);
        }
        if ((flag = extractBoolean(body, "openWatermark")) != null) {
            setting.setOpenWatermark(flag);
        }
        if ((flag = extractBoolean(body, "openWebjars")) != null) {
            setting.setOpenWebjars(flag);
        }
        if ((flag = extractBoolean(body, "openRemoteFile")) != null) {
            setting.setOpenRemoteFile(flag);
        }
        if ((flag = extractBoolean(body, "layout")) != null) {
            setting.setLayout(flag);
        }
        if ((flag = extractBoolean(body, "showFormat")) != null) {
            setting.setShowFormat(flag);
        }
        if ((flag = extractBoolean(body, "showSize")) != null) {
            setting.setShowSize(flag);
        }
        if ((flag = extractBoolean(body, "showTime")) != null) {
            setting.setShowTime(flag);
        }
        String text;
        if ((text = extractString(body, "watermark")) != null) {
            setting.setWatermark(text.isEmpty() ? null : text);
        }
        if ((text = extractString(body, "layoutType")) != null) {
            String upper = text.trim().toUpperCase(Locale.ROOT);
            if (!FileStorageSetting.LayoutType.SIMPLE.name().equals(upper)
                    && !FileStorageSetting.LayoutType.SYSTEM.name().equals(upper)) {
                throw new FileActionException(400, "layoutType 仅支持 SIMPLE/SYSTEM: " + text);
            }
            setting.setLayoutType(upper);
        }
        if ((text = extractString(body, "imageFilter")) != null) {
            String upper = text.trim().toUpperCase(Locale.ROOT);
            // 原图固定合法；其余取值必须是 ImageFilter SPI 真实注册的扩展名
            if (!"NONE".equals(upper) && !resolveImageFilterNames().contains(upper)) {
                throw new FileActionException(400,
                        "imageFilter 不支持的滤镜: " + text + "，可用值: NONE/" + String.join("/", resolveImageFilterNames()));
            }
            setting.setImageFilter(upper);
        }
        // 访问权限热更新：private=true/false 针对 URL 中的当前桶；privateBuckets 直接替换全部私有桶
        Boolean privateFlag = extractBoolean(body, "private");
        if (privateFlag != null) {
            String bucketName = resolveBucketName(request);
            if (bucketName != null) {
                if (privateFlag) {
                    setting.getPrivateBuckets().add(bucketName);
                } else {
                    setting.getPrivateBuckets().remove(bucketName);
                }
            }
        }
        String privateCsv = extractString(body, "privateBuckets");
        if (privateCsv != null) {
            java.util.Set<String> buckets = java.util.concurrent.ConcurrentHashMap.newKeySet();
            for (String item : privateCsv.split(",")) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    buckets.add(trimmed);
                }
            }
            setting.setPrivateBuckets(buckets);
        }
        log.info("管理员动态更新文件存储配置: openPreview={}, openDownload={}, openRange={}, "
                        + "openWatermark={}, openRemoteFile={}, layout={}, layoutType={}, "
                        + "showFormat={}, showSize={}, showTime={}, imageFilter={}, privateBuckets={}",
                setting.isOpenPreview(), setting.isOpenDownload(), setting.isOpenRange(),
                setting.isOpenWatermark(), setting.isOpenRemoteFile(), setting.isLayout(), setting.getLayoutType(),
                setting.isShowFormat(), setting.isShowSize(), setting.isShowTime(), setting.getImageFilter(),
                String.join(",", setting.getPrivateBuckets()));
        notifyConfigChanged();
        return writeActionSuccess(response, "\"configUpdated\":true");
    }

    /**
     * 通知配置热更新（同步私有桶给 OAuth 鉴权过滤器）。
     */
    private void notifyConfigChanged() {
        java.util.function.Consumer<FileStorageSetting> listener = this.configListener;
        if (listener == null) {
            return;
        }
        try {
            listener.accept(setting);
        } catch (Exception e) {
            log.warn("配置热更新回调失败（不影响 setConfig 结果）: {}", e.getMessage());
        }
    }

    /**
     * 解析请求路径中的桶名（第一段）。
     *
     * @param request 当前请求
     * @return 桶名，无法解析时返回 空
     */
    private String resolveBucketName(ServerRequest request) {
        List<String> segments = splitPath(request.getPath());
        return segments.isEmpty() ? null : segments.getFirst();
    }

    /**
     * 从 JSON 文本中提取布尔字段值（轻量解析，避免引入额外依赖）。
     *
     * @param json JSON 文本
     * @param key  字段名
     * @return 字段值；字段不存在返回 null
     */
    private static Boolean extractBoolean(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)").matcher(json);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : null;
    }

    /**
     * 从 JSON 文本中提取字符串字段值（支持 \" \\ \n \t \r \/ 转义）。
     *
     * @param json JSON 文本
     * @param key  字段名
     * @return 字段值（已反转义）；字段不存在返回 null
     */
    private static String extractString(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return matcher.find() ? unescapeJson(matcher.group(1)) : null;
    }

    /**
     * 反转义 JSON 字符串中的常见转义序列。
     *
     * @param raw 原始转义文本
     * @return 反转义后的文本
     */
    private static String unescapeJson(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char next = raw.charAt(++i);
                switch (next) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    default: sb.append(next); break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 新建目录 / 新建空文件。
     *
     * @param storage   存储实例
     * @param response  响应
     * @param params    查询参数
     * @param directory true=目录；false=空文件
     * @return 异步阶段
     */
    private CompletionStage<Void> doCreate(FileStorage storage, ServerResponse response,
                                           Map<String, String> params, boolean directory) {
        String path = normalizePathParam(params.get("path"));
        String name = requireValidName(params.get("name"));
        String key = joinKey(path, name);
        if (objectExists(storage, key)) {
            throw new FileActionException(409, "目标已存在: " + key);
        }
        if (directory) {
            createDirectory(storage, key);
        } else {
            putBytes(storage, key, EMPTY_BYTES, "新建文件失败: " + key);
        }
        return writeActionSuccess(response, "\"created\":\"" + escapeJson(key) + "\"");
    }

    /**
     * 重命名（同目录改名；目录递归搬运）。
     *
     * @param storage  存储实例
     * @param response 响应
     * @param params   查询参数
     * @return 异步阶段
     */
    private CompletionStage<Void> doRename(FileStorage storage, ServerResponse response, Map<String, String> params) {
        String path = normalizePathParam(params.get("path"));
        String name = requireValidName(params.get("name"));
        String newName = requireValidName(params.get("newName"));
        if (name.equals(newName)) {
            throw new FileActionException(400, "新旧名称不能相同");
        }
        String source = joinKey(path, name);
        String target = joinKey(path, newName);
        if (stat(storage, source) == null) {
            throw new FileActionException(404, "对象不存在: " + source);
        }
        if (objectExists(storage, target)) {
            throw new FileActionException(409, "目标已存在: " + target);
        }
        copyRecursively(storage, source, target);
        deleteRecursively(storage, source);
        return writeActionSuccess(response, "\"renamed\":\"" + escapeJson(source) + "\",\"target\":\""
                + escapeJson(target) + "\"");
    }

    /**
     * 复制 / 剪切（移动）一个或多个对象到目标目录。
     *
     * <p>同名冲突时自动按"名称 - 副本(n).扩展名"重命名；移动到自身或自身子目录时拒绝。</p>
     *
     * @param storage  存储实例
     * @param response 响应
     * @param params   查询参数
     * @param move     true=移动（复制后删除源）；false=仅复制
     * @return 异步阶段
     */
    private CompletionStage<Void> doCopyOrMove(FileStorage storage, ServerResponse response,
                                               Map<String, String> params, boolean move) {
        List<String> sources = parseSources(params.get("sources"));
        String targetDir = normalizePathParam(params.get("target"));
        if (!targetDir.isEmpty()) {
            Metadata targetMeta = stat(storage, targetDir);
            if (targetMeta == null || !targetMeta.isDirectory()) {
                throw new FileActionException(404, "目标目录不存在: " + targetDir);
            }
        }
        StringBuilder keys = new StringBuilder();
        int count = 0;
        for (String source : sources) {
            Metadata meta = stat(storage, source);
            if (meta == null) {
                throw new FileActionException(404, "对象不存在: " + source);
            }
            String targetName = uniqueName(storage, targetDir, baseName(source));
            String targetKey = joinKey(targetDir, targetName);
            if (targetKey.equals(source) || targetKey.startsWith(source + "/")) {
                throw new FileActionException(400, "不能移动到自身或其子目录: " + source);
            }
            copyRecursively(storage, source, targetKey);
            if (move) {
                deleteRecursively(storage, source);
            }
            if (count > 0) {
                keys.append(",");
            }
            keys.append("\"").append(escapeJson(targetKey)).append("\"");
            count++;
        }
        return writeActionSuccess(response, "\"count\":" + count + ",\"keys\":[" + keys + "]");
    }

    /**
     * 将一个或多个对象打包为 ZIP 下载。
     *
     * <p>单个源时压缩包名为"源名称.zip"（文件则去掉原扩展名）；多个源时为 {@code download.zip}。
     * ZIP 条目按源的相对结构组织。</p>
     *
     * @param storage  存储实例
     * @param response 响应
     * @param params   查询参数
     * @return 异步阶段
     */
    private CompletionStage<Void> doZip(FileStorage storage, ServerResponse response, Map<String, String> params) {
        List<String> sources = parseSources(params.get("sources"));
        String zipName;
        if (sources.size() == 1) {
            String single = baseName(sources.getFirst());
            int dot = single.lastIndexOf('.');
            String stem = dot > 0 ? single.substring(0, dot) : single;
            zipName = stem + ".zip";
        } else {
            zipName = DEFAULT_ZIP_NAME;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (String source : sources) {
                Metadata meta = stat(storage, source);
                if (meta == null) {
                    throw new FileActionException(404, "对象不存在: " + source);
                }
                // 单文件直接以文件名作为条目（前缀为空）；其余情况以各自名称作为根条目
                String prefix = (sources.size() == 1 && !meta.isDirectory()) ? "" : baseName(source) + "/";
                appendZipEntry(storage, source, prefix, zos);
            }
        } catch (FileActionException e) {
            throw e;
        } catch (IOException e) {
            throw new FileActionException(500, "打包失败: " + e.getMessage());
        }
        response.setStatus(200)
                .setHeader("Content-Disposition", buildDisposition(true, zipName))
                .setContentType(CONTENT_TYPE_ZIP)
                .setBody(bos.toByteArray())
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 解压 ZIP 到目标目录（默认压缩包所在目录），覆盖同名对象。
     *
     * @param storage  存储实例
     * @param response 响应
     * @param params   查询参数
     * @return 异步阶段
     */
    private CompletionStage<Void> doUnzip(FileStorage storage, ServerResponse response, Map<String, String> params) {
        String path = normalizePathParam(params.get("path"));
        String name = requireValidName(params.get("name"));
        String zipKey = joinKey(path, name);
        String targetDir = params.containsKey("target") ? normalizePathParam(params.get("target")) : path;
        if (!targetDir.isEmpty()) {
            Metadata targetMeta = stat(storage, targetDir);
            if (targetMeta == null || !targetMeta.isDirectory()) {
                throw new FileActionException(404, "目标目录不存在: " + targetDir);
            }
        }
        GetObjectResult get = storage.getObject(zipKey);
        if (get.getResultCode() != ObjectResult.ResultCode.SUCCESS || get.getInputStream() == null) {
            throw new FileActionException(404, "压缩包不存在: " + zipKey);
        }
        int count;
        try (InputStream in = get.getInputStream();
             ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            count = 0;
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = normalizeZipEntry(entry.getName());
                if (!entryName.isEmpty()) {
                    String dstKey = joinKey(targetDir, entryName);
                    if (entry.isDirectory()) {
                        createDirectory(storage, dstKey);
                    } else {
                        putBytes(storage, dstKey, zis.readAllBytes(), "解压写入失败: " + dstKey);
                    }
                    count++;
                }
                zis.closeEntry();
            }
        } catch (FileActionException e) {
            throw e;
        } catch (IOException e) {
            throw new FileActionException(500, "解压失败: " + e.getMessage());
        }
        return writeActionSuccess(response, "\"entries\":" + count + ",\"target\":\""
                + escapeJson(targetDir) + "\"");
    }

    /* ---------- 文件管理动作：SPI 组合原语 ---------- */

    /**
     * 创建目录（键以 {@code /} 结尾的零字节占位约定，本地存储落盘为真实目录）。
     *
     * @param storage 存储实例
     * @param key     目录键
     */
    private void createDirectory(FileStorage storage, String key) {
        PutObjectResult result = storage.putObject(PutObjectRequest.builder()
                .fileName(baseName(key) + "/")
                .filePath(parentOf(key))
                .content(EMPTY_BYTES)
                .build());
        ensurePutSuccess(result, "创建目录失败: " + key);
    }

    /**
     * 写入字节内容到指定键。
     *
     * @param storage 存储实例
     * @param key     对象键
     * @param bytes   内容字节
     * @param errMsg  失败提示
     */
    private void putBytes(FileStorage storage, String key, byte[] bytes, String errMsg) {
        PutObjectResult result = storage.putObject(PutObjectRequest.builder()
                .fileName(baseName(key))
                .filePath(parentOf(key))
                .content(bytes)
                .build());
        ensurePutSuccess(result, errMsg);
    }

    /**
     * 校验写入结果。
     *
     * @param result 写入结果
     * @param errMsg 失败提示
     */
    private void ensurePutSuccess(PutObjectResult result, String errMsg) {
        if (result.getResultCode() != ObjectResult.ResultCode.SUCCESS) {
            throw new FileActionException(500, errMsg + (result.getMessage() == null ? "" : ": " + result.getMessage()));
        }
    }

    /**
     * 请求是否显式要求"目录列表 JSON"。
     *
     * <p>前端（Vue API）统一用 {@code ?format=json} 拉目录列表。这个约定此前只在桶根生效：
     * 子目录路径会落到 {@link #handleGet} 的"读对象"分支，读内容时抛"是一个目录"，
     * 最终把"点进文件夹"变成 500 Internal Server Error。</p>
     *
     * <p>与"路径以 {@code /} 结尾"同等对待即可：两者都是"我要看这个目录里有什么"。</p>
     *
     * @param request 请求
     * @return true 表示要求目录列表
     */
    private static boolean wantsJsonListing(ServerRequest request) {
        Map<String, String> params = request.getParams();
        return params != null && "json".equalsIgnoreCase(params.get("format"));
    }

    /**
     * 判断对象是否存在。
     *
     * @param storage 存储实例
     * @param key     对象键
     * @return 存在返回 true
     */
    private boolean objectExists(FileStorage storage, String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        ExistObjectResult result = storage.existObject(ExistObjectRequest.builder()
                .fileName(baseName(key))
                .filePath(parentOf(key))
                .build());
        return result.getResultCode() == ObjectResult.ResultCode.SUCCESS && result.isExists();
    }

    /**
     * 查询单个对象元数据。
     *
     * <p>优先从父目录列表中匹配（可区分目录/文件）；存储实现语义不一致导致父列表缺失时，
     * 通过存在性 + 目标路径列举结果兜底判断。对象不存在返回 {@code null}。</p>
     *
     * @param storage 存储实例
     * @param key     对象键
     * @return 元数据；不存在返回 null
     */
    private Metadata stat(FileStorage storage, String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String parent = parentOf(key);
        String name = baseName(key);
        ListObjectResult list = storage.listObject(ListObjectRequest.builder()
                .filePath(parent)
                .limit(LIST_LIMIT)
                .build());
        if (list.getResultCode() == ObjectResult.ResultCode.SUCCESS && list.getMetadata() != null) {
            for (Metadata metadata : list.getMetadata()) {
                if (name.equals(metadata.getName())) {
                    return metadata;
                }
            }
        }
        if (!objectExists(storage, key)) {
            return null;
        }
        ListObjectResult probe = storage.listObject(ListObjectRequest.builder()
                .filePath(key)
                .limit(1)
                .build());
        boolean directory = probe.getResultCode() == ObjectResult.ResultCode.SUCCESS
                && probe.getMetadata() != null && !probe.getMetadata().isEmpty();
        return Metadata.builder()
                .name(name)
                .path(parent)
                .directory(directory)
                .build();
    }

    /**
     * 流式复制单个文件对象。
     *
     * @param storage 存储实例
     * @param source  源键
     * @param target  目标键
     */
    private void copyFile(FileStorage storage, String source, String target) {
        GetObjectResult get = storage.getObject(source);
        if (get.getResultCode() != ObjectResult.ResultCode.SUCCESS || get.getInputStream() == null) {
            throw new FileActionException(500, "读取对象失败: " + source);
        }
        try (InputStream in = get.getInputStream()) {
            PutObjectResult put = storage.putObject(PutObjectRequest.builder()
                    .fileName(baseName(target))
                    .filePath(parentOf(target))
                    .inputStream(in)
                    .build());
            ensurePutSuccess(put, "写入对象失败: " + target);
        } catch (IOException e) {
            throw new FileActionException(500, "复制对象失败: " + source + " - " + e.getMessage());
        }
    }

    /**
     * 递归复制对象（目录先建目录再逐项复制，文件直接流式复制）。
     *
     * @param storage 存储实例
     * @param source  源键
     * @param target  目标键
     */
    private void copyRecursively(FileStorage storage, String source, String target) {
        Metadata meta = stat(storage, source);
        if (meta == null) {
            throw new FileActionException(404, "对象不存在: " + source);
        }
        if (!meta.isDirectory()) {
            copyFile(storage, source, target);
            return;
        }
        createDirectory(storage, target);
        ListObjectResult list = storage.listObject(ListObjectRequest.builder()
                .filePath(source)
                .limit(LIST_LIMIT)
                .build());
        if (list.getResultCode() != ObjectResult.ResultCode.SUCCESS || list.getMetadata() == null) {
            throw new FileActionException(500, "列举目录失败: " + source);
        }
        for (Metadata child : list.getMetadata()) {
            copyRecursively(storage, joinKey(source, child.getName()), joinKey(target, child.getName()));
        }
    }

    /**
     * 递归删除对象（目录自底向上删除全部子对象后删除自身）。
     *
     * @param storage 存储实例
     * @param key     对象键
     */
    private void deleteRecursively(FileStorage storage, String key) {
        Metadata meta = stat(storage, key);
        if (meta != null && meta.isDirectory()) {
            ListObjectResult list = storage.listObject(ListObjectRequest.builder()
                    .filePath(key)
                    .limit(LIST_LIMIT)
                    .build());
            if (list.getResultCode() != ObjectResult.ResultCode.SUCCESS || list.getMetadata() == null) {
                throw new FileActionException(500, "列举目录失败: " + key);
            }
            for (Metadata child : list.getMetadata()) {
                deleteRecursively(storage, joinKey(key, child.getName()));
            }
        }
        DeleteObjectResult result = storage.deleteObject(key);
        if (result.getResultCode() != ObjectResult.ResultCode.SUCCESS) {
            throw new FileActionException(500, "删除对象失败: " + key
                    + (result.getMessage() == null ? "" : ": " + result.getMessage()));
        }
    }

    /**
     * 在目标目录中生成不冲突的名称，冲突时按"名称 - 副本(n).扩展名"递增。
     *
     * @param storage 存储实例
     * @param dir     目标目录
     * @param name    期望名称
     * @return 可用名称
     */
    private String uniqueName(FileStorage storage, String dir, String name) {
        if (!objectExists(storage, joinKey(dir, name))) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        String first = stem + COPY_SUFFIX + ext;
        if (!objectExists(storage, joinKey(dir, first))) {
            return first;
        }
        for (int i = 2; i < 10000; i++) {
            String candidate = stem + COPY_SUFFIX + "(" + i + ")" + ext;
            if (!objectExists(storage, joinKey(dir, candidate))) {
                return candidate;
            }
        }
        throw new FileActionException(409, "同名对象过多，无法生成副本名称: " + name);
    }

    /**
     * 递归向 ZIP 输出流追加对象。
     *
     * @param storage 存储实例
     * @param key     当前对象键
     * @param prefix  ZIP 内前缀（目录条目带尾斜杠；根文件为空串）
     * @param zos     ZIP 输出流
     * @throws IOException 读写 ZIP 流失败时抛出
     */
    private void appendZipEntry(FileStorage storage, String key, String prefix, ZipOutputStream zos) throws IOException {
        Metadata meta = stat(storage, key);
        if (meta == null) {
            throw new FileActionException(404, "对象不存在: " + key);
        }
        if (!meta.isDirectory()) {
            zos.putNextEntry(new ZipEntry(prefix + baseName(key)));
            GetObjectResult get = storage.getObject(key);
            if (get.getResultCode() != ObjectResult.ResultCode.SUCCESS || get.getInputStream() == null) {
                throw new FileActionException(500, "读取对象失败: " + key);
            }
            try (InputStream in = get.getInputStream()) {
                in.transferTo(zos);
            }
            zos.closeEntry();
            return;
        }
        // 显式写入目录条目（保证空目录也能保留在压缩包中）
        String dirEntry = prefix + baseName(key) + "/";
        zos.putNextEntry(new ZipEntry(dirEntry));
        zos.closeEntry();
        ListObjectResult list = storage.listObject(ListObjectRequest.builder()
                .filePath(key)
                .limit(LIST_LIMIT)
                .build());
        if (list.getResultCode() != ObjectResult.ResultCode.SUCCESS || list.getMetadata() == null) {
            throw new FileActionException(500, "列举目录失败: " + key);
        }
        for (Metadata child : list.getMetadata()) {
            appendZipEntry(storage, joinKey(key, child.getName()), dirEntry, zos);
        }
    }

    /* ---------- 文件管理动作：参数与键工具 ---------- */

    /**
     * 解析多源键参数（换行分隔），逐项做路径穿越校验。
     *
     * @param raw 原始参数值
     * @return 规范化后的键列表
     */
    private List<String> parseSources(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new FileActionException(400, "参数 sources 不能为空");
        }
        List<String> sources = new ArrayList<>();
        for (String item : raw.split(SOURCES_SEPARATOR)) {
            String key = normalizeObjectKey(item);
            if (!key.isEmpty() && !sources.contains(key)) {
                sources.add(key);
            }
        }
        if (sources.isEmpty()) {
            throw new FileActionException(400, "参数 sources 不能为空");
        }
        return sources;
    }

    /**
     * 规范化查询参数中的目录路径（允许空串表示桶根目录）。
     *
     * @param raw 原始参数值
     * @return 规范化后的路径
     */
    private String normalizePathParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return normalizeObjectKey(raw);
    }

    /**
     * 规范化对象键：反斜杠转正斜杠、去掉首尾斜杠、拒绝 {@code ..} 与盘符等危险片段。
     *
     * @param raw 原始键
     * @return 规范化后的键
     */
    private String normalizeObjectKey(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.replace('\\', '/').trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            return "";
        }
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || "..".equals(segment) || ".".equals(segment) || segment.contains(":")) {
                throw new FileActionException(400, "非法路径: " + raw);
            }
        }
        return value;
    }

    /**
     * 读取并校验名称参数（不允许包含路径分隔符与文件系统非法字符）。
     *
     * @param raw 原始参数值
     * @return 合法名称
     */
    private String requireValidName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new FileActionException(400, "名称不能为空");
        }
        String name = raw.trim();
        if (name.contains("/") || name.contains("\\") || "..".equals(name) || ".".equals(name)
                || name.contains(":") || name.contains("*") || name.contains("?")
                || name.contains("\"") || name.contains("<") || name.contains(">") || name.contains("|")) {
            throw new FileActionException(400, "名称非法: " + name);
        }
        return name;
    }

    /**
     * 规范化 ZIP 条目名，防止 Zip Slip 目录穿越。
     *
     * @param entryName 原始条目名
     * @return 安全的相对条目名；非法条目抛出异常
     */
    private String normalizeZipEntry(String entryName) {
        String value = entryName.replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        for (String segment : value.split("/")) {
            if ("..".equals(segment) || segment.contains(":")) {
                throw new FileActionException(400, "压缩包包含非法路径: " + entryName);
            }
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * 拼接父目录与名称。
     *
     * @param parent 父目录（空串表示桶根）
     * @param name   名称
     * @return 完整对象键
     */
    private String joinKey(String parent, String name) {
        if (parent == null || parent.isEmpty()) {
            return name;
        }
        return parent + "/" + name;
    }

    /**
     * 取对象键的父目录部分。
     *
     * @param key 对象键
     * @return 父目录（根下对象返回空串）
     */
    private String parentOf(String key) {
        int index = key.lastIndexOf('/');
        return index < 0 ? "" : key.substring(0, index);
    }

    /**
     * 取对象键的末段名称。
     *
     * @param key 对象键
     * @return 名称
     */
    private String baseName(String key) {
        int index = key.lastIndexOf('/');
        return index < 0 ? key : key.substring(index + 1);
    }

    /**
     * 写出文件管理动作成功响应。
     *
     * @param response 响应
     * @param detail   额外 JSON 字段（不含外层花括号）
     * @return 已完成阶段
     */
    private CompletionStage<Void> writeActionSuccess(ServerResponse response, String detail) {
        response.setStatus(200)
                .setContentType("application/json;charset=UTF-8")
                .setBody("{\"status\":\"SUCCESS\"," + detail + "}")
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 依据设置追加水印响应头。
     *
     * <p>HTTP 响应头仅允许 ASCII 可打印字符，水印值含中文等非 ASCII 字符时
     * JDK HttpServer 会直接抛出 {@code Illegal character found in header}（500），
     * 因此统一按 UTF-8 百分号编码输出，消费方使用百分号解码还原。</p>
     *
     * @param response 响应
     */
    private void applyWatermarkHeader(ServerResponse response) {
        if (setting.isOpenWatermark() && setting.getWatermark() != null) {
            String encoded = URLEncoder.encode(setting.getWatermark(), StandardCharsets.UTF_8);
            response.setHeader("X-Watermark", encoded);
        }
    }

    /**
     * 解析 范围 请求头。
     *
     * @param range     范围 头值（bytes=启动-结束 格式）
     * @param totalSize 数据总长度
     * @return [start, 结束] 区间（结束 为排他上界），格式非法返回 空
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
     * 规范化对象 键。
     *
     * <p>执行 URL 解码，拒绝包含 {@code ..}、{@code :} 等危险片段的路径穿越 Key。
     * http(s) 开头的远程地址仅在 打开远程文件 开启时放行。</p>
     *
     * @param rawKey 原始 键
     * @return 规范化后的 键，非法时返回 空
     */
    private String sanitizeKey(String rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            return "";
        }
        try {
            String decoded = URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name());
 // 远程地址仅受 打开远程文件 开关控制
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
     * 判断查询参数是否取到指定值（大小写不敏感，null 安全）。
     *
     * <p>与 {@link #boolParam} 的标志式语义互补：这里比对具体取值，
     * 用于 {@code ?format=json} / {@code ?format=capabilities} 这类互斥分支。</p>
     *
     * @param request 请求
     * @param name    参数名
     * @param value   期望取值
     * @return true 表示参数存在且取值匹配
     */
    private boolean stringParam(ServerRequest request, String name, String value) {
        Map<String, String> params = request.getParams();
        if (params == null) {
            return false;
        }
        return value.equalsIgnoreCase(params.get(name));
    }

    /**
     * 压缩包条目下钻预览。
     * <p>从压缩包字节中提取 {@code inner} 指定的条目，能走 SPI（代码/Markdown/CSV/Office 等）
     * 的输出 HTML 预览页；其余条目按原始内容类型内联返回。条目不存在或路径非法时返回提示页。</p>
     *
     * @param archiveData 压缩包字节
     * @param fileName    压缩包文件名
     * @param innerPath   包内条目路径（已 URL 解码）
     * @param response    响应对象
     * @return 已完成的异步阶段（始终提交响应）
     */
    private CompletionStage<Void> writeInnerEntryPreview(byte[] archiveData, String fileName,
                                                         String innerPath, ServerResponse response) {
        // 安全校验：拒绝路径穿越、反斜杠、绝对路径与盘符
        boolean safe = innerPath != null
                && !innerPath.contains("..")
                && !innerPath.contains("\\")
                && !innerPath.startsWith("/")
                && innerPath.indexOf(':') < 0;

        byte[] entryBytes = null;
        if (safe) {
            String ext = simpleExtOf(fileName);
            for (String candidate : buildExtCandidates(fileName, ext)) {
                entryBytes = ArchivePreviewProvider.extractFile(archiveData, candidate, innerPath);
                if (entryBytes != null) {
                    break;
                }
            }
        }

        if (entryBytes == null) {
            String html = buildInnerMessagePage("压缩包内条目不存在或不支持预览：" + innerPath);
            response.setStatus(404)
                    .setHeader("X-FileStorage-Preview", "inner-miss")
                    .setContentType("text/html;charset=UTF-8")
                    .setBody(html.getBytes(StandardCharsets.UTF_8))
                    .end();
            return CompletableFuture.completedFuture(null);
        }

        String entryName = innerPath;
        int slash = entryName.lastIndexOf('/');
        if (slash >= 0) {
            entryName = entryName.substring(slash + 1);
        }

        byte[] previewPage = trySpiPreview(entryBytes, entryName);
        if (previewPage != null) {
            response.setStatus(200)
                    .setHeader("X-FileStorage-Preview", "spi-inner")
                    .setContentType("text/html;charset=UTF-8")
                    .setBody(previewPage)
                    .end();
            return CompletableFuture.completedFuture(null);
        }

        response.setStatus(200)
                .setHeader("X-FileStorage-Preview", "inner-raw")
                .setHeader("Content-Disposition", buildDisposition(false, entryName))
                .setContentType(resolveContentType(entryName))
                .setBody(entryBytes)
                .end();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 构建压缩包条目下钻失败提示页。
     *
     * @param message 提示信息
     * @return 完整 HTML 页面
     */
    private String buildInnerMessagePage(String message) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>body{margin:0;padding:48px 24px;background:#f8f9fa;color:#1a1a2e;"
                + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;text-align:center}"
                + ".box{display:inline-block;background:#fff;border:1px solid #e5e7eb;border-radius:12px;"
                + "padding:28px 36px;max-width:560px}"
                + ".msg{font-size:14px;color:#374151;margin-bottom:16px;word-break:break-all}"
                + "a{color:#2563eb;text-decoration:none;font-size:14px}"
                + "</style></head><body><div class=\"box\">"
                + "<div class=\"msg\">" + escapeHtml(message) + "</div>"
                + "<a href=\"javascript:history.back()\">返回压缩包列表</a>"
                + "</div></body></html>";
    }

    /**
     * 取文件名末段扩展名（小写、不含点）。
     *
     * <p>刻意只取末段：返回值会作为 {@link #buildExtCandidates} 的兜底候选，
     * 若在此处提前合并为复合扩展名（如 {@code tar.gz}），会挤掉 {@code gz}
     * 这一单段兜底候选，导致压缩包条目识别回退。</p>
     *
     * @param fileName 文件名
     * @return 扩展名；无扩展名时返回空串
     */
    private static String simpleExtOf(String fileName) {
        return fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
    }

    /**
     * 尝试通过 SPI 预览提供者转换文件为 HTML 预览页（带并发去重）。
     *
     * <p>classpath 中存在 utils-support-filestorage-starter 时，可预览
     * xlsx/xls/docx/doc/pptx/ppt（Univer）、odt/ods/odp（打开文档）、vsd（Visio）等；
     * 无可用提供者或转换失败时返回 空，回退为原始字节响应。</p>
     *
     * <p>并发去重：同一文件的并发请求会共享同一个转换 Future，
     * 避免重复调用 SPI 提供者浪费 CPU/IO。</p>
     *
     * @param content  文件内容
     * @param fileName 文件名（用于推断扩展名）
     * @return HTML 页面字节，无法预览返回 空
     */
    private byte[] trySpiPreview(byte[] content, String fileName) {
        List<FileStoragePreviewProvider> providers = previewProviders();
        if (providers.isEmpty()) {
            return null;
        }
        String ext = simpleExtOf(fileName);

        // 扩展名候选：复合后缀（如 tar.gz）优先于单段后缀（如 gz）
        List<String> extCandidates = buildExtCandidates(fileName, ext);

 // 构建去重 键：内容首 1KB + 长度 + 扩展名（兼顾性能与唯一性）
        String dedupKey = buildDedupKey(content, extCandidates.isEmpty() ? "" : extCandidates.getFirst());

        CompletableFuture<byte[]> future = inflightMap.computeIfAbsent(dedupKey, k -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    for (FileStoragePreviewProvider provider : providers) {
                        PreviewResult result = null;
                        String matchedExt = null;
                        for (String candidate : extCandidates) {
                            if (!provider.supports(candidate, "")) {
                                continue;
                            }
                            matchedExt = candidate;
                            break;
                        }
                        if (matchedExt == null) {
                            continue;
                        }
                        try {
                            result = provider.preview(content, matchedExt, "");
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
     * 构建扩展名候选列表。
     * <p>双段复合后缀（如 {@code tar.gz}、{@code tar.bz2}）排在前面，
     * 末段单扩展名（如 {@code gz}）作为兜底，保证复合格式优先命中专属 Provider。</p>
     *
     * @param fileName 文件名
     * @param simpleExt 末段扩展名（小写）
     * @return 扩展名候选列表，至少包含末段扩展名
     */
    private List<String> buildExtCandidates(String fileName, String simpleExt) {
        List<String> candidates = new ArrayList<>();
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        int lastDot = lowerName.lastIndexOf('.');
        if (lastDot > 0) {
            int previousDot = lowerName.lastIndexOf('.', lastDot - 1);
            if (previousDot >= 0) {
                String compoundExt = lowerName.substring(previousDot + 1);
                if (!compoundExt.equals(simpleExt)) {
                    candidates.add(compoundExt);
                }
            }
        }
        candidates.add(simpleExt);
        return candidates;
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
        String htmlContent = result.getHtmlContent();
        // 提供方已返回完整 HTML 文档（含 <!DOCTYPE html> 或 <html>）时，
        // 不再套一层 <html>，而是把资源注入其 head/body，避免双层页面嵌套
        if (htmlContent != null && isCompleteDocument(htmlContent)) {
            return injectResourcesIntoDocument(htmlContent, result);
        }
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
     * 判断字符串是否为完整 HTML 文档（以 {@code <!doctype html} 或 {@code <html} 开头）。
     *
     * @param html HTML 内容
     * @return true 表示已是完整文档
     */
    private boolean isCompleteDocument(String html) {
        String trimmed = html.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("<!doctype html") || lower.startsWith("<html");
    }

    /**
     * 将预览资源（CSS/JS）注入到已有的完整 HTML 文档中。
     *
     * <p>CSS 注入到 {@code </head>} 前，JS 注入到 {@code </body>} 前；
     * 找不到对应标记时追加到文档末尾。</p>
     *
     * @param html   完整 HTML 文档
     * @param result 预览结果（含资源）
     * @return 注入后的 HTML
     */
    private String injectResourcesIntoDocument(String html, PreviewResult result) {
        StringBuilder head = new StringBuilder();
        if (result.getEmbeddedCss() != null) {
            head.append("<style>").append(result.getEmbeddedCss()).append("</style>");
        }
        if (result.getCssUrls() != null) {
            for (String url : result.getCssUrls()) {
                head.append("<link rel=\"stylesheet\" href=\"").append(escapeAttr(url)).append("\">");
            }
        }
        StringBuilder body = new StringBuilder();
        if (result.getEmbeddedJs() != null) {
            body.append("<script>").append(result.getEmbeddedJs()).append("</script>");
        }
        if (result.getJsUrls() != null) {
            for (String url : result.getJsUrls()) {
                body.append("<script src=\"").append(escapeAttr(url)).append("\"></script>");
            }
        }
        String out = html;
        String headStr = head.toString();
        if (!headStr.isEmpty()) {
            String headEnd = "</head>";
            int idx = out.toLowerCase(Locale.ROOT).indexOf(headEnd);
            if (idx >= 0) {
                out = out.substring(0, idx) + headStr + out.substring(idx);
            } else {
                out = headStr + out;
            }
        }
        String bodyStr = body.toString();
        if (!bodyStr.isEmpty()) {
            String bodyEnd = "</body>";
            int idx = out.toLowerCase(Locale.ROOT).lastIndexOf(bodyEnd);
            if (idx >= 0) {
                out = out.substring(0, idx) + bodyStr + out.substring(idx);
            } else {
                out = out + bodyStr;
            }
        }
        return out;
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
     * 构建 内容-Disposition 头。
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
     * 按扩展名推断 内容-类型。
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
     * 判定本次预览请求应交由客户端直渲染，并解析出对应的渲染器标识。
     *
     * <p>命中条件：未显式指定 {@code render=server}，且扩展名在
     * {@link ClientPreviewSupport} 的客户端可渲染清单内（docx / xlsx / xls / pdf / pptx）。
     * 传统 {@code .doc} / {@code .ppt}（OLE2 复合文档）与 {@code .odt} 系列
     * 不在清单内，将继续走服务端 SPI / 转换链路。</p>
     *
     * <p>对 {@code doc / xls / ppt} 这类传统扩展名，额外按<b>内容魔数</b>嗅探：
     * 若文件实际是 OOXML（扩展名写着 doc、内容其实是 docx），则按真实类型
     * 交给客户端组件渲染，避免误入 POI 二进制解析而失败。</p>
     *
     * @param request  请求对象，用于读取 {@code render} 参数
     * @param fileName 文件名（用于推断扩展名）
     * @param content  文件字节（用于魔数嗅探；可为 {@code null}）
     * @return 客户端渲染器标识；应走服务端渲染时返回 {@code null}
     */
    private String resolveClientRenderer(ServerRequest request, String fileName, byte[] content) {
        if (!ClientPreviewSupport.isClientPreferred(request.getParam(ClientPreviewSupport.PARAM_RENDER))) {
            return null;
        }
        return ClientPreviewSupport.rendererOfSniffed(content, ClientPreviewSupport.resolveExtension(fileName));
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
