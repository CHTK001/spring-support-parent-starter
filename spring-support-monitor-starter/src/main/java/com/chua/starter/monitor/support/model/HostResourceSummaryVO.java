package com.chua.starter.monitor.support.model;

/**
 * 主机资源摘要（底部状态条展示句柄数与 inode 使用率）。
 *
 * @param handleCount  系统句柄总数（Windows 为各进程 HandleCount 汇总，POSIX 为已分配文件句柄数）
 * @param threadCount  线程总数
 * @param processCount 进程数
 * @param inodeUsed    根文件系统已用 inode 数，取不到为 -1
 * @param inodeTotal   根文件系统总 inode 数，取不到为 -1
 * @param inodePercent 根文件系统 inode 使用率，取不到为 -1
 * @param inodeMount   统计的挂载点
 * @param collectedAt  采集时间
 * @author CH
 * @since 4.0.0.51
 */
public record HostResourceSummaryVO(
        long handleCount,
        int threadCount,
        int processCount,
        long inodeUsed,
        long inodeTotal,
        double inodePercent,
        String inodeMount,
        String collectedAt) {
}
