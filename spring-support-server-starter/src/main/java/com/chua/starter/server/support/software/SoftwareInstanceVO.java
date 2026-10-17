package com.chua.starter.server.support.software;

/**
 * 主机上运行的软件实例视图。
 *
 * <p>由进程列表按已知软件特征识别，同一软件支持多实例（按 PID 区分），
 * 字段跨平台通用（OSHI / SSH / WinRM / Agent 均可给出）。</p>
 *
 * @param type        软件类型标识（java / redis / mysql / nginx ...）
 * @param displayName 软件展示名称
 * @param pid         进程号
 * @param cpu         CPU 使用率（%）
 * @param memMb       常驻内存（MB）
 * @param uptimeSec   已运行时长（秒）
 * @param cmd         命令行 / 可执行路径
 * @param port        监听端口（取不到为 0）
 * @author CH
 * @since 4.0.0.42
 */
public record SoftwareInstanceVO(
        String type,
        String displayName,
        int pid,
        double cpu,
        double memMb,
        long uptimeSec,
        String cmd,
        int port) {
}
