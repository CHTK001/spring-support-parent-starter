package com.chua.starter.monitor.support.model;

import lombok.Data;

/**
 * 驱动行视图。
 *
 * <p>Linux 来自 {@code lsmod}（模块名/体积/引用数/依赖），Windows 来自
 * {@code Get-WindowsDriver -Online}（驱动文件/提供者/版本/类名），两侧不重合的列留空。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class DriverRowVO {

    /**
     * 模块名或驱动.inf 文件名
     */
    private String name;

    /**
     * 原始文件名（Windows OriginalFileName）
     */
    private String fileName;

    /**
     * 占用字节数（仅 Linux lsmod 提供，Windows 为 null）
     */
    private Long size;

    /**
     * 被引用次数（仅 Linux lsmod 提供，Windows 为 null）
     */
    private Integer usedBy;

    /**
     * 依赖的其他模块
     */
    private String dependsOn;

    /**
     * 驱动提供者
     */
    private String provider;

    /**
     * 驱动版本
     */
    private String version;

    /**
     * 驱动日期展示文本
     */
    private String date;

    /**
     * 设备类名
     */
    private String className;

    /**
     * 是否启动关键（仅 Windows Get-WindowsDriver 提供，Linux 为 null）
     */
    private Boolean bootCritical;
}
