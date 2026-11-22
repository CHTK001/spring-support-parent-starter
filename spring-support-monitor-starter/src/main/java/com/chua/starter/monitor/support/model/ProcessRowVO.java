package com.chua.starter.monitor.support.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * @param state     进程状态首字母（R 运行 / S 睡眠 / D 不可中断 / T 停止 / Z 僵尸），Windows 通道无此概念恒为空
 * @param orphan    是否无主进程（父进程已退出：ppid 不在本机当前进程集合中）
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
        String path,
        String state,
        boolean orphan) {

    /**
     * 以新的 CPU 占用百分比生成副本。
     *
     * <p>部分通道只能先取回整表、再做 CPU 时间占比换算，故提供该变换方法以保持不可变语义。</p>
     *
     * @param cpuValue CPU 占用百分比（0-100）
     * @return 替换 CPU 后的新实例
     */
    public ProcessRowVO withCpu(double cpuValue) {
        return new ProcessRowVO(pid, ppid, name, user, cpuValue, memory, threads, startTime, command, path, state, orphan);
    }

    /**
     * 以新的无主标记生成副本。
     *
     * @param orphanValue 是否无主进程
     * @return 替换无主标记后的新实例
     */
    public ProcessRowVO withOrphan(boolean orphanValue) {
        return new ProcessRowVO(pid, ppid, name, user, cpu, memory, threads, startTime, command, path, state, orphanValue);
    }

    /**
     * 在同一主机的整表范围内标记无主进程（孤儿进程：父进程号不在当前进程集合中）。
     *
     * <p>必须用同一主机同一时刻的全量进程表计算；对分页后的子集计算会把
     * 「父进程在别的页」误判成无主。父进程号为 0（内核/idle）不视为无主。</p>
     *
     * @param rows 同一主机的进程行（应为本机全表，至少是覆盖父子关系的快照）
     * @return 标记后的新列表，永不为 null
     */
    public static List<ProcessRowVO> markOrphans(List<ProcessRowVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        Set<Long> pids = new HashSet<>(rows.size() * 2);
        for (ProcessRowVO row : rows) {
            pids.add(row.pid());
        }
        List<ProcessRowVO> out = new ArrayList<>(rows.size());
        for (ProcessRowVO row : rows) {
            boolean orphan = row.ppid() > 0 && !pids.contains(row.ppid());
            out.add(orphan == row.orphan() ? row : row.withOrphan(orphan));
        }
        return out;
    }
}
