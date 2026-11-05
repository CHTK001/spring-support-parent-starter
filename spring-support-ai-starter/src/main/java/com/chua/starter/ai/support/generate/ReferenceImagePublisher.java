// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.generate;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import com.chua.starter.filesystem.support.server.FileStorageServerLauncher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 参考图发布器：把前端传来的 base64 参考图换成<b>公网可访问的限时地址</b>。
 *
 * <p>背景：部分视频协议（如 Agnes）的参考图字段 {@code first_frame} 只收
 * 「公网可访问的图片 URL」，既不收字节也不收 data URI。而本项目的参考图链路是
 * 前端 canvas 缩放 → data URI → 请求体 → 落库，全程 base64，拿不出公网地址。
 * 本组件借 {@code spring-support-filesystem-starter} 的文件服务器补上这一环：</p>
 *
 * <ol>
 *   <li>用 {@link FileStorage} SPI 把字节落到指定目录（默认 {@code ai-ref}）；</li>
 *   <li>向文件服务器的 {@code POST /ot?bucket=..&key=..&type=expiry&expires=..}
 *       申请一条<b>限时</b>访问链接，返回 {@code /ot/{token}}；</li>
 *   <li>拼成绝对地址交给协议使用。</li>
 * </ol>
 *
 * <p><b>为什么用限时链接而不是直接给 {@code /{bucket}/{key}}</b>：后者是永久公开地址，
 * 参考图一旦泄露就长期可访问。限时链接在任务生命周期内有效，到期自动失效，
 * 且可用 {@code type=once} 做到阅后即焚。</p>
 *
 * <p><b>部署前提</b>：文件服务器的 {@code publicHost} 必须配成<em>外部可达</em>的域名或 IP。
 * 未配置时退回 {@code host:port}，而默认 host 是 {@code 0.0.0.0} 会被折成
 * {@code 127.0.0.1} —— 那种地址协议方（Agnes 在公网）根本取不到，
 * 表现为「提交成功但生成结果不对」。因此本组件在检测到本地回环地址时
 * 记 warn 日志提示。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenceImagePublisher {

    /**
     * 默认存放参考图的存储桶名
     */
    public static final String DEFAULT_BUCKET = "ai-ref";

    /**
     * 默认存放目录
     */
    private static final String DEFAULT_DIR = "generate";

    /**
     * 限时链接有效期（秒）：30 分钟
     *
     * <p>要大于「提交 + 排队 + 生成」的最坏耗时，否则协议方还没取到图链接就过期了。</p>
     */
    private static final int LINK_TTL_SECONDS = 1800;

    /**
    /**
     * 文件服务器启动器（取对外地址）
     */
    private final ObjectProvider<FileStorageServerLauncher> launcherProvider;

    /**
     * 文件服务器配置（取 publicHost）
     */
    private final ObjectProvider<FileStorageServerProperties> propertiesProvider;

    /**
     * 把参考图字节发布为公网可访问的限时地址。
     *
     * @param bytes    参考图字节；为空直接返回 null
     * @param mimeType 图片 MIME 类型，用于决定落盘扩展名与上传 Content-Type
     * @return 公网可访问的限时地址；无法发布时返回 null
     */
    public String publish(byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String base = publicBaseUrl();
        if (base == null) {
            return null;
        }
        String fileName = UUID.randomUUID() + extensionOf(mimeType);
        String key = DEFAULT_DIR + "/" + fileName;
        // 走文件服务器自身的 API（PUT /{bucket}/{key}，请求体为原始字节），
        // 而不是直接依赖 FileStorage SPI —— 那会引入 utils-support-filestorage-starter，
        // 而本模块只需要 filesystem-starter 对外暴露的服务
        ClientResponse put = HttpClientFactory.of(base + DEFAULT_BUCKET + "/" + key)
                .header("Content-Type", mimeType == null ? "image/jpeg" : mimeType)
                .body(bytes)
                .connectTimeout(30000)
                .put();
        if (!put.isSuccess()) {
            log.warn("[Generate] 参考图上传失败 HTTP {}: {}", put.getStatusCode(), put.getBodyString());
            return null;
        }
        String oneTime = requestOneTimeLink(base, key);
        // 申请不到限时链接就退回直链：直链同样可访问，只是不过期
        return oneTime != null ? base + oneTime : base + DEFAULT_BUCKET + "/" + key;
    }

    /**
     * 向文件服务器申请一条限时访问链接。
     *
     * @param base 文件服务器基地址（以 / 结尾）
     * @param key  对象键
     * @return {@code /ot/{token}} 形式的路径；失败返回 null
     */
    private String requestOneTimeLink(String base, String key) {
        try {
            String url = base + "ot?bucket=" + enc(DEFAULT_BUCKET) + "&key=" + enc(key)
                    + "&type=expiry&expires=" + LINK_TTL_SECONDS;
            ClientResponse resp = HttpClientFactory.of(url)
                    .json()
                    .body("{}")
                    .connectTimeout(15000)
                    .post();
            if (!resp.isSuccess()) {
                log.warn("[Generate] 申请限时链接失败 HTTP {}: {}", resp.getStatusCode(), resp.getBodyString());
                return null;
            }
            Map<String, Object> body = Json.fromJson(resp.getBodyString(), Map.class);
            Object path = body.get("url");
            return path == null ? null : String.valueOf(path);
        } catch (Exception e) {
            log.warn("[Generate] 申请限时链接异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 取文件服务器对外可访问的基地址。
     *
     * <p>优先用 {@code publicHost}（协议方在公网，必须是这个）；未配置时退回启动器地址，
     * 并在解析成回环地址时告警——那种地址外部取不到。</p>
     *
     * @return 形如 {@code http://host:port/} 的地址；服务未启动返回 null
     */
    private String publicBaseUrl() {
        FileStorageServerLauncher launcher = launcherProvider.getIfAvailable();
        if (launcher == null || !launcher.isRunning()) {
            log.warn("[Generate] 文件服务器未启动，图生视频参考图无法发布");
            return null;
        }
        String port = null;
        FileStorageServerProperties properties = propertiesProvider.getIfAvailable();
        if (properties != null) {
            port = String.valueOf(properties.getPort());
            String publicHost = properties.getPublicHost();
            if (publicHost != null && !publicHost.isBlank()) {
                return scheme(properties) + "://" + publicHost + ":" + port + "/";
            }
        }
        String url = launcher.getServerUrl();
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("127.0.0.1") || lower.contains("localhost") || lower.contains("[::1]")) {
            log.warn("[Generate] 文件服务器对外地址为回环地址（{}），协议方在公网将取不到参考图；"
                    + "请配置 plugin.filesystem.storage-server.public-host 为外部可达的域名或 IP", url);
        }
        return url;
    }

    /**
     * 取对外协议前缀。
     *
     * @param properties 文件服务器配置
     * @return {@code http} 或 {@code https}
     */
    private static String scheme(FileStorageServerProperties properties) {
        String raw = properties.getHost() == null ? "" : properties.getHost();
        return raw.startsWith("https://") ? "https" : "http";
    }

    /**
     * 由 MIME 类型推断落盘扩展名。
     *
     * @param mimeType MIME 类型，可为 null
     * @return 含点的扩展名，识别不出返回 {@code .jpg}
     */
    private static String extensionOf(String mimeType) {
        if (mimeType == null) {
            return ".jpg";
        }
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/bmp" -> ".bmp";
            default -> ".jpg";
        };
    }

    /**
     * URL 编码查询参数。
     *
     * @param value 原始值
     * @return 编码后的值
     */
    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}