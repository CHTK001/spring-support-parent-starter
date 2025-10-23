package com.chua.starter.shell.command;

import com.github.fonimus.ssh.shell.PromptColor;
import com.github.fonimus.ssh.shell.SshShellHelper;
import com.github.fonimus.ssh.shell.commands.SshShellComponent;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.text.DecimalFormat;

/**
 * 系统信息命令
 * 
 * 提供系统监控和信息查询功能
 * 
 * @author CH
 * @version 4.0.0.32
 */
@SshShellComponent
@ShellCommandGroup("系统监控")
public class SystemInfoCommand {

    private final SshShellHelper helper;
    private final DecimalFormat df = new DecimalFormat("#.##");

    public SystemInfoCommand(SshShellHelper helper) {
        this.helper = helper;
    }

    /**
     * 显示系统信息
     * 
     * @param type 信息类型
     * @return 系统信息
     */
    @ShellMethod(value = "显示系统信息", key = {"system-info", "si"})
    public String systemInfo(@ShellOption(value = {"-t", "--type"}, defaultValue = "all", help = "信息类型: all, os, jvm, memory, disk, network") String type) {
        
        StringBuilder sb = new StringBuilder();
        
        switch (type.toLowerCase()) {
            case "os" -> sb.append(getOsInfo());
            case "jvm" -> sb.append(getJvmInfo());
            case "memory" -> sb.append(getMemoryInfo());
            case "disk" -> sb.append(getDiskInfo());
            case "network" -> sb.append(getNetworkInfo());
            case "all" -> {
                sb.append(getOsInfo()).append("\n");
                sb.append(getJvmInfo()).append("\n");
                sb.append(getMemoryInfo()).append("\n");
                sb.append(getDiskInfo()).append("\n");
                sb.append(getNetworkInfo());
            }
            default -> sb.append(helper.getColored("未知的信息类型: " + type, PromptColor.RED))
                    .append("\n支持的类型: all, os, jvm, memory, disk, network");
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
        return getMemoryInfo();
    }

    /**
     * 显示线程信息
     * 
     * @return 线程信息
     */
    @ShellMethod(value = "显示线程信息", key = {"thread-info", "ti"})
    public String threadInfo() {
        StringBuilder sb = new StringBuilder();

        sb.append(helper.getColored("=== 线程信息 ===", PromptColor.CYAN)).append("\n\n");
        
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        sb.append(helper.getColored("线程统计:", PromptColor.YELLOW)).append("\n");
        sb.append(String.format("  当前线程数: %s\n", helper.getColored(String.valueOf(threadBean.getThreadCount()), PromptColor.GREEN)));
        sb.append(String.format("  峰值线程数: %s\n", helper.getColored(String.valueOf(threadBean.getPeakThreadCount()), PromptColor.GREEN)));
        sb.append(String.format("  守护线程数: %s\n", helper.getColored(String.valueOf(threadBean.getDaemonThreadCount()), PromptColor.BLUE)));
        sb.append(String.format("  总启动线程数: %s\n", helper.getColored(String.valueOf(threadBean.getTotalStartedThreadCount()), PromptColor.BLUE)));
        
        // 线程状态统计
        Thread.State[] states = Thread.State.values();
        sb.append("\n").append(helper.getColored("线程状态分布:", PromptColor.YELLOW)).append("\n");
        
        for (Thread.State state : states) {
            long count = threadBean.getAllThreadIds().length; // 简化统计
            sb.append(String.format("  %s: %s\n", state.name(), helper.getColored("N/A", PromptColor.BLUE)));
        }
        
        return sb.toString();
    }

    /**
     * 获取操作系统信息
     */
    private String getOsInfo() {
        StringBuilder sb = new StringBuilder();

        sb.append(helper.getColored("=== 操作系统信息 ===", PromptColor.CYAN)).append("\n\n");

        sb.append(helper.getColored("基本信息:", PromptColor.YELLOW)).append("\n");
        sb.append(String.format("  操作系统: %s\n", helper.getColored(System.getProperty("os.name"), PromptColor.GREEN)));
        sb.append(String.format("  系统版本: %s\n", helper.getColored(System.getProperty("os.version"), PromptColor.GREEN)));
        sb.append(String.format("  系统架构: %s\n", helper.getColored(System.getProperty("os.arch"), PromptColor.GREEN)));
        sb.append(String.format("  用户名: %s\n", helper.getColored(System.getProperty("login.name"), PromptColor.BLUE)));
        sb.append(String.format("  用户目录: %s\n", helper.getColored(System.getProperty("login.home"), PromptColor.BLUE)));
        sb.append(String.format("  工作目录: %s\n", helper.getColored(System.getProperty("login.dir"), PromptColor.BLUE)));
        
        // 处理器信息
        int processors = Runtime.getRuntime().availableProcessors();
        sb.append(String.format("  可用处理器: %s\n", helper.getColored(String.valueOf(processors), PromptColor.GREEN)));
        
        return sb.toString();
    }

    /**
     * 获取JVM信息
     */
    private String getJvmInfo() {
        StringBuilder sb = new StringBuilder();

        sb.append(helper.getColored("=== JVM信息 ===", PromptColor.CYAN)).append("\n\n");

        sb.append(helper.getColored("Java信息:", PromptColor.YELLOW)).append("\n");
        sb.append(String.format("  Java版本: %s\n", helper.getColored(System.getProperty("java.version"), PromptColor.GREEN)));
        sb.append(String.format("  Java供应商: %s\n", helper.getColored(System.getProperty("java.vendor"), PromptColor.GREEN)));
        sb.append(String.format("  Java安装目录: %s\n", helper.getColored(System.getProperty("java.home"), PromptColor.BLUE)));
        sb.append(String.format("  JVM名称: %s\n", helper.getColored(System.getProperty("java.vm.name"), PromptColor.GREEN)));
        sb.append(String.format("  JVM版本: %s\n", helper.getColored(System.getProperty("java.vm.version"), PromptColor.GREEN)));
        sb.append(String.format("  JVM供应商: %s\n", helper.getColored(System.getProperty("java.vm.vendor"), PromptColor.GREEN)));
        
        // 运行时信息
        Runtime runtime = Runtime.getRuntime();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        sb.append(String.format("  JVM运行时间: %s\n", helper.getColored(formatUptime(uptime), PromptColor.CYAN)));
        
        return sb.toString();
    }

    /**
     * 获取内存信息
     */
    private String getMemoryInfo() {
        StringBuilder sb = new StringBuilder();

        sb.append(helper.getColored("=== 内存信息 ===", PromptColor.CYAN)).append("\n\n");
        
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        // JVM内存信息
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        sb.append(helper.getColored("JVM内存:", PromptColor.YELLOW)).append("\n");
        sb.append(String.format("  最大内存: %s\n", helper.getColored(formatBytes(maxMemory), PromptColor.GREEN)));
        sb.append(String.format("  总内存: %s\n", helper.getColored(formatBytes(totalMemory), PromptColor.GREEN)));
        sb.append(String.format("  已用内存: %s\n", helper.getColored(formatBytes(usedMemory), PromptColor.YELLOW)));
        sb.append(String.format("  空闲内存: %s\n", helper.getColored(formatBytes(freeMemory), PromptColor.GREEN)));
        
        // 内存使用率
        double usagePercent = (double) usedMemory / totalMemory * 100;
        PromptColor usageColor = usagePercent > 80 ? PromptColor.RED :
                usagePercent > 60 ? PromptColor.YELLOW : PromptColor.GREEN;
        sb.append(String.format("  内存使用率: %s%%\n", helper.getColored(df.format(usagePercent), usageColor)));
        
        // 堆内存信息
        sb.append("\n").append(helper.getColored("堆内存详情:", PromptColor.YELLOW)).append("\n");
        sb.append(String.format("  堆内存已用: %s\n", helper.getColored(formatBytes(memoryBean.getHeapMemoryUsage().getUsed()), PromptColor.YELLOW)));
        sb.append(String.format("  堆内存已提交: %s\n", helper.getColored(formatBytes(memoryBean.getHeapMemoryUsage().getCommitted()), PromptColor.GREEN)));
        sb.append(String.format("  堆内存最大: %s\n", helper.getColored(formatBytes(memoryBean.getHeapMemoryUsage().getMax()), PromptColor.GREEN)));
        
        // 非堆内存信息
        sb.append("\n").append(helper.getColored("非堆内存详情:", PromptColor.YELLOW)).append("\n");
        sb.append(String.format("  非堆内存已用: %s\n", helper.getColored(formatBytes(memoryBean.getNonHeapMemoryUsage().getUsed()), PromptColor.YELLOW)));
        sb.append(String.format("  非堆内存已提交: %s\n", helper.getColored(formatBytes(memoryBean.getNonHeapMemoryUsage().getCommitted()), PromptColor.GREEN)));
        sb.append(String.format("  非堆内存最大: %s\n", helper.getColored(formatBytes(memoryBean.getNonHeapMemoryUsage().getMax()), PromptColor.GREEN)));
        
        return sb.toString();
    }

    /**
     * 获取磁盘信息
     */
    private String getDiskInfo() {
        StringBuilder sb = new StringBuilder();

        sb.append(helper.getColored("=== 磁盘信息 ===", PromptColor.CYAN)).append("\n\n");
        
        java.io.File[] roots = java.io.File.listRoots();

        sb.append(helper.getColored("磁盘分区:", PromptColor.YELLOW)).append("\n");
        sb.append(String.format("  %-10s %-12s %-12s %-12s %s\n", "分区", "总空间", "可用空间", "已用空间", "使用率"));
        sb.append("  ").append("-".repeat(60)).append("\n");
        
        for (java.io.File root : roots) {
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;
            
            if (totalSpace > 0) {
                double usagePercent = (double) usedSpace / totalSpace * 100;
                PromptColor usageColor = usagePercent > 90 ? PromptColor.RED :
                        usagePercent > 80 ? PromptColor.YELLOW : PromptColor.GREEN;
                
                sb.append(String.format("  %-10s %-12s %-12s %-12s %s%%\n",
                        root.getPath(),
                        formatBytes(totalSpace),
                        formatBytes(freeSpace),
                        formatBytes(usedSpace),
                        helper.getColored(df.format(usagePercent), usageColor)));
            }
        }
        
        return sb.toString();
    }

    /**
     * 获取网络信息
     */
    private String getNetworkInfo() {
        StringBuilder sb = new StringBuilder();

        sb.append(helper.getColored("=== 网络信息 ===", PromptColor.CYAN)).append("\n\n");
        
        try {
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            sb.append(helper.getColored("本机信息:", PromptColor.YELLOW)).append("\n");
            sb.append(String.format("  主机名: %s\n", helper.getColored(localHost.getHostName(), PromptColor.GREEN)));
            sb.append(String.format("  IP地址: %s\n", helper.getColored(localHost.getHostAddress(), PromptColor.GREEN)));
            
            // 网络接口信息
            sb.append("\n").append(helper.getColored("网络接口:", PromptColor.YELLOW)).append("\n");
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    sb.append(String.format("  接口名: %s\n", helper.getColored(networkInterface.getDisplayName(), PromptColor.BLUE)));
                    
                    java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        java.net.InetAddress address = addresses.nextElement();
                        if (!address.isLoopbackAddress()) {
                            sb.append(String.format("    IP: %s\n", helper.getColored(address.getHostAddress(), PromptColor.GREEN)));
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            sb.append(helper.getColored("获取网络信息失败: " + e.getMessage(), PromptColor.RED));
        }
        
        return sb.toString();
    }

    /**
     * 格式化字节数
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return df.format(bytes / Math.pow(1024, exp)) + " " + pre + "B";
    }

    /**
     * 格式化运行时间
     */
    private String formatUptime(long millis) {
        long seconds = millis / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (days > 0) {
            return String.format("%d天 %d小时 %d分钟", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%d小时 %d分钟", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%d分钟 %d秒", minutes, secs);
        } else {
            return String.format("%d秒", secs);
        }
    }
}
