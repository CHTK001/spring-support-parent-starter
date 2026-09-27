package com.chua.starter.filesystem.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储服务器配置属性（随应用自动启动的独立文件存储网关）。
 *
 * <p>配置示例:
 * <pre>
 * plugin:
 *   filesystem:
 *     storage-server:
 *       enable: true          # 是否随应用启动文件服务器
 *       host: 0.0.0.0         # 监听地址
 *       port: 18080           # 监听端口（与前端"文件服务器"页面默认地址对齐）
 *       type: jdk             # 服务器引擎: jdk/nio/aio
 *       open-preview: true    # 允许 ?preview 内联预览
 *       open-download: true   # 允许 ?download 附件下载
 *       open-range: true      # 允许 Range 分段下载
 *       layout: true          # 目录列表页
 *       layout-type: SIMPLE   # 列表布局: SIMPLE/SYSTEM
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = FileStorageServerProperties.PRE)
public class FileStorageServerProperties {

    /**
     * 配置属性前缀
     */
    public static final String PRE = "plugin.filesystem.storage-server";

    /**
     * 是否随应用启动文件服务器
     */
    private boolean enable = true;

    /**
     * 监听地址
     */
    private String host = "0.0.0.0";

    /**
     * 对外可达地址（用于 /v2/setting/default 下发的文件服务器地址）；
     * 留空时回退 {@link #host}（0.0.0.0 归一为 127.0.0.1）。
     */
    private String publicHost;

    /**
     * 监听端口（默认与前端文件服务器页 DEFAULT_SERVER 对齐）
     */
    private int port = 18080;

    /**
     * 服务器引擎：jdk / nio / aio
     */
    private String type = "jdk";

    /**
     * 允许内联预览（?preview）
     */
    private boolean openPreview = true;

    /**
     * 允许附件下载（?download）
     */
    private boolean openDownload = true;

    /**
     * 允许 Range 分段下载
     */
    private boolean openRange = true;

    /**
     * 启用水印响应头
     */
    private boolean openWatermark = false;

    /**
     * 启用 webjars 静态资源（预览页依赖）
     */
    private boolean openWebjars = true;

    /**
     * 允许远程文件透传（http(s) 开头的对象键）
     */
    private boolean openRemoteFile = false;

    /**
     * 是否开启目录文件列表页面
     */
    private boolean layout = true;

    /**
     * 文件列表布局类型：SIMPLE-简单文件列表树 / SYSTEM-资源管理器风格
     */
    private String layoutType = "SIMPLE";

    /**
     * 水印文本
     */
    private String watermark;

    /**
     * 图片滤镜 SPI 名称（NONE 表示原图）
     */
    private String imageFilter = "NONE";

    /**
     * 私有桶（逗号分隔，provider 名；{@code *} 表示全部私有）。
     * <p>启动时作为 OAuth 鉴权过滤器（oauth 模块，反射挂载）的初始私有桶；
     * 运行期可在文件服务器页「系统配置」热更新（写入 FileStorageSetting.privateBuckets）。
     * 留空时回退环境配置 {@code plugin.oauth.file-storage.private-buckets}。</p>
     */
    private String privateBuckets;

    /**
     * 允许嵌入本文件服务器的来源（CSP {@code frame-ancestors}）。
     * <p>文件服务器预览统一走 iframe，需允许被前端页面跨源嵌入；默认 {@code *}。
     * 该值同时用于关闭 X-Frame-Options（避免 DENY 阻断 iframe 渲染）。</p>
     */
    private String frameAncestors = "*";
}
