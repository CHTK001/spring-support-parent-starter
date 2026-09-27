package com.chua.starter.monitor.support.model;

/**
 * 进程概览计数。
 *
 * <p>概览/实时指标只需要"有多少进程、多少线程"，不需要逐条命令行与路径。Windows 上命令行与路径
 * 是 OSHI 的按进程补全字段，实测 464 个进程取全量约 18 秒，故计数走不补全的轻量快照。</p>
 *
 * @param processCount 进程数
 * @param threadCount  线程总数
 * @author CH
 * @since 4.0.0.42
 */
public record ProcessSummaryVO(int processCount, long threadCount) {
}
