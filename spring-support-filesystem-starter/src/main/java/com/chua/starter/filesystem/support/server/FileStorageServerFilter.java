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

        // 访问模式收敛：显式 download 参数优先，其次按设置降级
        boolean wantDownload = Boolean.parseBoolean(request.getParam("download"));
        boolean download;
        if (wantDownload && setting.isOpenDownload()) {
            download = true;
        } else if (setting.isOpenPreview()) {
            download = false;
        } else if (setting.isOpenDownload()) {
            download = true;
        } else {
            return error(response, 403, "Access Denied: preview/download disabled");
        }
        String fileName = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
        response.setHeader("Content-Disposition",
                (download ? "attachment" : "inline") + "; filename=\"" + fileName + "\"");

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
    private CompletionStage<Void> handleRemoteFile(ServerRequest request, ServerResponse response, String remoteUrl) {
        try {
            byte[] data;
            try (InputStream in = URI.create(remoteUrl).toURL().openStream()) {
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
     * <p>执行 URL 解码，拒绝包含 {@code ..}、{@code :} 等危险片段的路径穿越 Key。</p>
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
     * 按扩展名推断 Content-Type。
     *
     * @param fileName 文件名
     * @return Content-Type
     */
    private String resolveContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".log")) {
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
        if (lower.endsWith(".css")) {
            return "text/css;charset=UTF-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript;charset=UTF-8";
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
