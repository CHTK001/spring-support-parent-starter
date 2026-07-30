package com.chua.starter.server.support.spi.process;

/**
 * Unix 进程命令支持工具类。
 *
 * <p>提供 Unix/Linux 系统下进程列表和终止命令的构建。
 *
 * @author CH
 */
public final class UnixProcessCommandSupport {

    private UnixProcessCommandSupport() {
    }

    /**
     * 构建 Unix 进程列表命令。
     *
     * @param queryWindow 查询窗口
     * @return 命令字符串
     */
    public static String unixListCommand(int queryWindow) {
        return "ps -eo pid=,ppid=,pcpu=,pmem=,rss=,fds=,nlwp=,etimes=,user=,stat=,comm=,args= --sort=-pcpu | head -n "
                + queryWindow;
    }

    /**
     * 构建 Unix 终止进程命令。
     *
     * @param pid 进程 ID
     * @param force 是否强制终止
     * @return 命令字符串
     */
    public static String unixTerminateCommand(Long pid, boolean force) {
        return (force ? "kill -KILL " : "kill -TERM ") + pid;
    }
}
