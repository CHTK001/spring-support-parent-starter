package com.chua.starter.filesystem.support.properties;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
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
@Getter
@Setter
@ConfigurationProperties(prefix = FileStorageProperties.PREFIX)
public class FileStorageProperties {

    public static final String PREFIX = "plugin.filesystem";

    /**
     * 是否启用文件存储模块
     */
    public boolean enable = false;

    /**
     * 存储配置列表（支持多个存储后端）
     */
    public List<StorageConfig> storages = new ArrayList<>();

    /**
     * HTTP 文件服务器配置列表（支持多端口）
     */
    public List<ServerConfig> servers = new ArrayList<>();

    /**
     * 是否开启 webjars 资源服务
     */
    public boolean openWebjars = true;

    /**
     * 是否开启远程文件代理
     */
    public boolean openRemoteFile = false;

    /**
     * 是否开启预览功能
     */
    public boolean openPreview = true;

    /**
     * 是否开启下载功能
     */
    public boolean openDownload = true;

    /**
     * 是否开启 Range 断点续传
     */
    public boolean openRange = true;

    /**
     * 是否开启水印功能
     */
    public boolean openWatermark = false;

    /**
     * 水印文本或图片 URL
     */
    public String watermark;

    /**
     * 存储后端配置
     */
    @Data
    @Getter
    @Setter
    public static class StorageConfig {

        /**
         * 存储名称（唯一标识，用于 bucket 路由）
         */
        public String name;

        /**
         * 存储类型：minio, oss, cos, obs, local, ftp, sftp 等
         */
        public String type = "minio";

        /**
         * 服务端点 URL
         */
        public String endpoint;

        /**
         * 访问密钥 ID
         */
        public String accessKeyId;

        /**
         * 访问密钥 Secret
         */
        public String accessKeySecret;

        /**
         * 默认存储桶名称
         */
        public String bucket;

        /**
         * 区域（部分存储需要）
         */
        public String region;

        /**
         * 是否为默认存储
         */
        public boolean defaultStorage = false;

        /**
         * 本地存储根目录（type=local 时有效）
         */
        public String basePath;
    }

    /**
     * HTTP 文件服务器配置
     */
    @Data
    @Getter
    @Setter
    public static class ServerConfig {

        /**
         * 服务器名称
         */
        public String name = "default";

        /**
         * 是否启用
         */
        public boolean enable = true;

        /**
         * 监听主机
         */
        public String host = "0.0.0.0";

        /**
         * 监听端口
         */
        public int port = 9000;

        /**
         * 是否启用 HTTPS
         */
        public boolean ssl = false;

        /**
         * SSL 证书路径
         */
        public String sslCertPath;

        /**
         * SSL 证书密码
         */
        public String sslCertPassword;

        /**
         * 关联的存储名称列表（为空则关联所有存储）
         */
        public List<String> storageNames = new ArrayList<>();

        /**
         * 上下文路径
         */
        public String contextPath = "/";

        /**
         * 读取超时（毫秒）
         */
        public long readTimeoutMillis = 30000;

        /**
         * 写入超时（毫秒）
         */
        public long writeTimeoutMillis = 30000;
    }
}
