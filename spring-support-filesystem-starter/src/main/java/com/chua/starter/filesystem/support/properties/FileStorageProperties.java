// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
 * 文件存储配置属性 (多 提供者 模式)。
 *
 * <p>支持配置多种存储后端 (Provider)，每个 Provider 独立配置，
 * 参考 utils-support-common-starter 的 文件系统 SPI 多实现风格。</p>
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * plugin:
 *   filesystem:
 *     enable: true                    # 模块总开关（默认 false，必须显式开启）
 *     default-provider: local         # 默认 Provider 名称（缺省取第一个注册成功的）
 *     mode: both                      # 访问模式: view / download / both（默认 both）
 *     dir-list: false                 # 是否开放本机目录浏览接口（默认 false，安全开关）
 *     url-suffix: ""                  # 生成访问地址时拼接的后缀（可选）
 *     open-webjars: true              # webjars 静态资源（预览页依赖，默认 true）
 *     open-remote-file: false         # 允许 http(s)/ssh 开头对象键远程透传（默认 false）
 *     task-expire-minutes: 60         # 上传任务过期回收时间（分钟，默认 60）
 *     task-sync-max-mb: 5             # 小文件同步直传阈值 MB（0 表示全部走任务）
 *     providers:                      # 多 Provider 配置（key 为 Provider 名）
 *       local:
 *         type: local                 # 留空时按 endpoint 自动推断
 *         bucket: default
 *         base-path: /data/uploads
 *       minio:
 *         bucket: backup
 *         endpoint: http://your-minio-host:9000
 *         access-key: your-access-key
 *         secret-key: your-secret-key
 *         region: us-east-1
 *     collab:                         # 协同文档定时落盘
 *       persist-enable: true
 *       persist-seconds: 30
 * }</pre>
 *
 * <p>注意：{@code open-preview} / {@code open-download} / {@code open-range} /
 * {@code open-watermark} 等文件服务器行为开关位于
 * {@link FileStorageServerProperties}（前缀 {@code plugin.filesystem.storage-server}），
 * 不在本前缀下。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@Getter
@Setter
@ConfigurationProperties(prefix = FileStorageProperties.PREFIX)
public class FileStorageProperties {

    /**
     * 前缀
    */
    public static final String PREFIX = "plugin.filesystem";

    /**
     * 是否启用文件存储模块（总开关）。
     *
     * <p>默认值：{@code false}（关闭）。为 false 时整个
     * {@code FileSystemAutoConfiguration} 不装配，所有 Bean 与 HTTP 接口均不存在，
     * 必须显式配置为 {@code true}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     enable: true
     * }</pre>
     */
    private boolean enable = false;

    /**
     * 是否开放服务器目录浏览接口（安全开关，默认关闭）。
     *
     * <p>开启后可通过 {@code GET /dir/list} 浏览服务器本地目录，用于前端目录选择器。
     * 生产环境建议保持关闭，避免泄露服务器目录结构。</p>
     *
     * <p>默认值：{@code false}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     dir-list: false
     * }</pre>
     */
    private boolean dirList = false;

    /**
     * 默认 提供者 名称 (用于 文件storagetemplate.获取默认storage())。
     *
     * <p>默认值：无（null）。为空时回落到第一个注册成功的 Provider；
     * 也可通过 {@link com.chua.starter.filesystem.support.template.FileStorageTemplate#setDefaultStorage(String)}
     * 在运行期切换默认存储。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     default-provider: local
     * }</pre>
     */
    private String defaultProvider;

    /**
     * 多 提供者 配置列表 (按名称路由)。
     *
     * <p>默认值：空 Map。key 为 Provider 名（业务侧按名路由），value 见
     * {@link ProviderConfig}；未配置任何 Provider 时上传会报「未配置文件存储提供者」，
     * 至少要配置一个（如 {@code providers.local}）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     providers:
     *       local:
     *         type: local
     *         bucket: default
     *         base-path: /data/uploads
     * }</pre>
     */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    /**
     * HTTP 文件服务器配置列表（旧形态，与 {@code plugin.filesystem.storage-server} 相互独立）。
     *
     * <p>默认值：空列表（不启动任何旧形态服务）。由 {@code FileServerManager} 在启动时
     * 逐个拉起；与 {@code storage-server} 同时开启会各起一套服务，排查端口占用时两处都要看。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     servers:
     *       - name: default
     *         host: 0.0.0.0
     *         port: 9000
     * }</pre>
     */
    private List<ServerConfig> servers = new ArrayList<>();

