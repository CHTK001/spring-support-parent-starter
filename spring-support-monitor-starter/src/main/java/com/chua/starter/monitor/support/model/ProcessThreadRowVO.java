package com.chua.starter.monitor.support.model;

import lombok.Data;

/**
 * 进程线程行视图。
 *
 * <p>Windows（PowerShell {@code Get-Process | Threads}）与 Linux（{@code ps -L}）两种来源
 * 的键名差异在采集侧归一，字段名即前端消费的 JSON 键名。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class ProcessThreadRowVO {

    /**
     * 线程号
     */
    private long tid;

    /**
     * CPU 占用百分比
     */
    private double cpu;

    /**
     * 内存占用百分比
     */
    private double memory;

    /**
     * 线程状态
     */
    private String state;

    /**
     * 线程名
     */
    private String name;

    /**
     * 累计 CPU 时间展示文本
     */
    private String cpuTime;
}
