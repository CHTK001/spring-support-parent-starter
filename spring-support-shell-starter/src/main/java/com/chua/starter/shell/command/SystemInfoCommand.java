package com.chua.starter.shell.command;

import com.chua.starter.shell.support.ConsoleColor;
import com.chua.starter.shell.support.ShellHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 系统信息命令
 * <p>
 * 提供系统监控和信息查询功能
 *
 * @author CH
 * @version 4.0.0.37
 */
@Slf4j
@ShellComponent
@ShellCommandGroup("系统监控")
public class SystemInfoCommand {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ShellHelper helper;

    public SystemInfoCommand(ShellHelper helper) {
        this.helper = helper;
    }

    /**
     * 显示系统信息
     *
     * @param type 信息类型
     * @return 系统信息
     */
    @ShellMethod(value = "显示系统信息", key = {"system-info", "si"})
    public String systemInfo(@ShellOption(value = {"-t", "--type"}, defaultValue = "all", help = "信息类型: os, jvm, all") String type) {

        var sb = new StringBuilder();

        sb.append(helper.getColored("=== 系统信息 ===", ConsoleColor.CYAN)).append("\n");
        sb.append("查询时间: ").append(helper.getColored(LocalDateTime.now().format(DATE_TIME_FORMATTER), ConsoleColor.GREEN)).append("\n\n");

        if ("all".equalsIgnoreCase(type) || "os".equalsIgnoreCase(type)) {
            appendOsInfo(sb);
        }

        if ("all".equalsIgnoreCase(type) || "jvm".equalsIgnoreCase(type)) {
            appendJvmInfo(sb);
        }

        return sb.toString();
    }

    /**
     * 显示内存使用情况
     *
     * @return 内存信息
     */
    @ShellMethod(value = "显示内存使用情况", key = {"memory-info", "mi"})
    public String memoryInfo() {
        var sb = new StringBuilder();

        sb.append(helper.getColored("=== 内存使用情况 ===", ConsoleColor.CYAN)).append("\n\n");

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        // 堆内存
        var heapUsage = memoryBean.getHeapMemoryUsage();
        sb.append(helper.getColored("堆内存 (Heap):", ConsoleColor.YELLOW)).append("\n");
        sb.append(String.format("  已使用: %s / %s (%.1f%%)%n",
                helper.getColored(formatBytes(heapUsage.getUsed()), ConsoleColor.GREEN),
                formatBytes(heapUsage.getMax()),
                (double) heapUsage.getUsed() / heapUsage.getMax() * 100));
        sb.append(String.format("  已提交: %s%n", formatBytes(heapUsage.getCommitted())));
        sb.append(String.format("  初始值: %s%n", formatBytes(heapUsage.getInit())));

        // 显示内存使用条
        appendProgressBar(sb, (double) heapUsage.getUsed() / heapUsage.getMax());

        sb.append("\n");

        // 非堆内存
        var nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        sb.append(helper.getColored("非堆内存 (Non-Heap):", ConsoleColor.YELLOW)).append("\n");
        sb.append(String.format("  已使用: %s%n", helper.getColored(formatBytes(nonHeapUsage.getUsed()), ConsoleColor.GREEN)));
        sb.append(String.format("  已提交: %s%n", formatBytes(nonHeapUsage.getCommitted())));
        sb.append(String.format("  初始值: %s%n", formatBytes(nonHeapUsage.getInit())));

        // GC信息
        sb.append("\n").append(helper.getColored("垃圾回收器:", ConsoleColor.YELLOW)).append("\n");
        ManagementFactory.getGarbageCollectorMXBeans().forEach(gc ->
                sb.append(String.format("  %s: 回收次数=%d, 回收时间=%dms%n",
                        gc.getName(), gc.getCollectionCount(), gc.getCollectionTime()))
        );

        return sb.toString();
    }

    /**
     * 显示线程信息
     *
     * @param showAll 是否显示所有线程
     * @return 线程信息
     */
    @ShellMethod(value = "显示线程信息", key = {"thread-info", "ti"})
    public String threadInfo(@ShellOption(value = {"-a", "--all"}, defaultValue = "false", help = "显示所有线程") boolean showAll) {

        var sb = new StringBuilder();

        sb.append(helper.getColored("=== 线程信息 ===", ConsoleColor.CYAN)).append("\n\n");

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        sb.append(helper.getColored("线程统计:", ConsoleColor.YELLOW)).append("\n");
        sb.append(String.format("  当前线程数: %s%n", helper.getColored(String.valueOf(threadBean.getThreadCount()), ConsoleColor.GREEN)));
        sb.append(String.format("  守护线程数: %s%n", helper.getColored(String.valueOf(threadBean.getDaemonThreadCount()), ConsoleColor.GREEN)));
        sb.append(String.format("  峰值线程数: %s%n", helper.getColored(String.valueOf(threadBean.getPeakThreadCount()), ConsoleColor.YELLOW)));
        sb.append(String.format("  累计启动线程数: %s%n", helper.getColored(String.valueOf(threadBean.getTotalStartedThreadCount()), ConsoleColor.GREEN)));

        if (showAll) {
            sb.append("\n").append(helper.getColored("线程列表:", ConsoleColor.YELLOW)).append("\n");
            sb.append(helper.separator(80)).append("\n");
            sb.append(String.format("%-8s %-40s %-15s%n", "ID", "名称", "状态"));
            sb.append(helper.separator(80)).append("\n");

            var threadIds = threadBean.getAllThreadIds();
            var threadInfos = threadBean.getThreadInfo(threadIds);

            int count = 0;
            for (var info : threadInfos) {
                if (info != null && count < 30) {
                    var state = info.getThreadState();
                    var stateColor = switch (state) {
                        case RUNNABLE -> ConsoleColor.GREEN;
                        case BLOCKED -> ConsoleColor.RED;
                        case WAITING, TIMED_WAITING -> ConsoleColor.YELLOW;
                        default -> ConsoleColor.WHITE;
                    };
                    var name = info.getThreadName();
                    if (name.length() > 38) {
                        name = name.substring(0, 35) + "...";
                    }
                    sb.append(String.format("%-8d %-40s %s%n",
                            info.getThreadId(), name, helper.getColored(state.name(), stateColor)));
                    count++;
                }
            }

            sb.append(helper.separator(80)).append("\n");
            if (threadInfos.length > 30) {
                sb.append("... 还有 ").append(threadInfos.length - 30).append(" 个线程未显示\n");
            }
        }

        return sb.toString();
    }