    /**
     * 分片上传临时目录 (用于存储分片文件)。
     *
     * <p><b>当前仅声明、代码未读取</b>：实际上传分片暂存目录由 {@code UploadTaskManager}
     * 在系统临时目录下随机创建（前缀 {@code upload-task-chunks} / {@code upload-task-merge}），
     * 配置本项不会改变暂存位置。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     temp-path: /data/tmp/fs-chunks   # 当前未生效，仅保留声明
     * }</pre>
     */
    private String tempPath;

    /**
     * 上传任务过期分钟数：超时仍无新分片活动的任务标记失败并回收分片。
     *
     * <p>默认值：{@code 60}（分钟）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     task-expire-minutes: 60
     * }</pre>
     */
    private long taskExpireMinutes = 60;

    /**
     * 小文件同步直传阈值（MB）：不超过该大小的单文件直传同步写存储立即返回，不建任务。
     *
     * <p>默认值：{@code 5}（MB）；配置为 {@code 0} 时表示全部文件都走任务化分片上传。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     task-sync-max-mb: 5
     * }</pre>
     */
    private long taskSyncMaxMb = 5;

    /**
     * 协同文档持久化配置（内存态 + 定时落盘）。
     *
     * <p>协同文档本身是内存态（快照式协同，读写都在内存里，编辑零阻塞），
     * 这里配置的是「多久把整库刷到磁盘一次」，用于抗进程重启。</p>
     *
     * <p>默认值：见 {@link CollabPersistConfig}（{@code persist-enable=true}、
     * {@code persist-seconds=30}；两个路径留空时回落 {@code data/file-collab/*}）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     collab:
     *       persist-enable: true
     *       persist-path: data/file-collab/collab-store.json
     *       persist-seconds: 30
     *       hub-path: data/file-collab/hub
     * }</pre>
     */
    private CollabPersistConfig collab = new CollabPersistConfig();

    /**
     * 协同文档落盘配置。
     *
     * <h3>配置示例</h3>
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     collab:
     *       persist-enable: true                                  # 是否定时落盘（默认 true）
     *       persist-path: data/file-collab/collab-store.json      # 快照落盘文件（缺省回落此值）
     *       persist-seconds: 30                                   # 落盘间隔秒（默认 30，≤0 按 30）
     *       hub-path: data/file-collab/hub                        # 实时操作日志目录（空串=纯内存）
     * }</pre>
     */
    @Data
    public static class CollabPersistConfig {

        /**
         * 是否启用定时落盘。
         *
         * <p>默认值：{@code true}；配置为 {@code false} 则协同文档重启即丢（纯内存态）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     collab:
         *       persist-enable: true
         * }</pre>
         */
        private boolean persistEnable = true;

        /**
         * 落盘文件路径（相对进程工作目录）。
         *
         * <p>默认值：无（null/空串时回落 {@code data/file-collab/collab-store.json}，
         * 由装配逻辑补齐）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     collab:
         *       persist-path: data/file-collab/collab-store.json
         * }</pre>
         */
        private String persistPath;

        /**
         * 落盘间隔（秒）。
         *
         * <p>默认值：{@code 30}；配置 {@code ≤0} 时按 30 秒处理
         * （见 {@code FileCollabPersistence} 构造器）。
         * 间隔越小丢数据窗口越小、写盘越频繁；协同快照较大，30s 是「几乎无感 IO」
         * 与「最多丢半分钟改动」之间的折中。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     collab:
         *       persist-seconds: 30
         * }</pre>
         */
        private long persistSeconds = 30;

