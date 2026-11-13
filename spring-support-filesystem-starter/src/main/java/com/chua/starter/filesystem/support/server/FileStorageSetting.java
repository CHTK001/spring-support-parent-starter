package com.chua.starter.filesystem.support.server;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件存储设置
 *
 * @since 2024/12/28
 * @author CH
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileStorageSetting {
    /**
     * Openpreview
    */
    private boolean openPreview;
    /**
     * 打开下载
    */
    private boolean openDownload;
    /**
     * Openrange
    */
    private boolean openRange;
    /**
     * Openwatermark
    */
    private boolean openWatermark;
    /**
     * Openwebjars
    */
    private boolean openWebjars;
    /**
     * 打开远程文件
    */
    private boolean openRemoteFile;
    /**
     * 三方 URL 预览能力（{@code ?url=<base64(http(s)://…)>}，取值来自
     * {@code FileStorageUrlPreviewProvider} SPI 实现名，{@code NONE} 表示关闭）。
     *
     * <p>外部系统把目标地址 base64 编码后传入，本服务拉取远端内容并走与本地对象
     * 完全相同的预览分发（{@code preview} / {@code format=pdf} 等参数照常生效）。</p>
     *
     * <p>不是布尔开关：可处理外部地址的实现由 SPI 枚举，管理员显式选择后才生效，
     * 且只有声明 {@code supportsUrlPreview()} 的实现真正放行 {@code ?url=}——
     * 其余取值（含未配置）一律 403。</p>
     */
    @Builder.Default
    private String thirdUrlPreview = "NONE";
    /**
     * Watermark
    */
    private String watermark;
    /**
     * 是否开启目录文件列表页面（true 时 获取 /{bucket} 返回 HTML 文件列表；false 时目录访问直接 404）
    */
    private boolean layout;
    /**
     * 文件列表布局类型：简单-简单文件列表树 / 系统-资源管理器风格，默认 简单
    */
    @Builder.Default
    private String layoutType = LayoutType.SIMPLE.name();
    /**
     * 是否显示"类型"列（SYSTEM 布局详情视图）
    */
    @Builder.Default
    private boolean showFormat = true;
    /**
     * 是否显示"大小"列（SYSTEM 布局详情视图）
    */
    @Builder.Default
    private boolean showSize = true;
    /**
     * 是否显示"修改时间"列（SYSTEM 布局详情视图）
    */
    @Builder.Default
    private boolean showTime = true;
    /**
     * 图片滤镜名称（对应 {@code com.chua.common.support.image.filter.ImageFilter} SPI 扩展名，NONE 表示原图）
     */
    @Builder.Default
    private String imageFilter = "NONE";

    /**
     * URL 参数能力实现（{@code FileStorageFileSetting} SPI 扩展名，默认 {@code jdk}）。
     *
     * <p>决定 {@code ?size=100x100&format=webp} 这类文件级参数由哪个 SPI 实现解析。
     * 可选值来自后台 {@code ?format=capabilities} 的 {@code fileSettingCapabilities}，
     * 管理界面按该清单渲染下拉；写入时再次校验，未知实现直接拒绝。</p>
     */
    @Builder.Default
    private String fileSettingKey = "jdk";

    /**
     * 是否启用路径穿透防护（默认开启）。
     *
     * <p>开启后对每个请求做统一穿越筛查：路径含 {@code ..} 段、反斜杠（Windows 下的穿越通道）、
     * NUL 字节或双重编码的 {@code %252e%252e} 直接 400；{@code mkdir/rename/move/copy/unzip}
     * 等动作的 {@code path/name/target/sources/inner} 参数同样筛查。关闭仅用于兼容排障，
     * 对象键级的 {@link #sanitizeKey} 防护不受此开关影响（始终生效）。</p>
     */
    @Builder.Default
    private boolean openPathGuard = true;

    /**
     * 是否启用全局图片滤镜链（默认关闭，对应 utils 侧 {@code FileStorageSetting#imageFilterEnabled}）。
     *
     * <p>开启后每次图片请求（除 {@code ?download} 外的常规预览/读取）都会按
     * {@link #filterChain} 顺序施加滤镜，与 URL 参数叠加：先 URL 操作、后全局链。</p>
     */
    @Builder.Default
    private boolean imageFilterEnabled = false;

    /**
     * 全局图片滤镜链配置（紧凑串：{@code id:key=value,key=value;id2;id3}）。
     *
     * <p>id 必须来自后台 {@code FileStorageFilterSetting} SPI 能力列表（gray、size、blur 等），
     * 写入时校验并统一小写；运行期由图片管线逐项映射执行，JDK 管线不支持的滤镜跳过并 WARN。
     * 空串/null 表示无滤镜链。</p>
     */
    private String filterChain;

    /**
     * 私有桶集合（支持热更新；空表示全部公共桶）。
     * <p>命中私有桶的请求由 OAuthServerFilter（oauth 模块，反射挂载）校验访问令牌；
     * 本集合为该过滤器的私有桶来源，管理员 setConfig 变更后实时同步。</p>
     */
    @Builder.Default
    private Set<String> privateBuckets = ConcurrentHashMap.newKeySet();

    /**
     * 访问链接默认有效期（毫秒）
     *
     * <p>创建链接未显式指定过期时间时使用。原先是 {@code FileStorageServerFilter} 里的
     * {@code static final long ONE_TIME_TTL_MILLIS} 硬编码常量（10 分钟），管理员无法调整，
     * 只能在前端 9 档预设里挑。改为配置项后可由系统配置面板调整，改完立即生效。</p>
     */
    @Builder.Default
    // [P3C A20 豁免]
    private long oneTimeTtlMs = 10 * 60 * 1000L;

    /**
     * 文件列表布局类型。
     * @author CH
     * @since 4.0.0
     */
    public enum LayoutType {
        /**
         * 简单文件列表树
         */
        SIMPLE,
        /**
         * 类 窗口 资源管理器
         */
        SYSTEM
    }
}