    /**
     * 显示运行时信息
     *
     * @return 运行时信息
     */
    @ShellMethod(value = "显示运行时信息", key = {"runtime-info", "ri"})
    public String runtimeInfo() {
        var sb = new StringBuilder();

        sb.append(helper.getColored("=== 运行时信息 ===", ConsoleColor.CYAN)).append("\n\n");

        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

        sb.append(helper.getColored("JVM信息:", ConsoleColor.YELLOW)).append("\n");
        sb.append(String.format("  JVM名称: %s%n", helper.getColored(runtimeBean.getVmName(), ConsoleColor.GREEN)));
        sb.append(String.format("  JVM版本: %s%n", helper.getColored(runtimeBean.getVmVersion(), ConsoleColor.GREEN)));
        sb.append(String.format("  JVM厂商: %s%n", runtimeBean.getVmVendor()));

        // 运行时间
        var uptime = Duration.ofMillis(runtimeBean.getUptime());
        var uptimeStr = String.format("%d天 %d小时 %d分钟 %d秒",
                uptime.toDays(), uptime.toHoursPart(), uptime.toMinutesPart(), uptime.toSecondsPart());
        sb.append(String.format("  运行时间: %s%n", helper.getColored(uptimeStr, ConsoleColor.GREEN)));

        sb.append(String.format("  启动时间: %s%n", helper.getColored(
                java.time.Instant.ofEpochMilli(runtimeBean.getStartTime())
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(DATE_TIME_FORMATTER), ConsoleColor.GREEN)));

        sb.append("\n").append(helper.getColored("启动参数:", ConsoleColor.YELLOW)).append("\n");
        var args = runtimeBean.getInputArguments();
        if (args.isEmpty()) {
            sb.append("  (无)\n");
        } else {
            for (String arg : args) {
                if (arg.length() > 70) {
                    arg = arg.substring(0, 67) + "...";
                }
                sb.append("  ").append(arg).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 追加操作系统信息
     *
     * @param sb StringBuilder
     */
    private void appendOsInfo(StringBuilder sb) {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

        sb.append(helper.getColored("操作系统:", ConsoleColor.YELLOW)).append("\n");
        sb.append(String.format("  名称: %s%n", helper.getColored(osBean.getName(), ConsoleColor.GREEN)));
        sb.append(String.format("  版本: %s%n", osBean.getVersion()));
        sb.append(String.format("  架构: %s%n", osBean.getArch()));
        sb.append(String.format("  处理器数量: %s%n", helper.getColored(String.valueOf(osBean.getAvailableProcessors()), ConsoleColor.GREEN)));
        sb.append(String.format("  系统负载: %s%n", helper.getColored(String.format("%.2f", osBean.getSystemLoadAverage()), ConsoleColor.GREEN)));
        sb.append("\n");
    }

    /**
     * 追加JVM信息
     *
     * @param sb StringBuilder
     */
    private void appendJvmInfo(StringBuilder sb) {
        sb.append(helper.getColored("JVM:", ConsoleColor.YELLOW)).append("\n");
        sb.append(String.format("  Java版本: %s%n", helper.getColored(System.getProperty("java.version"), ConsoleColor.GREEN)));
        sb.append(String.format("  Java厂商: %s%n", System.getProperty("java.vendor")));
        sb.append(String.format("  Java Home: %s%n", System.getProperty("java.home")));
        sb.append(String.format("  用户目录: %s%n", System.getProperty("user.dir")));
        sb.append(String.format("  文件编码: %s%n", System.getProperty("file.encoding")));
        sb.append("\n");
    }

    /**
     * 格式化字节数
     *
     * @param bytes 字节数
     * @return 格式化后的字符串
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), unit);
    }

    /**
     * 追加进度条
     *
     * @param sb    StringBuilder
     * @param ratio 比例 (0-1)
     */
    private void appendProgressBar(StringBuilder sb, double ratio) {
        int barLength = 40;
        int filledLength = (int) (barLength * ratio);

        sb.append("  [");
        var color = ratio > 0.9 ? ConsoleColor.RED : (ratio > 0.7 ? ConsoleColor.YELLOW : ConsoleColor.GREEN);
        sb.append(helper.getColored("█".repeat(filledLength), color));
        sb.append("░".repeat(barLength - filledLength));
        sb.append("]");
    }
}
