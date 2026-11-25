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
     * HTTP 文件服务器配置（旧形态 {@code plugin.filesystem.servers[*]}，与
     * {@link FileStorageServerProperties} 的 {@code plugin.filesystem.storage-server} 相互独立）。
     *
     * <p>由 {@code FileServerManager} 在应用启动时逐个拉起；每个条目对应一个
     * 独立监听端口的 HTTP 文件服务，可分别绑定不同 Provider。</p>
     *
     * <h3>配置示例</h3>
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     servers:
     *       - name: default
     *         enable: true
     *         type: jdk
     *         host: 0.0.0.0
     *         port: 9000
     *         context-path: /fs
     *         provider-names: [local]
     * }</pre>
     * @author CH
     * @since 4.0.0
     */
    @Data
    @Getter
    @Setter
    public static class ServerConfig {
        /**
         * 服务名称（路由与展示用，同一应用内建议唯一）。
         *
         * <p>默认值：{@code "default"}。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         * }</pre>
         */
        private String name = "default";
        /**
         * 是否启用该文件服务器。
         *
         * <p>默认值：{@code true}。为 {@code false} 时该条目不启动，其余条目不受影响。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         enable: true
         * }</pre>
         */
        private boolean enable = true;
        /**
         * HTTP 服务器实现类型。
         *
         * <p>默认值：{@code "jdk"}（JDK 内置 HttpServer）。其它可选值以运行期注册的
         * 服务器实现为准，未注册的类型会在启动该条目时失败。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         type: jdk
         * }</pre>
         */
        private String type = "jdk";
        /**
         * 监听地址。
         *
         * <p>默认值：{@code "0.0.0.0"}（监听所有网卡；生产环境建议显式绑定内网地址）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         host: 0.0.0.0
         * }</pre>
         */
        private String host = "0.0.0.0";
        /**
         * 监听端口。
         *
         * <p>默认值：{@code 9000}。注意与 {@code storage-server}（默认 18082）及
         * 其它 servers 条目的端口互不冲突。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         port: 9000
         * }</pre>
         */
        private int port = 9000;
        /**
         * 是否启用 HTTPS。
         *
         * <p>默认值：{@code false}（HTTP）。开启后需同时配置
         * {@link #sslCertPath} 与 {@link #sslCertPassword}。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         ssl: false
         * }</pre>
         */
        private boolean ssl = false;
        /**
         * SSL 证书路径（{@code ssl: true} 时必填）。
         *
         * <p>默认值：无（null）。一般指向 PKCS12/JKS 格式证书文件
         * （classpath 内路径或文件系统绝对路径）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         ssl: true
         *         ssl-cert-path: /certs/server.p12
         *         ssl-cert-password: your-password
         * }</pre>
         */
        private String sslCertPath;
        /**
         * SSL 证书口令（{@code ssl: true} 时必填）。
         *
         * <p>默认值：无（null）。建议经环境变量或配置中心注入，勿明文提交到仓库。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         ssl-cert-password: your-password
         * }</pre>
         */
        private String sslCertPassword;
        /**
         * 该文件服务器绑定的 Provider 名称列表（对应 {@link #providers} 的 key）。
         *
         * <p>默认值：空列表（不额外限定；具体如何映射到 Provider 以
         * {@code FileServerManager} 装配逻辑为准）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         provider-names: [local]
         * }</pre>
         */
        private List<String> providerNames = new ArrayList<>();
        /**
         * 上下文路径（该服务的根路径）。
         *
         * <p>默认值：{@code "/"}（根路径）。配置为 {@code /fs} 后，
         * 预览/下载等接口挂在 {@code /fs/**} 下。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         context-path: /fs
         * }</pre>
         */
        private String contextPath = "/";
        /**
         * 读取超时（毫秒）。
         *
         * <p>默认值：{@code 30000}（30 秒）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         read-timeout-millis: 30000
         * }</pre>
         */
        private long readTimeoutMillis = 30_000;
        /**
         * 写入超时（毫秒）。
         *
         * <p>默认值：{@code 30000}（30 秒）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         write-timeout-millis: 30000
         * }</pre>
         */
        private long writeTimeoutMillis = 30_000;
        /**
         * 附加过滤器（服务器级，作用于该服务的所有请求）。
         *
         * <p>默认值：空列表（不追加额外过滤器）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         filters: []
         * }</pre>
         */
        private List<String> filters = new ArrayList<>();
        /**
         * 单次请求最大体积（字节），超过即拒绝。
         *
         * <p>默认值：{@code 10}MB（{@code 10L * 1024 * 1024}）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         max-request-size: 10485760
         * }</pre>
         */
        private long maxRequestSize = 10L * 1024 * 1024;
        /**
         * 最大并发连接数。
         *
         * <p>默认值：{@code 512}。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         max-connections: 512
         * }</pre>
         */
        private int maxConnections = 512;
        /**
         * 工作线程数。
         *
         * <p>默认值：{@code 8}。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         worker-threads: 8
         * }</pre>
         */
        private int workerThreads = 8;
        /**
         * 响应超时（毫秒）。
         *
         * <p>默认值：{@code 60000}（60 秒）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         response-timeout-millis: 60000
         * }</pre>
         */
        private long responseTimeoutMillis = 60_000;
        /**
         * 默认响应内容类型（无法从文件名推断时的兜底）。
         *
         * <p>默认值：{@code "application/octet-stream"}。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         content-type: application/octet-stream
         * }</pre>
         */
        private String contentType = "application/octet-stream";
        /**
         * 是否启用 gzip 压缩响应。
         *
         * <p>默认值：{@code false}。开启后超过 {@link #gzipMinSize} 的响应体压缩传输，
         * 对图片等已压缩内容收益有限。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         gzip-enabled: false
         * }</pre>
         */
        private boolean gzipEnabled = false;
        /**
         * 启用 gzip 压缩的最小响应体字节数（小于该值不压缩，避免负优化）。
         *
         * <p>默认值：{@code 1024}。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         gzip-min-size: 1024
         * }</pre>
         */
        private int gzipMinSize = 1024;
        /**
         * 响应字符集。
         *
         * <p>默认值：{@code "UTF-8"}。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     servers:
         *       - name: default
         *         charset: UTF-8
         * }</pre>
         */
        private String charset = "UTF-8";
    }

 // ==================== 兼容老配置 (已弃用, 但保留解析能力) ====================

    /**
     * 旧版存储配置列表 (列表&lt;storage配置&gt;)。
     * 新版本请改用 {@link #providers}, 此字段保留用于向后兼容解析。
     *
     * <p>默认值：空列表（未配置旧版 {@code storages} 时不影响新配置 {@code providers}）。</p>
     *
     * <p>旧版 YAML 示例（{@code plugin.filesystem.storages[*]}，已弃用，仅保留兼容解析）：</p>
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storages:
     *       - name: legacy-minio
     *         type: minio
     *         endpoint: http://127.0.0.1:9000
     *         access-key-id: your-access-key
     *         access-key-secret: your-secret-key
     *         bucket: my-bucket
     *         region: us-east-1
     *         default-storage: false
     *         base-path: files
     * }</pre>
     */
    @Deprecated
    private List<LegacyStorageConfig> storages = new ArrayList<>();

    /**
     * 旧版单存储配置（用于平滑迁移，已弃用）。
     *
     * <p>新配置请使用 {@link ProviderConfig}（{@code plugin.filesystem.providers.*}）；
     * 本类仅保留用于向后兼容解析旧版 {@code plugin.filesystem.storages[*]} 配置，
     * 不建议在新代码中依赖。</p>
     *
     * <pre>{@code
     * plugin:
     *   filesystem:
     *     storages:
     *       - name: legacy-minio
     *         type: minio
     *         endpoint: http://127.0.0.1:9000
     *         access-key-id: your-access-key
     *         access-key-secret: your-secret-key
     *         bucket: my-bucket
     *         region: us-east-1
     *         default-storage: false
     *         base-path: files
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    @Data
    @Getter
    @Setter
    @Deprecated
    public static class LegacyStorageConfig {
        /**
         * 存储名称（建议唯一，用于关联默认存储等）。
         *
         * <p>默认值：无（null）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storages:
         *       - name: legacy-minio
         * }</pre>
         */
        private String name;
        /**
         * 存储实现类型。
         *
         * <p>默认值：{@code "minio"}。旧版常见取值：minio / oss / cos / obs / s3 / local，
         * 具体以运行期注册的 Provider 类型为准。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storages:
         *       - name: legacy-minio
         *         type: minio
         * }</pre>
         */
        private String type = "minio";
        /**
         * 服务端点（如 S3/MinIO 的访问地址）。
         *
         * <p>默认值：无（null）；本地存储一般无需配置。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storages:
         *       - name: legacy-minio
         *         endpoint: http://127.0.0.1:9000
         * }</pre>
        */
        private String endpoint;
        /**
         * Access Key ID（对象存储访问密钥标识）。
         *
         * <p>默认值：无（null）。建议经环境变量或配置中心注入，勿明文提交到仓库。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storages:
         *       - name: legacy-minio
         *         access-key-id: your-access-key
         * }</pre>
        */
        private String accessKeyId;
        /**
         * Access Key Secret（对象存储访问密钥）。
         *
         * <p>默认值：无（null）。建议经环境变量或配置中心注入，勿明文提交到仓库。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storages:
         *       - name: legacy-minio
         *         access-key-secret: your-secret-key
         * }</pre>
        */
        private String accessKeySecret;
        /**
         * 存储桶名称。
         *
         * <p>默认值：无（null）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storages:
         *       - name: legacy-minio
         *         bucket: my-bucket
         * }</pre>
        */
        private String bucket;
        /**
         * 区域（如 {@code us-east-1}；部分云厂商必填）。
         *
         * <p>默认值：无（null）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storages:
         *       - name: legacy-minio
         *         region: us-east-1
         * }</pre>
        */
        private String region;
        /**
         * 是否作为默认存储。
         *
         * <p>默认值：{@code false}。多个存储都未标记默认时，运行期通常回落到
         * 第一个注册成功的存储。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storages:
         *       - name: legacy-minio
         *         default-storage: false
         * }</pre>
        */
        private boolean defaultStorage = false;
        /**
         * 对象在存储内的基础路径（前缀目录）。
         *
         * <p>默认值：无（null，即桶根路径）。</p>
         *
         * <pre>{@code
         * plugin:
         *   filesystem:
         *     storages:
         *       - name: legacy-minio
         *         base-path: files
         * }</pre>
        */
        private String basePath;
    }
}
