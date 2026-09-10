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
    /** Open下载 */
    private boolean openDownload;
    /** Openrange */
    private boolean openRange;
    /** Openwatermark */
    private boolean openWatermark;
    /** Openwebjars */
    private boolean openWebjars;
    /** Open远程文件 */
    private boolean openRemoteFile;
    /** Watermark */
    private String watermark;
    /** 是否开启目录文件列表页面（true 时 GET /{bucket} 返回 HTML 文件列表；false 时目录访问直接 404） */
    private boolean layout;
    /** 文件列表布局类型：SIMPLE-简单文件列表树 / SYSTEM-资源管理器风格，默认 SIMPLE */
    private String layoutType = LayoutType.SIMPLE.name();

    /**
     * 文件列表布局类型。
     */
    public enum LayoutType {
        /**
         * 简单文件列表树
         */
        SIMPLE,
        /**
         * 类 Windows 资源管理器
         */
        SYSTEM
    }
}