        /**
         * 实时协同 hub 的日志目录（相对进程工作目录）。
         *
         * <p>默认值：无（null/空串时回落 {@code data/file-collab/hub}）；
         * 显式配置为空串表示纯内存（重启即丢日志，只适合本地调试）。</p>
         *
         * <p>hub 保存的是「快照 + 操作日志」，与文档快照本身分开存：前者约束实时
         * 编辑的顺序与续接，后者是用户看到的文件内容。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     collab:
         *       hub-path: data/file-collab/hub
         * }</pre>
         */
        private String hubPath;
    }

    // ==================== 功能开关 ====================

    /**
     * 访问模式: view-仅预览, download-仅下载, both-两者都允许。
     *
     * <p>默认值：{@link AccessMode#BOTH}（预览与下载都允许）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     mode: both
     * }</pre>
     */
    private AccessMode mode = AccessMode.BOTH;

    /**
     * Openwebjars — 是否开放 webjars 静态资源（预览页依赖）。
     *
     * <p>默认值：{@code true}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     open-webjars: true
     * }</pre>
    */
    private boolean openWebjars = true;
    /**
     * 打开远程文件 — 是否允许 http(s)/ssh 开头的对象键远程透传读取。
     *
     * <p>默认值：{@code false}（关闭，避免被当作开放代理滥用）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     open-remote-file: false
     * }</pre>
    */
    private boolean openRemoteFile = false;

    /**
     * 访问 URL 后缀（可选），生成访问地址时拼接。
     *
     * <p>默认值：无（null，不拼接任何后缀）。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     url-suffix: "?size=800x600&format=jpg"
     * }</pre>
     */
    private String urlSuffix;

    /**
     * 图片处理与预览设置（对应底层 文件operationsetting）。
     *
     * <p>默认值：无（null，不配置则不做对应处理）；各字段见 {@link FileOperationConfig}。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     operation:
     *       size: 800x600
     *       format: jpg
     *       quality: 80
     * }</pre>
     */
    private FileOperationConfig operation;

