package com.chua.starter.filesystem.support.server;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    /** Openpreview */
    private boolean openPreview;
    /** 打开下载 */
    private boolean openDownload;
    /** Openrange */
    private boolean openRange;
    /** Openwatermark */
    private boolean openWatermark;
    /** Openwebjars */
    private boolean openWebjars;
    /** 打开远程文件 */
    private boolean openRemoteFile;
    /** Watermark */
    private String watermark;
    /** 是否开启目录文件列表页面（true 时 获取 /{bucket} 返回 HTML 文件列表；false 时目录访问直接 404） */
    private boolean layout;
    /** 文件列表布局类型：简单-简单文件列表树 / 系统-资源管理器风格，默认 简单 */
    private String layoutType = LayoutType.SIMPLE.name();
    /** 是否显示"类型"列（SYSTEM 布局详情视图） */
    @Builder.Default
    private boolean showFormat = true;
    /** 是否显示"大小"列（SYSTEM 布局详情视图） */
    @Builder.Default
    private boolean showSize = true;
    /** 是否显示"修改时间"列（SYSTEM 布局详情视图） */
    @Builder.Default
    private boolean showTime = true;
    /** 图片滤镜名称（对应 SPI {@code FileStorageImageFilterProvider#getName()}，默认原图） */
    @Builder.Default
    private String imageFilter = "NONE";

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
