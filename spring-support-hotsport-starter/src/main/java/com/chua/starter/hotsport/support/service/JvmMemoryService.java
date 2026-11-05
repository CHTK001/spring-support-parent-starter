// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.hotsport.support.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JVM 内存诊断服务 — 对象内存查看 + 手动 GC / 释放内存。
 *
 * <p>与前端的 {@code jvm/snapshot} 不同，这里聚焦可操作能力：
 * <ul>
 *   <li>{@link #memoryPools()}：各内存池对象占用（used/committed/max）+ 按池名推断对象类型</li>
 *   <li>{@link #gc()}：触发 {@link System#gc()}，返回 GC 前后堆用量差与累计 GC 次数</li>
 *   <li>{@link #classLoading()}：已加载类 / 类加载器统计（辅助定位 classloader 泄漏）</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @since 4.1.0
 */
@Slf4j
@Service
public class JvmMemoryService {

    /**
     * 内存池列表（每个池含 used/committed/max + 对象近似数量）。
     *
     * @return 内存池明细
     */
    public Map<String, Object> memoryPools() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ts", System.currentTimeMillis());
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        Map<String, Object> heapSummary = memoryUsageMap("heap", mem.getHeapMemoryUsage());
        Map<String, Object> nonHeapSummary = memoryUsageMap("non-heap", mem.getNonHeapMemoryUsage());
        result.put("heap", heapSummary);
        result.put("nonHeap", nonHeapSummary);

        List<Map<String, Object>> pools = new ArrayList<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", pool.getName());
            p.put("type", pool.getType() == MemoryType.HEAP ? "HEAP" : "NON_HEAP");
            MemoryUsage usage = pool.getUsage();
            if (usage != null) {
                p.put("used", usage.getUsed());
                p.put("committed", usage.getCommitted());
                p.put("max", usage.getMax());
                p.put("usedPercent", usage.getMax() > 0
                        ? Math.round((100.0 * usage.getUsed()) / usage.getMax()) : 0);
            }
            MemoryUsage peak = pool.getPeakUsage();
            if (peak != null) {
                p.put("peakUsed", peak.getUsed());
            }
            p.put("objectEstimate", estimateObjects(pool));
            pools.add(p);
        }
        result.put("pools", pools);
        return result;
    }

    /**
     * 触发显式 GC 并返回释放量。
     *
     * @return GC 前后堆用量、差值、GC 累计次数
     */
    public Map<String, Object> gc() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        long before = mem.getHeapMemoryUsage().getUsed();
        long gcBefore = totalGcCount();
        long t0 = System.currentTimeMillis();
        // 显式 GC（生产默认 System.gc() 可能被 -XX:+DisableExplicitGC 关闭）
        mem.gc();
        long t1 = System.currentTimeMillis();
        long after = mem.getHeapMemoryUsage().getUsed();
        long gcAfter = totalGcCount();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("triggered", true);
        result.put("beforeBytes", before);
        result.put("afterBytes", after);
        result.put("freedBytes", Math.max(0, before - after));
        result.put("freedMb", (before - after) / 1024.0 / 1024.0);
        result.put("gcCountBefore", gcBefore);
        result.put("gcCountAfter", gcAfter);
        result.put("gcDelta", Math.max(0, gcAfter - gcBefore));
        result.put("elapsedMs", t1 - t0);
        return result;
    }

    /**
     * 类加载统计。
     *
     * @return 类加载信息
     */
    public Map<String, Object> classLoading() {
        ClassLoadingMXBean cl = ManagementFactory.getClassLoadingMXBean();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loadedClassCount", cl.getLoadedClassCount());
        result.put("totalLoadedClassCount", cl.getTotalLoadedClassCount());
        result.put("unloadedClassCount", cl.getUnloadedClassCount());
        result.put("verbose", cl.isVerbose());
        result.put("ts", System.currentTimeMillis());
        return result;
    }

    /**
     * 把 MemoryUsage 转为前端友好的 Map。
     *
     * @param name  名称
     * @param usage 内存用量
     * @return Map
     */
    private Map<String, Object> memoryUsageMap(String name, MemoryUsage usage) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("used", usage.getUsed());
        m.put("committed", usage.getCommitted());
        m.put("max", usage.getMax());
        m.put("usedPercent", usage.getMax() > 0
                ? Math.round((100.0 * usage.getUsed()) / usage.getMax()) : 0);
        return m;
    }

    /**
     * 基于内存池类型粗略估算对象数量（对象平均 256B 假设）。
     *
     * @param pool 内存池
     * @return 对象数量估算
     */
    private long estimateObjects(MemoryPoolMXBean pool) {
        MemoryUsage usage = pool.getUsage();
        if (usage == null || usage.getUsed() <= 0) {
            return 0;
        }
        return usage.getUsed() / 256L;
    }

    /**
     * 累计 GC 次数。
     *
     * @return GC 次数
     */
    private long totalGcCount() {
        long sum = 0L;
        for (var c : ManagementFactory.getGarbageCollectorMXBeans()) {
            long n = c.getCollectionCount();
            sum += (n > 0 ? n : 0L);
        }
        return sum;
    }}