package com.chua.starter.monitor.support.model;

/**
 * 进程线程行视图。
 *
 * <p>Windows（PowerShell {@code Get-Process | Threads}）与 Linux（{@code ps -L}）两种来源
 * 的键名差异在采集侧归一，字段名即前端消费的 JSON 键名。</p>
 *
 * <p>Windows 侧没有线程名，{@code name} 留空；{@code priority} 与 {@code waitReason}
 * 只有 Windows 能给，Linux 侧为空串。</p>
 *
 * @param tid        线程号
 * @param cpu        CPU 占用百分比
 * @param memory     内存占用百分比
 * @param state      线程状态
 * @param name       线程名
 * @param priority   优先级，仅 Windows 有值
 * @param waitReason 等待原因，仅 Windows 有值
 * @param cpuTime    累计 CPU 时间展示文本
 * @author CH
 * @since 4.0.0.42
 */
public record ProcessThreadRowVO(
        long tid,
        double cpu,
        double memory,
        String state,
        String name,
        String priority,
        String waitReason,
        String cpuTime) {
}
