package com.chua.starter.monitor.support.model;

/**
 * 磁盘挂载点视图。
 *
 * <p>各通道单位不同（Agent 给 "465.3 G" 文本、SSH 的 {@code df} 给字节、本机 OSHI 给 GB 浮点），
 * 统一在 Provider 侧换算成 GB 并补算使用率。</p>
 *
 * @param name    磁盘名或卷标
 * @param mount   挂载点
 * @param fsType  文件系统类型
 * @param totalGb 总容量，单位 GB
 * @param usedGb  已用容量，单位 GB
 * @param availGb 可用容量，单位 GB
 * @param usage   使用率（0-100）
 * @author CH
 * @since 4.0.0.42
 */
public record DiskRowVO(
        String name,
        String mount,
        String fsType,
        double totalGb,
        double usedGb,
        double availGb,
        double usage) {
}
