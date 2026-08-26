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
     * 创建 FileStorageServerFilter 实例
     * @param setting setting
     */
    public FileStorageServerFilter(FileStorageSetting setting) {
        this.setting = setting == null ? FileStorageSetting.builder().build() : setting;
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
                    return handleList(storage, bucket, response);
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
     * @param storage  存储实例
     * @param bucket   存储名
     * @param response 响应
     * @return 异步阶段
     */
    private CompletionStage<Void> handleList(FileStorage storage, String bucket, ServerResponse response) {
        applyWatermarkHeader(response);
        ListObjectResult result = storage.listObject(ListObjectRequest.builder()
                .limit(Integer.MAX_VALUE)
                .build());
        if (result.getResultCode() != ObjectResult.ResultCode.SUCCESS) {
            return error(response, 500, "List Objects Failed");
        }
        return writeList(bucket, "", result, response);
    }

    /**
     * 写出对象列表 JSON 响应。
     *
     * @param bucket 存储名
     * @param dir    目录路径（根目录为空字符串）
     * @param result 列表结果
     * @param response 响应
     * @return 异步阶段
     */
    private CompletionStage<Void> writeList(String bucket, String dir, ListObjectResult result, ServerResponse response) {
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
                return writeList(bucket, key, list, response);
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
                return writeList(bucket, key, list, response);
            }
            return error(response, 404, "Directory Not Found: " + key);
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
     * 尝试通过 SPI 预览提供者转换文件为 HTML 预览页。
     *
     * <p>classpath 中存在 utils-support-filestorage-starter 时，可预览
     * xlsx/xls/docx/doc/pptx/ppt（Univer）、odt/ods/odp（OpenDocument）、vsd（Visio）等；
     * 无可用提供者或转换失败时返回 null，回退为原始字节响应。</p>
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
                log.warn("SPI 预览提供者 {} 转换失败: {}", provider.getClass().getSimpleName(), e.getMessage());
            }
        }
        return null;
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
}
