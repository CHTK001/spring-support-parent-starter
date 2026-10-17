package com.chua.starter.monitor.support.model;

/**
 * 进程行视图。
 *
 * <p>本机 OSHI、Agent HTTP、SSH 命令、WinRM PowerShell 四类采集通道的统一输出结构，
 * 字段名即前端消费的 JSON 键名，通道差异在 Provider 内部消化。</p>
 *
 * @param pid       进程号
 * @param ppid      父进程号，取不到为 0
 * @param name      进程名
 * @param user      属主用户
 * @param cpu       CPU 占用百分比（0-100）
 * @param memory    内存占用，单位 MB（四类通道统一折算后下发）
 * @param threads   线程数
 * @param startTime 启动时间展示文本
 * @param command   命令行
 * @param path      可执行文件路径
 * @author CH
 * @since 4.0.0.42
 */
public record ProcessRowVO(
        long pid,
        long ppid,
        String name,
        String user,
        double cpu,
        long memory,
        long threads,
        String startTime,
        String command,
        String path) {

    /**
     * 以新的 CPU 占用百分比生成副本。
     *
     * <p>部分通道只能先取回整表、再做 CPU 时间占比换算，故提供该变换方法以保持不可变语义。</p>
     *
     * @param cpuValue CPU 占用百分比（0-100）
     * @return 替换 CPU 后的新实例
     */
    public ProcessRowVO withCpu(double cpuValue) {
        return new ProcessRowVO(pid, ppid, name, user, cpuValue, memory, threads, startTime, command, path);
    }
}
