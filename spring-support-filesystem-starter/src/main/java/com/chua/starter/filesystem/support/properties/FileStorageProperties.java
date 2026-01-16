package com.chua.starter.filesystem.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件存储配置属性
 * <p>
 * 支持配置多个存储后端和多个 HTTP 文件服务器端口
 * </p>
 *
 * @author CH
 * @since 2024/12/28
 */
@Data
@ConfigurationProperties(prefix = FileStorageProperties.PREFIX)
public class FileStorageProperties {

    public static final String PREFIX = "plugin.filesystem";

    /**
     * 是否启用文件存储模块
     */
    private boolean enable = false;

    /**
     * 存储配置列表（支持多个存储后端）
     */
    private List<StorageConfig> storages = new ArrayList<>();

    /**
     * HTTP 文件服务器配置列表（支持多端口）
     */
    private List<ServerConfig> servers = new ArrayList<>();

    /**
     * 是否开启 webjars 资源服务
     */
    private boolean openWebjars = true;

    /**
     * 是否开启远程文件代理
     */
    private boolean openRemoteFile = false;

    /**
     * 是否开启预览功能
     */
    private boolean openPreview = true;

    /**
     * 是否开启下载功能
     */
    private boolean openDownload = true;

    /**
     * 是否开启 Range 断点续传
     */
    private boolean openRange = true;

    /**
     * 是否开启水印功能
     */
    private boolean openWatermark = false;

    /**
     * 水印文本或图片 URL
     */
    private String watermark;

    /**
     * 存储后端配置
     */
    @Data
    public static class StorageConfig {

        /**
         * 存储名称（唯一标识，用于 bucket 路由）
         */
        private String name;

        /**
         * 存储类型：minio, oss, cos, obs, local, ftp, sftp 等
         */
        private String type = "minio";

        /**
         * 服务端点 URL
         */
        private String endpoint;

        /**
         * 访问密钥 ID
         */
        private String accessKeyId;

        /**
         * 访问密钥 Secret
         */
        private String accessKeySecret;

        /**
         * 默认存储桶名称
         */
        private String bucket;

        /**
         * 区域（部分存储需要）
         */
        private String region;

        /**
         * 是否为默认存储
         */
        private boolean defaultStorage = false;

        /**
         * 本地存储根目录（type=local 时有效）
         */
        private String basePath;
    }

    /**
     * HTTP 文件服务器配置
     */
    @Data
    public static class ServerConfig {

        /**
         * 服务器名称
         */
        private String name = "default";

        /**
         * 是否启用
         */
        private boolean enable = true;

        /**
         * 监听主机
         */
        private String host = "0.0.0.0";

        /**
         * 监听端口
         */
        private int port = 9000;

        /**
         * 是否启用 HTTPS
         */
        private boolean ssl = false;

        /**
         * SSL 证书路径
         */
        private String sslCertPath;

        /**
         * SSL 证书密码
         */
        private String sslCertPassword;

        /**
         * 关联的存储名称列表（为空则关联所有存储）
         */
        private List<String> storageNames = new ArrayList<>();

        /**
         * 上下文路径
         */
        private String contextPath = "/";

        /**
         * 读取超时（毫秒）
         */
        private long readTimeoutMillis = 30000;

        /**
         * 写入超时（毫秒）
         */
        private long writeTimeoutMillis = 30000;
    }
}
