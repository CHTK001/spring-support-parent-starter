package com.chua.starter.monitor.support.model;

/**
 * 驱动行视图。
 *
 * <p>Linux 来自 {@code lsmod}（模块名/体积/引用数/依赖），Windows 来自
 * {@code Get-WindowsDriver -Online}（驱动文件/提供者/版本/类名），两侧不重合的列留空。</p>
 *
 * @param name         模块名或驱动.inf 文件名
 * @param fileName     原始文件名（Windows OriginalFileName）
 * @param size         占用字节数（仅 Linux lsmod 提供，Windows 为 null）
 * @param usedBy       被引用次数（仅 Linux lsmod 提供，Windows 为 null）
 * @param dependsOn    依赖的其他模块
 * @param provider     驱动提供者
 * @param version      驱动版本
 * @param date         驱动日期展示文本
 * @param className    设备类名
 * @param bootCritical 是否启动关键（仅 Windows Get-WindowsDriver 提供，Linux 为 null）
 * @author CH
 * @since 4.0.0.42
 */
public record DriverRowVO(
        String name,
        String fileName,
        Long size,
        Integer usedBy,
        String dependsOn,
        String provider,
        String version,
        String date,
        String className,
        Boolean bootCritical) {
}
