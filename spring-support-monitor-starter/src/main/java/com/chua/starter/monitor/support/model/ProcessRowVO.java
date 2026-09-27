package com.chua.starter.monitor.support.model;

import lombok.Data;

/**
 * 进程行视图。
 *
 * <p>本机 OSHI、Agent HTTP、SSH 命令、WinRM PowerShell 四类采集通道的统一输出结构，
 * 字段名即前端消费的 JSON 键名，通道差异在 Provider 内部消化。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class ProcessRowVO {

    /**
     * 进程号
     */
    private long pid;

    /**
     * 父进程号，取不到为 0
     */
    private long ppid;

    /**
     * 进程名
     */
    private String name;

    /**
     * 属主用户
     */
    private String user;

    /**
     * CPU 占用百分比（0-100）
     */
    private double cpu;

    /**
     * 内存占用，单位 MB（四类通道统一折算后下发）
     */
    private long memory;

    /**
     * 线程数
     */
    private long threads;

    /**
     * 启动时间展示文本
     */
    private String startTime;

    /**
     * 命令行
     */
    private String command;

    /**
     * 可执行文件路径
     */
    private String path;
}
