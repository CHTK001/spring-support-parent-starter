package com.chua.starter.server.support.software;

import lombok.Data;

/**
 * 主机上运行的软件实例视图。
 *
 * <p>由进程列表按已知软件特征识别，同一软件支持多实例（按 PID 区分），
 * 字段跨平台通用（OSHI / SSH / WinRM / Agent 均可给出）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class SoftwareInstanceVO {

    /**
     * 软件类型标识（java / redis / mysql / nginx ...）
     */
    private String type;

    /**
     * 软件展示名称
     */
    private String displayName;

    /**
     * 进程号
     */
    private int pid;

    /**
     * CPU 使用率（%）
     */
    private double cpu;

    /**
     * 常驻内存（MB）
     */
    private double memMb;

    /**
     * 已运行时长（秒）
     */
    private long uptimeSec;

    /**
     * 命令行 / 可执行路径
     */
    private String cmd;

    /**
     * 监听端口（取不到为 0）
     */
    private int port;
}