    /**
     * 访问模式枚举。
     * @author CH
     * @since 4.0.0
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
     * 图片处理与预览配置（对应底层 文件operationsetting）。
     *
     * <p>所有字段均可空，不配置则不做对应处理；仅对图片/PDF 类预览与转码生效。</p>
     *
     * <h3>配置示例</h3>
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     operation:
     *       size: 800x600            # 输出尺寸
     *       format: jpg              # 输出格式 jpg/png/webp
     *       quality: 80              # 图片质量 1-100
     *       watermark-text: your-text  # 文字水印
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    @Data
    @Getter
    @Setter
    public static class FileOperationConfig {
        /**
         * 输出尺寸，如 "800x600" 或 "800"（等比）。
         *
         * <p>默认值：无（null，不调整尺寸）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       size: 800x600
         * }</pre>
         */
        private String size;
        /**
         * 输出格式，如 "jpg"、"png"。
         *
         * <p>默认值：无（null，保持原格式）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       format: jpg
         * }</pre>
         */
        private String format;
        /**
         * 图片质量 (1-100)。
         *
         * <p>默认值：无（null，使用默认质量）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       quality: 80
         * }</pre>
         */
        private Integer quality;
        /**
         * 裁剪区域，如 "100,100,200,200"。
         *
         * <p>默认值：无（null，不裁剪）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       crop: "100,100,200,200"
         * }</pre>
         */
        private String crop;
        /**
         * 旋转角度 (0-360)。
         *
         * <p>默认值：无（null，不旋转）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       rotate: 90
         * }</pre>
         */
        private Integer rotate;
        /**
         * 翻转方向: "horizontal"/"vertical"。
         *
         * <p>默认值：无（null，不翻转）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       flip: horizontal
         * }</pre>
         */
        private String flip;
        /**
         * 是否灰度。
         *
         * <p>默认值：无（null，按 false 处理，不置灰）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       grayscale: true
         * }</pre>
         */
        private Boolean grayscale;
        /**
         * 模糊半径。
         *
         * <p>默认值：无（null，不模糊）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       blur: 2.0
         * }</pre>
         */
        private Float blur;
        /**
         * 锐化强度。
         *
         * <p>默认值：无（null，不锐化）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       sharpen: 1.0
         * }</pre>
         */
        private Float sharpen;
        /**
         * 自动旋转校正。
         *
         * <p>默认值：无（null，按 false 处理，不按 EXIF 方向校正）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       auto-orient: true
         * }</pre>
         */
        private Boolean autoOrient;
        /**
         * 文字水印。
         *
         * <p>默认值：无（null，不加文字水印）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       watermark-text: your-watermark-text
         * }</pre>
         */
        private String watermarkText;
        /**
         * 水印图片 URL。
         *
         * <p>默认值：无（null，不加图片水印）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       watermark-image: https://your-host/watermark.png
         * }</pre>
         */
        private String watermarkImage;
        /**
         * 强制下载（预览时也走 attachment）。
         *
         * <p>默认值：无（null，按 false 处理，允许内联预览）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       force-download: true
         * }</pre>
         */
        private Boolean forceDownload;
        /**
         * PDF 页码 (1-based)。
         *
         * <p>默认值：无（null，取第 1 页）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       pdf-page: 1
         * }</pre>
         */
        private Integer pdfPage;
        /**
         * PDF 页面尺寸。
         *
         * <p>默认值：无（null，使用原始页面尺寸）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       pdf-page-size: A4
         * }</pre>
         */
        private String pdfPageSize;
        /**
         * PDF 页面方向。
         *
         * <p>默认值：无（null，使用原始方向）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     operation:
         *       pdf-orientation: landscape
         * }</pre>
         */
        private String pdfOrientation;
    }

    /**
     * 单个存储 提供者 配置 (对应一个 文件storage SPI 实现)。
     *
     * <h3>配置示例</h3>
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     providers:
     *       local:                   # Provider 名（业务按名路由）
     *         type: local            # 留空按 endpoint 自动推断
     *         bucket: default
     *         base-path: /data/uploads
     *       minio:
     *         endpoint: http://your-minio-host:9000
     *         bucket: backup
     *         access-key: your-access-key
     *         secret-key: your-secret-key
     *         region: us-east-1
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    @Data
    @Getter
    @Setter
    public static class ProviderConfig {
        /**
         * 服务端点 URL (本地/ftp 可空; minio/s3 例 http://your-host:9000)。
         *
         * <p>默认值：无（null）。为空且未显式配置 {@code type} 时按 local 处理。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     providers:
         *       minio:
         *         endpoint: http://your-minio-host:9000
         * }</pre>
         */
        private String endpoint;
        /**
         * Bucket 名称 (存储空间)。
         *
         * <p>默认值：无（null，建议显式配置）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     providers:
         *       local:
         *         bucket: default
         * }</pre>
         */
        private String bucket;
        /**
         * 访问密钥 标识 (本地 可空)。
         *
         * <p>默认值：无（null；本地存储可空，云存储必填）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     providers:
         *       minio:
         *         access-key: your-access-key
         * }</pre>
         */
        private String accessKey;
        /**
         * 访问密钥 Secret (本地 可空)。
         *
         * <p>默认值：无（null；本地存储可空，云存储必填；建议经环境变量/配置中心注入，勿明文提交）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     providers:
         *       minio:
         *         secret-key: your-secret-key
         * }</pre>
         */
        private String secretKey;
        /**
         * 区域 (本地 可空; 例 cn-hangzhou, us-east-1)。
         *
         * <p>默认值：无（null；部分云厂商必填）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     providers:
         *       minio:
         *         region: us-east-1
         * }</pre>
         */
        private String region;
        /**
         * 本地存储根目录 (类型=本地 时有效)。
         *
         * <p>默认值：无（null）；对象实际落盘到 {@code base-path/bucket} 目录下。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     providers:
         *       local:
         *         base-path: /data/uploads
         * }</pre>
         */
        private String basePath;
        /**
         * 连接超时 (毫秒)。
         *
         * <p>默认值：{@code 10000}（10 秒）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     providers:
         *       minio:
         *         connection-timeout: 10000
         * }</pre>
         */
        private long connectionTimeout = 10_000;
        /**
         * 读取超时 (毫秒)。
         *
         * <p>默认值：{@code 30000}（30 秒）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     providers:
         *       minio:
         *         read-timeout: 30000
         * }</pre>
         */
        private long readTimeout = 30_000;
        /**
         * 写入超时 (毫秒)。
         *
         * <p>默认值：{@code 30000}（30 秒）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     providers:
         *       minio:
         *         write-timeout: 30000
         * }</pre>
         */
        private long writeTimeout = 30_000;
        /**
         * 是否使用 HTTPS。
         *
         * <p>默认值：{@code false}（HTTP）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     providers:
         *       minio:
         *         secure: false
         * }</pre>
         */
        private boolean secure = false;
        /**
         * 自定义 SPI 类型 (留空时根据 endpoint 自动推断)。
         *
         * <p>推断规则（见 {@code FileStorageTemplate#inferType}）：{@code type} 显式配置优先；
         * 否则 endpoint 为空 → {@code local}；含 {@code oss.aliyuncs} → {@code oss}；
         * 含 {@code myhuaweicloud} 或 {@code obs.} → {@code obs}；
         * 含 {@code cos.} 或 {@code tencentyun} → {@code cos}；
         * 含 {@code amazonaws} → {@code s3}；其它非空 → {@code minio}（S3 兼容）。</p>
         *
         * <p>默认值：无（null，自动推断）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     providers:
         *       local:
         *         type: local
         * }</pre>
         */
        private String type;
    }

    /**
     * HTTP 文件服务器配置。
     * @author CH
     * @since 4.0.0
     */
    @Data
    @Getter
    @Setter
    public static class ServerConfig {
        /**
         * 名称
        */
        private String name = "default";
        /**
         * Enable
        */
        private boolean enable = true;
        /**
         * 类型
        */
        private String type = "jdk";
        /**
         * 主机
        */
        private String host = "0.0.0.0";
        /**
         * 端口
        */
        private int port = 9000;
        /**
         * SSL
        */
        private boolean ssl = false;
        /**
         * SSL证书路径
        */
        private String sslCertPath;
        /**
         * SSL证书密码
        */
        private String sslCertPassword;
        /**
         * 提供者名称
        */
        private List<String> providerNames = new ArrayList<>();
        /**
         * 上下文路径
        */
        private String contextPath = "/";
        /**
         * 读取超时毫秒
        */
        private long readTimeoutMillis = 30_000;
        /**
         * 写入超时毫秒
        */
        private long writeTimeoutMillis = 30_000;
        /**
         * 过滤器
        */
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

 // ==================== 兼容老配置 (已弃用, 但保留解析能力) ====================

    /**
     * 旧版存储配置列表 (列表&lt;storage配置&gt;)。
     * 新版本请改用 {@link #providers}, 此字段保留用于向后兼容解析。
     */
    @Deprecated
    /**
     * Storages
    */
    private List<LegacyStorageConfig> storages = new ArrayList<>();

    /**
     * 旧版单存储配置 (用于平滑迁移)。
     * @author CH
     * @since 4.0.0
     */
    @Data
    @Getter
    @Setter
    @Deprecated
    public static class LegacyStorageConfig {
        /**
         * 名称
        */
        private String name;
        /**
         * 类型
        */
        private String type = "minio";
        /**
         * 端点
        */
        private String endpoint;
        /**
         * Access密钥标识
        */
        private String accessKeyId;
        /**
         * Access密钥secret
        */
        private String accessKeySecret;
        /**
         * 存储桶
        */
        private String bucket;
        /**
         * 区域
        */
        private String region;
        /**
         * 默认存储
        */
        private boolean defaultStorage = false;
        /**
         * 基础路径
        */
        private String basePath;
    }
}
