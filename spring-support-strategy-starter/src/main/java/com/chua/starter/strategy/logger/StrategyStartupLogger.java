package com.chua.starter.strategy.logger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.*;

/**
 * 策略模块启动日志美化
 * <p>
 * 应用启动时打印美化的启动信息，使用Java 21虚拟线程特性。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@Component
public class StrategyStartupLogger {

    private static final String BANNER = """
            
            ╔═══════════════════════════════════════════════════════════════╗
            ║                                                               ║
            ║   ███████╗████████╗██████╗  █████╗ ████████╗███████╗ ██████╗  ║
            ║   ██╔════╝╚══██╔══╝██╔══██╗██╔══██╗╚══██╔══╝██╔════╝██╔════╝  ║
            ║   ███████╗   ██║   ██████╔╝███████║   ██║   █████╗  ██║  ███╗ ║
            ║   ╚════██║   ██║   ██╔══██╗██╔══██║   ██║   ██╔══╝  ██║   ██║ ║
            ║   ███████║   ██║   ██║  ██║██║  ██║   ██║   ███████╗╚██████╔╝ ║
            ║   ╚══════╝   ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝   ╚══════╝ ╚═════╝  ║
            ║                                                               ║
            ╚═══════════════════════════════════════════════════════════════╝
            """;

    private final Environment environment;
    private final long startTime = System.currentTimeMillis();

    @Value("${spring.application.name:application}")
    private String applicationName;

    @Value("${server.port:8080}")
    private int serverPort;

    public StrategyStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        // 并行收集系统信息
        try {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            
            Future<HostInfo> hostFuture = executor.submit(this::getHostInfo);
            Future<JvmInfo> jvmFuture = executor.submit(this::getJvmInfo);
            Future<MemoryInfo> memoryFuture = executor.submit(this::getMemoryInfo);
            Future<StrategyInfo> strategyFuture = executor.submit(this::getStrategyInfo);
            
            printStartupInfo(
                    hostFuture.get(),
                    jvmFuture.get(),
                    memoryFuture.get(),
                    strategyFuture.get()
            );
            
            executor.shutdown();
        } catch (Exception e) {
            log.warn("启动信息收集失败", e);
            printSimpleStartupInfo();
        }
    }

    private void printStartupInfo(HostInfo host, JvmInfo jvm, MemoryInfo memory, StrategyInfo strategy) {
        long startupTime = System.currentTimeMillis() - startTime;
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        String info = BANNER +
            "┌─────────────────────────────────────────────────────────────────┐\n" +
            "│  Application: " + padRight(applicationName, 48) + "│\n" +
            "│  Started at:  " + padRight(now, 48) + "│\n" +
            "│  Startup time: " + padRight(startupTime + "ms", 47) + "│\n" +
            "├─────────────────────────────────────────────────────────────────┤\n" +
            "│  Host:        " + padRight(host.hostname() + " (" + host.ip() + ")", 48) + "│\n" +
            "│  Port:        " + padRight(String.valueOf(serverPort), 48) + "│\n" +
            "│  Profiles:    " + padRight(String.join(", ", host.profiles()), 48) + "│\n" +
            "├─────────────────────────────────────────────────────────────────┤\n" +
            "│  Java:        " + padRight(jvm.version() + " (" + jvm.vendor() + ")", 48) + "│\n" +
            "│  VM:          " + padRight(jvm.vmName(), 48) + "│\n" +
            "│  PID:         " + padRight(String.valueOf(jvm.pid()), 48) + "│\n" +
            "├─────────────────────────────────────────────────────────────────┤\n" +
            "│  Heap Memory: " + padRight(memory.heapUsed() + " / " + memory.heapMax(), 48) + "│\n" +
            "│  Non-Heap:    " + padRight(memory.nonHeapUsed(), 48) + "│\n" +
            "├─────────────────────────────────────────────────────────────────┤\n" +
            "│  Strategy Module Features:                                      │\n" +
            "│    ✓ Rate Limiter (local/redis)                                 │\n" +
            "│    ✓ Debounce (local/redis)                                     │\n" +
            "│    ✓ Circuit Breaker                                            │\n" +
            "│    ✓ Retry                                                      │\n" +
            "│    ✓ Time Limiter                                               │\n" +
            "│    ✓ Bulkhead                                                   │\n" +
            "│    ✓ IP Access Control                                          │\n" +
            "│    ✓ Multi-Level Cache                                          │\n" +
            "└─────────────────────────────────────────────────────────────────┘\n\n" +
            "  ► Local:   http://localhost:" + serverPort + "\n" +
            "  ► Network: http://" + host.ip() + ":" + serverPort + "\n";
        
        System.out.println(info);
    }

    private void printSimpleStartupInfo() {
        long startupTime = System.currentTimeMillis() - startTime;
        log.info("Application '{}' started in {}ms on port {}", applicationName, startupTime, serverPort);
    }

    private HostInfo getHostInfo() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            String[] profiles = environment.getActiveProfiles();
            if (profiles.length == 0) {
                profiles = environment.getDefaultProfiles();
            }
            return new HostInfo(
                    localhost.getHostName(),
                    localhost.getHostAddress(),
                    List.of(profiles)
            );
        } catch (Exception e) {
            return new HostInfo("unknown", "127.0.0.1", List.of("default"));
        }
    }

    private JvmInfo getJvmInfo() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        return new JvmInfo(
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                runtime.getVmName(),
                ProcessHandle.current().pid()
        );
    }

    private MemoryInfo getMemoryInfo() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        var heap = memory.getHeapMemoryUsage();
        var nonHeap = memory.getNonHeapMemoryUsage();
        
        return new MemoryInfo(
                formatBytes(heap.getUsed()),
                formatBytes(heap.getMax()),
                formatBytes(nonHeap.getUsed())
        );
    }

    private StrategyInfo getStrategyInfo() {
        return new StrategyInfo(
                isEnabled("plugin.strategy.rate-limiter.enabled"),
                isEnabled("plugin.strategy.debounce.enabled"),
                isEnabled("plugin.strategy.circuit-breaker.enabled"),
                isEnabled("plugin.cache.enable")
        );
    }

    private boolean isEnabled(String property) {
        return "true".equalsIgnoreCase(environment.getProperty(property, "true"));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String padRight(String s, int length) {
        if (s == null) s = "";
        if (s.length() >= length) return s.substring(0, length);
        return s + " ".repeat(length - s.length());
    }

    // Java 21 Records
    record HostInfo(String hostname, String ip, List<String> profiles) {}
    record JvmInfo(String version, String vendor, String vmName, long pid) {}
    record MemoryInfo(String heapUsed, String heapMax, String nonHeapUsed) {}
    record StrategyInfo(boolean rateLimiter, boolean debounce, boolean circuitBreaker, boolean cache) {}
}
