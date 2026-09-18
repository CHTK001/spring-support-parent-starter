package com.chua.agent;

import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.util.*;

/**
 * Agent 侧基于 OSHI 的系统信息采集器。
 *
 * <p>采集结果统一序列化为 JSON 字符串，供 Agent HTTP 接口与指标上报线程使用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class AgentSystemInfo {

    /** OSHI 入口 */
    private final SystemInfo si = new SystemInfo();
    /** 操作系统信息 */
    private final OperatingSystem os = si.getOperatingSystem();
    /** 硬件抽象层 */
    private final HardwareAbstractionLayer hal = si.getHardware();
    /** JSON 序列化器 */
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 采集主机基础信息（主机名 / 系统 / CPU / 内存 / IP / MAC）。
     *
     * @return JSON 字符串
     * @throws Exception 序列化失败
     */
    public String info() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hostname", os.getNetworkParams().getHostName());
        m.put("os", os.toString());
        m.put("arch", System.getProperty("os.arch"));
        m.put("cpuCores", hal.getProcessor().getLogicalProcessorCount());
        long memBytes = hal.getMemory().getTotal();
        m.put("memoryMb", memBytes / 1024 / 1024);
        m.put("publicIp", "");
        m.put("privateIp", getPrimaryIp());
        m.put("mac", getPrimaryMac());
        return json.writeValueAsString(m);
    }

    /**
     * 获取PrimaryIP。
     *
     * @return 结果字符串
     */
    private String getPrimaryIp() {
        try {
            for (NetworkIF nif : hal.getNetworkIFs()) {
                if (!nif.getName().startsWith("lo") && nif.getIPv4addr().length > 0) {
                    return nif.getIPv4addr()[0];
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    /**
     * 获取PrimaryMac。
     *
     * @return 结果字符串
     */
    private String getPrimaryMac() {
        try {
            for (NetworkIF nif : hal.getNetworkIFs()) {
                if (!nif.getName().startsWith("lo") && nif.getMacaddr() != null && !nif.getMacaddr().isEmpty()) {
                    return nif.getMacaddr();
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * 分页采集进程列表（按 CPU 占用倒序）。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return JSON 字符串
     * @throws Exception 序列化失败
     */
    public String processes(int page, int pageSize) throws Exception {
        List<OSProcess> procs = os.getProcesses(null, OperatingSystem.ProcessSorting.CPU_DESC, Integer.MAX_VALUE);
        List<Map<String, Object>> list = new ArrayList<>();
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, procs.size());
        for (int i = from; i < to; i++) {
            OSProcess p = procs.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pid", p.getProcessID());
            m.put("ppid", p.getParentProcessID());
            m.put("cpu", String.format("%.1f", p.getProcessCpuLoadCumulative() * 100));
            m.put("mem", String.format("%.1f", (double) p.getResidentSetSize() / hal.getMemory().getTotal() * 100));
            m.put("rss", p.getResidentSetSize() / 1024);
            m.put("name", p.getName());
            m.put("args", p.getCommandLine());
            list.add(m);
        }
        return json.writeValueAsString(Map.of("list", list, "total", procs.size()));
    }

    /**
     * services。
     *
     * @param page 页，不允许为 null
     * @param pageSize 页大小，不允许为 null
     * @return 结果字符串
     * @throws Exception 当执行过程不满足前置条件时
     */
    public String services(int page, int pageSize) throws Exception {
        List<OSService> services = os.getServices();
        List<Map<String, Object>> list = new ArrayList<>();
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, services.size());
        for (int i = from; i < to; i++) {
            OSService s = services.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.getName());
            m.put("status", s.getState().name());
            m.put("pid", s.getProcessID());
            list.add(m);
        }
        return json.writeValueAsString(Map.of("list", list, "total", services.size()));
    }

    /**
     * disks。
     *
     * @return 结果字符串
     * @throws Exception 当执行过程不满足前置条件时
     */
    public String disks() throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (OSFileStore store : os.getFileSystem().getFileStores()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mount", store.getMount());
            m.put("name", store.getName());
            long total = store.getTotalSpace();
            long free = store.getUsableSpace();
            m.put("size", formatBytes(total));
            m.put("used", formatBytes(total - free));
            m.put("avail", formatBytes(free));
            m.put("use%", total > 0 ? (int)((total - free) * 100 / total) + "%" : "0%");
            list.add(m);
        }
        return json.writeValueAsString(list);
    }

    /**
     * network。
     *
     * @return 结果字符串
     * @throws Exception 当执行过程不满足前置条件时
     */
    public String network() throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (NetworkIF nif : hal.getNetworkIFs()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ifname", nif.getName());
            m.put("mac", nif.getMacaddr());
            m.put("ipv4", String.join(",", nif.getIPv4addr()));
            m.put("ipv6", String.join(",", nif.getIPv6addr()));
            m.put("mtu", nif.getMTU());
            list.add(m);
        }
        return json.writeValueAsString(list);
    }

    /**
     * netProc。
     *
     * @return 结果字符串
     * @throws Exception 当执行过程不满足前置条件时
     */
    public String netProc() throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (NetworkIF nif : hal.getNetworkIFs()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ifname", nif.getName());
            m.put("rxBytes", nif.getBytesRecv());
            m.put("txBytes", nif.getBytesSent());
            list.add(m);
        }
        return json.writeValueAsString(list);
    }

    /**
     * metrics。
     *
     * @return 结果字符串
     * @throws Exception 当执行过程不满足前置条件时
     */
    public String metrics() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        GlobalMemory mem = hal.getMemory();
        m.put("cpu", hal.getProcessor().getSystemCpuLoadBetweenTicks(hal.getProcessor().getSystemCpuLoadTicks()) * 100);
        m.put("memUsed", mem.getTotal() - mem.getAvailable());
        m.put("memTotal", mem.getTotal());
        m.put("memUsedPct", (double)(mem.getTotal() - mem.getAvailable()) / mem.getTotal() * 100);
        m.put("timestamp", System.currentTimeMillis());
        return json.writeValueAsString(m);
    }

    /**
     * 格式化字节数组。
     *
     * @param b 方法入参 b
     * @return 结果字符串
     */
    private String formatBytes(long b) {
        if (b < 1024) {
            return b + " B";
        }
        if (b < 1048576) {
            return String.format("%.1f K", b / 1024.0);
        }
        if (b < 1073741824) {
            return String.format("%.1f M", b / 1048576.0);
        }
        return String.format("%.1f G", b / 1073741824.0);
    }
}
