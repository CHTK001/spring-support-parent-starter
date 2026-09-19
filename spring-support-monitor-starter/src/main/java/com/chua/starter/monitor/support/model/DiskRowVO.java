package com.chua.starter.monitor.support.model;

import lombok.Data;

/**
 * 磁盘挂载点视图。
 *
 * <p>各通道单位不同（Agent 给 "465.3 G" 文本、SSH 的 {@code df} 给字节、本机 OSHI 给 GB 浮点），
 * 统一在 Provider 侧换算成 GB 并补算使用率。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class DiskRowVO {

    /**
     * 磁盘名或卷标
     */
    private String name;

    /**
     * 挂载点
     */
    private String mount;

    /**
     * 文件系统类型
     */
    private String fsType;

    /**
     * 总容量，单位 GB
     */
    private double totalGb;

    /**
     * 已用容量，单位 GB
     */
    private double usedGb;

    /**
     * 可用容量，单位 GB
     */
    private double availGb;

    /**
     * 使用率（0-100）
     */
    private double usage;
}
