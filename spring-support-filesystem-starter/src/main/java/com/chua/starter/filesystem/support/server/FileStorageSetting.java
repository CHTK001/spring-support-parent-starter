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
     * 开启三方 URL 预览（{@code ?url=<base64(http(s)://…)>}，拉取远端内容走预览链路）
     *
     * <p>外部系统把目标地址 base64 编码后传入，本服务拉取远端内容并走与本地对象
     * 完全相同的预览分发（{@code preview} / {@code format=pdf} 等参数照常生效）。
     * 仅放行 http/https，默认关闭。</p>
     */
    private boolean openThirdPreview;
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
