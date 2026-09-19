package com.chua.starter.hotsport.support.service;

import com.chua.starter.hotsport.support.config.HotsportProperties;
import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.Query;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JVM 指标服务 — 快照 + 环形缓冲时序。
 *
 * <p>基于 JDK PlatformMXBean 采集堆内存 / GC / 线程 / CPU 指标：
 * {@link #snapshot()} 实时读取；{@link #recordSample()} 由
 * {@code @Scheduled}（{@code hotsport.jvm.sample-interval-ms}，默认 5s）驱动写入
 * {@link #RING_CAPACITY} 点环形缓冲，供 {@link #metrics(int)} 返回最近 N 分钟时序。</p>
 *
 * <p>每次采样后经 {@link #onSample} 钩子通知告警监控（由
 * {@link AlertMonitorService} 经 {@code @Lazy} 注入，打破循环依赖）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class JvmMetricsService {

    /**
     * 环形缓冲容量（约 10h @5s 采样）。
    */
    private static final int RING_CAPACITY = 2000;

    private final AtomicReference<ConcurrentLinkedDeque<Map<String, Object>>> ring =
            new AtomicReference<>(new ConcurrentLinkedDeque<>());

    /**
     * 最近一次采样（含告警巡检用派生指标）。
    */
    private final AtomicReference<Map<String, Object>> lastSample = new AtomicReference<>(Map.of());

    /**
     * 采样次数（用于 QPS 窗口估算）。
    */
    private final AtomicLong sampleCount = new AtomicLong(0);

    /**
     * 采样钩子（AlertMonitorService 经 @Lazy 构造注入后注册，null 安全）。
    */
    private volatile Runnable onSample;

    /**
     * 注册采样后钩子（告警监控巡检）。
     *
     * @param hook 钩子
     */
    public void registerSampleHook(Runnable hook) {
        this.onSample = hook;
    }

    /**
     * 启动即取首个采样，环形缓冲立即可读。
    */
    @PostConstruct
    public void init() {
        recordSample();
    }

    /**
     * 定时采样（间隔由 {@code hotsport.jvm.sample-interval-ms} 控制，默认 5000ms）。
    */
    @Scheduled(fixedDelayString = "${hotsport.jvm.sample-interval-ms:5000}")
    public void recordSample() {
        Map<String, Object> snapshot = snapshot();
        ring.get().addLast(snapshot);
        while (ring.get().size() > RING_CAPACITY) {
            ring.get().pollFirst();
        }
        sampleCount.incrementAndGet();
        lastSample.set(snapshot);
        if (onSample != null) {
            try {
                onSample.run();
            } catch (Exception e) {
                log.debug("[Hotsport] 采样钩子执行失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 实时 JVM 快照（PlatformMXBean）。
     *
     * @return 快照 Map（memory/gc/cpu/threads/network/process）
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ts", System.currentTimeMillis());
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("heapUsed", heap.getUsed());
        memory.put("heapMax", heap.getMax());
        memory.put("heapCommitted", heap.getCommitted());
        memory.put("heapUsedPercent", heap.getMax() > 0 ? Math.round((100.0 * heap.getUsed()) / heap.getMax()) : 0);
        memory.put("nonHeapUsed", mem.getNonHeapMemoryUsage().getUsed());
        m.put("memory", memory);

        Map<String, Object> gc = new LinkedHashMap<>();
        long gcCount = 0L;
        long gcTime = 0L;
        List<String> collectors = new ArrayList<>();
        for (GarbageCollectorMXBean c : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += c.getCollectionCount();
            gcTime += c.getCollectionTime();
            collectors.add(c.getName());
        }
        gc.put("collectors", collectors);
        gc.put("gcCount", gcCount);
        gc.put("gcTimeMs", gcTime);
        m.put("gc", gc);

        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("cpuCores", os.getAvailableProcessors());
        cpu.put("systemLoadAverage", Math.round(os.getSystemLoadAverage() * 100) / 100.0);
        cpu.put("loadPerCore", Math.round(os.getSystemLoadAverage() / Math.max(1, os.getAvailableProcessors()) * 100) / 100.0);
        cpu.put("processCpuLoad", Math.round(((com.sun.management.OperatingSystemMXBean) os).getProcessCpuLoad() * 10000) / 100.0);
        m.put("cpu", cpu);

        ThreadMXBean th = ManagementFactory.getThreadMXBean();
        Map<String, Object> threads = new LinkedHashMap<>();
        threads.put("totalThreads", th.getThreadCount());
        threads.put("peakThreads", th.getPeakThreadCount());
        threads.put("daemonThreads", th.getDaemonThreadCount());
        threads.put("liveThreads", th.getThreadCount());
        m.put("threads", threads);

        Map<String, Object> network = new LinkedHashMap<>();
        network.put("listeners", 0);
        m.put("network", network);

        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> process = new LinkedHashMap<>();
        process.put("pid", rt.getPid());
        process.put("uptimeMs", rt.getUptime());
        process.put("javaHome", System.getProperty("java.home"));
        m.put("process", process);
        return m;
    }

    /**
     * 最近 N 分钟时序（堆百分比 / GC 次数 / GC 时间 / 线程数）。
     *
     * @param minutes 时间窗（分钟），&lt;=0 时返回全部缓冲
     * @return 时序点列表（按时间升序）
     */
    public List<Map<String, Object>> metrics(int minutes) {
        long cutoff = minutes > 0 ? System.currentTimeMillis() - minutes * 60_000L : Long.MIN_VALUE;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : ring.get()) {
            long ts = ((Number) p.get("ts")).longValue();
            if (ts >= cutoff) {
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("ts", ts);
                q.put("heapUsed", ((Map<String, Object>) p.get("memory")).get("heapUsed"));
                q.put("heapPercent", ((Map<String, Object>) p.get("memory")).get("heapUsedPercent"));
                q.put("gcCount", ((Map<String, Object>) p.get("gc")).get("gcCount"));
                q.put("gcTimeMs", ((Map<String, Object>) p.get("gc")).get("gcTimeMs"));
                q.put("liveThreads", ((Map<String, Object>) p.get("threads")).get("liveThreads"));
                out.add(q);
            }
        }
        Collections.reverse(out);
        return out;
    }

    /**
     * 最近一次采样（告警巡检读 QPS / 活跃泄漏）。
     *
     * @return 采样 Map（永不为 null）
     */
    public Map<String, Object> lastSample() {
        return lastSample.get();
    }

    /**
     * 关闭清理。
     */
    @PreDestroy
    public void shutdown() {
        onSample = null;
    }

    /**
     * 当前活跃泄漏数（告警巡检用，读 APM 存储兜底 0）。
     * @return 结果数值
     */
    public int activeLeakCount() {
        try {
            ApmStorage storage = HotsportProperties.storage();
            if (storage == null) {
                return 0;
            }
            List<?> leaks = storage.queryLeaks(new Query().setLimit(100000));
            if (leaks == null) {
                return 0;
            }
            int count = 0;
            for (Object o : leaks) {
                if (o instanceof com.chua.runtime.apm.storage.LeakRecord l && l.isActive()) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            log.debug("[Hotsport] 活跃泄漏统计失败: {}", e.getMessage());
            return 0;
        }
    }
}
