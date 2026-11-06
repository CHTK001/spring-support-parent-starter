package com.chua.starter.monitor.support.model;

/**
 * 挂载盘视图。
 *
 * <p>与「磁盘容量」不同：这里描述的是「什么设备挂在哪个目录 / 盘符」，
 * 用于查询现有挂载并执行挂载 / 卸载动作。</p>
 *
 * @param source      设备或卷标识（Linux /dev/sda1、Windows 卷 GUID）
 * @param target      挂载点（Linux 目录、Windows 盘符）
 * @param fsType      文件系统类型
 * @param options     挂载选项
 * @param totalBytes  总容量（字节），取不到为 -1
 * @param usedBytes   已用容量（字节），取不到为 -1
 * @param availBytes  可用容量（字节），取不到为 -1
 * @param usage       使用率（0-100），取不到为 -1
 * @param volumeId    卷标识（Windows 用于重新挂载），Linux 为空串
 * @author CH
 * @since 4.0.0.51
 */
public record MountRowVO(
        String source,
        String target,
        String fsType,
        String options,
        long totalBytes,
        long usedBytes,
        long availBytes,
        double usage,
        String volumeId) {
}
