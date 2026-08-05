package com.chua.starter.filesystem.support.properties;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件存储配置属性 (多 Provider 模式)。
 *
 * <p>支持配置多种存储后端 (Provider)，每个 Provider 独立配置，
 * 参考 utils-support-common-starter 的 FileSystem SPI 多实现风格。</p>
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * plugin:
 *   filesystem:
 *     enable: true
 *     default-provider: local                            # 默认 Provider 名称
 *     open-preview: true
 *     open-download: true
 *     open-range: true
 *     open-webjars: true
 *     open-watermark: false
 *     servers:                                          # HTTP 文件服务器
 *       - name: default
 *         host: 0.0.0.0
 *         port: 9000
 *     providers:                                        # 多 Provider 配置
 *       local:
 *         bucket: default
 *         base-path: /data/uploads
 *       minio:
 *         bucket: backup
 *         endpoint: http://172.16.0.40:9000
 *         access-key: minioadmin
 *         secret-key: minioadmin
 *         region: us-east-1
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
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
    private boolean enable = false;

    /**
     * 是否开放服务器目录浏览接口（安全开关，默认关闭）
     * <p>开启后可通过 {@code GET /dir/list} 浏览服务器本地目录，用于前端目录选择器。
     * 生产环境建议保持关闭，避免泄露服务器目录结构。</p>
     */
    private boolean dirList = false;

    /**
     * 默认 Provider 名称 (用于 FileStorageTemplate.getDefaultStorage())
     */
    private String defaultProvider;

    /**
     * 多 Provider 配置列表 (按名称路由)
     */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    /**
     * HTTP 文件服务器配置列表
     */
    private List<ServerConfig> servers = new ArrayList<>();

    /**
     * 分片上传临时目录 (用于存储分片文件)
     * 不配置时默认使用系统临时目录 + /filesystem-chunks
     */
    private String tempPath;

    // ==================== 功能开关 ====================

    /**
     * 访问模式: view-仅预览, download-仅下载, both-两者都允许
     */
    private AccessMode mode = AccessMode.BOTH;

    private boolean openWebjars = true;
    private boolean openRemoteFile = false;

    /**
     * 访问 URL 后缀（可选，如 "?size=800x600&format=jpg"），生成访问地址时拼接
     */
    private String urlSuffix;

    /**
     * 图片处理与预览设置（对应底层 FileOperationSetting）
     */
    private FileOperationConfig operation;

    /**
     * 访问模式枚举。
     */
    public enum AccessMode {
        /**
         * 仅预览
         */
        VIEW,
        /**
         * 仅下载
         */
        DOWNLOAD,
        /**
         * 预览与下载都允许
         */
        BOTH
    }

    /**
     * 图片处理与预览配置（对应底层 FileOperationSetting）。
     */
    @Data
    @Getter
    @Setter
    public static class FileOperationConfig {
        /**
         * 输出尺寸，如 "800x600" 或 "800"（等比）
         */
        private String size;
        /**
         * 输出格式，如 "jpg"、"png"
         */
        private String format;
        /**
         * 图片质量 (1-100)
         */
        private Integer quality;
        /**
         * 裁剪区域，如 "100,100,200,200"
         */
        private String crop;
        /**
         * 旋转角度 (0-360)
         */
        private Integer rotate;
        /**
         * 翻转方向: "horizontal"/"vertical"
         */
        private String flip;
        /**
         * 是否灰度
         */
        private Boolean grayscale;
        /**
         * 模糊半径
         */
        private Float blur;
        /**
         * 锐化强度
         */
        private Float sharpen;
        /**
         * 自动旋转校正
         */
        private Boolean autoOrient;
        /**
         * 文字水印
         */
        private String watermarkText;
        /**
         * 水印图片 URL
         */
        private String watermarkImage;
        /**
         * 强制下载（预览时也走 attachment）
         */
        private Boolean forceDownload;
        /**
         * PDF 页码 (1-based)
         */
        private Integer pdfPage;
        /**
         * PDF 页面尺寸
         */
        private String pdfPageSize;
        /**
         * PDF 页面方向
         */
        private String pdfOrientation;
    }

    /**
     * 单个存储 Provider 配置 (对应一个 FileStorage SPI 实现)。
     */
    @Data
    @Getter
    @Setter
    public static class ProviderConfig {
        /**
         * 服务端点 URL (local/ftp 可空; minio/s3 例 http://10.0.0.1:9000)
         */
        private String endpoint;
        /**
         * Bucket 名称 (存储空间)
         */
        private String bucket;
        /**
         * 访问密钥 ID (local 可空)
         */
        private String accessKey;
        /**
         * 访问密钥 Secret (local 可空)
         */
        private String secretKey;
        /**
         * 区域 (local 可空; 例 cn-hangzhou, us-east-1)
         */
        private String region;
        /**
         * 本地存储根目录 (type=local 时有效)
         */
        private String basePath;
        /**
         * 连接超时 (毫秒)
         */
        private long connectionTimeout = 10_000;
        /**
         * 读取超时 (毫秒)
         */
        private long readTimeout = 30_000;
        /**
         * 写入超时 (毫秒)
         */
        private long writeTimeout = 30_000;
        /**
         * 是否使用 HTTPS
         */
        private boolean secure = false;
        /**
         * 自定义 SPI 类型 (留空时根据配置自动推断)
         *   - endpoint 为空 → local
         *   - endpoint 以 s3/amazonaws 开头 → s3
         *   - endpoint 包含 oss.aliyuncs → oss
         *   - 兜底 → minio (S3 兼容)
         */
        private String type;
    }

    /**
     * HTTP 文件服务器配置。
     */
    @Data
    @Getter
    @Setter
    public static class ServerConfig {
        private String name = "default";
        private boolean enable = true;
        private String type = "jdk";
        private String host = "0.0.0.0";
        private int port = 9000;
        private boolean ssl = false;
        private String sslCertPath;
        private String sslCertPassword;
        private List<String> providerNames = new ArrayList<>();
        private String contextPath = "/";
        private long readTimeoutMillis = 30_000;
        private long writeTimeoutMillis = 30_000;
        private List<String> filters = new ArrayList<>();
        /**
         * 最大请求大小（字节）
         */
        private long maxRequestSize = 10L * 1024 * 1024;
        /**
         * 最大连接数
         */
        private int maxConnections = 512;
        /**
         * 工作线程数
         */
        private int workerThreads = 8;
        /**
         * 响应超时（毫秒）
         */
        private long responseTimeoutMillis = 60_000;
        /**
         * 默认内容类型
         */
        private String contentType = "application/octet-stream";
        /**
         * 是否启用 gzip 压缩
         */
        private boolean gzipEnabled = false;
        /**
         * gzip 压缩最小字节数
         */
        private int gzipMinSize = 1024;
        /**
         * 字符集
         */
        private String charset = "UTF-8";
    }

    // ==================== 兼容老配置 (deprecated, 但保留解析能力) ====================

    /**
     * 旧版存储配置列表 (List&lt;StorageConfig&gt;)。
     * 新版本请改用 {@link #providers}, 此字段保留用于向后兼容解析。
     */
    @Deprecated
    private List<LegacyStorageConfig> storages = new ArrayList<>();

    /**
     * 旧版单存储配置 (用于平滑迁移)。
     */
    @Data
    @Getter
    @Setter
    @Deprecated
    public static class LegacyStorageConfig {
        private String name;
        private String type = "minio";
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucket;
        private String region;
        private boolean defaultStorage = false;
        private String basePath;
    }
}