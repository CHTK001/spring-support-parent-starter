package com.chua.starter.filesystem.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.filesystem.support.pojo.FileServerConfigResponse;
import com.chua.starter.filesystem.support.properties.FileStorageProperties;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件服务器运行期配置接口。
 *
 * <p>前端进入文件管理页前，先请求本接口获取「文件服务器当前部署形态与连接地址」，
 * 再决定用嵌入式相对路径还是独立端口，而不是写死 {@code http://host:port}。</p>
 *
 * <p>返回模型与 {@code /v2/setting/default} 的
 * {@code other.filestorage.mode + server} 一致：embedded 返回应用内相对路径，
 * standalone 返回完整地址。本接口是 filesystem-starter 自带的权威来源，
 * 前端优先级：本接口 > localStorage > app.yaml 兜底。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/v2/file")
@Tag(name = "文件服务器配置接口")
public class FileServerConfigController {

    /**
     * 文件服务器配置（部署形态 / 路径前缀 / 端口）
     */
    private final FileStorageServerProperties serverProperties;

    /**
     * 文件存储配置（默认 provider = 默认存储桶）
     */
    private final FileStorageProperties storageProperties;

    /**
     * 应用上下文路径（如 {@code /system/api}），embedded 拼路径用
     */
    private final String contextPath;

    /**
     * 构造器。
     *
     * @param serverProperties 文件服务器配置
     * @param storageProperties 文件存储配置
     * @param contextPath      应用上下文路径（如 {@code /system/api}）
     */
    public FileServerConfigController(
            FileStorageServerProperties serverProperties,
            FileStorageProperties storageProperties,
            @Value("${server.servlet.context-path:}") String contextPath) {
        this.serverProperties = serverProperties;
        this.storageProperties = storageProperties;
        this.contextPath = contextPath == null ? "" : contextPath;
    }

    /**
     * 读取文件服务器当前运行期配置。
     *
     * <p>embedded（默认）：{@code server} 为 {@code contextPath + path-prefix}
     * 相对路径（如 {@code /fs} 或 {@code /system/api/fs}），由前端拼当前 origin；
     * standalone：{@code server} 为 {@code http://host:port}。</p>
     *
     * @return 文件服务器配置
     */
    @Operation(summary = "文件服务器当前配置")
    @GetMapping("/server-config")
    public ReturnResult<FileServerConfigResponse> serverConfig() {
        String mode = serverProperties.isStandalone() ? "standalone" : "embedded";
        String server;
        if (serverProperties.isStandalone()) {
            String host = resolvePublicHost(serverProperties);
            server = "http://" + host + ":" + serverProperties.getPort();
        } else {
            server = joinPath(contextPath, serverProperties.normalizedPathPrefix());
        }
        String bucket = storageProperties.getDefaultProvider();
        return ReturnResult.ok(new FileServerConfigResponse(
                mode, server, serverProperties.normalizedPathPrefix(), bucket));
    }

    /**
     * standalone 对外地址：优先 publicHost，其次 host；空 / 0.0.0.0 归一为 127.0.0.1。
     * @param properties 参数
     * @return 返回值
    */
    private static String resolvePublicHost(FileStorageServerProperties properties) {
        String publicHost = properties.getPublicHost();
        if (publicHost != null && !publicHost.isBlank() && !"0.0.0.0".equals(publicHost)) {
            return publicHost;
        }
        String host = properties.getHost();
        return host == null || host.isBlank() || "0.0.0.0".equals(host) ? "127.0.0.1" : host;
    }

    /**
     * 拼接上下文路径与前缀，只保留一个斜杠边界。
     *
     * @param base 上下文路径（可为空）
     * @param tail 路径前缀（可为空 / {@code /} / {@code /fs}）
     * @return 拼接后的相对路径
     */
    private static String joinPath(String base, String tail) {
        String b = base == null ? "" : base.trim();
        String t = tail == null ? "" : tail.trim();
        if (t.equals("/")) {
            t = "";
        }
        while (b.startsWith("/")) {
            b = b.substring(1);
        }
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        while (t.startsWith("/")) {
            t = t.substring(1);
        }
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        if (b.isEmpty() && t.isEmpty()) {
            return "/";
        }
        return "/" + (b.isEmpty() ? "" : b + "/") + t;
    }
}
