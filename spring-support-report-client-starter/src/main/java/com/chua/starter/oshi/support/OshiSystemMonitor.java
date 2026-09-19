package com.chua.starter.oshi.support;

import com.chua.oshi.support.Cpu;
import com.chua.oshi.support.Mem;
import com.chua.oshi.support.Network;
import com.chua.oshi.support.Oshi;
import com.chua.oshi.support.Sys;
import com.chua.oshi.support.SysFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OSHI 系统监控封装类。
 * <p>
 * 将 {@link Oshi} 静态工具类的采集结果转换为 映射 结构，供上报模块统一使用。
 * </p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class OshiSystemMonitor {

    /**
     * 采集 CPU 使用率快照。
     *
     * @param tickMillis 采样间隔（毫秒）
     * @return CPU 指标映射
     */
    public Map<String, Object> cpuSnapshot(long tickMillis) {
        Cpu cpu = Oshi.newCpu(tickMillis);
        Map<String, Object> map = new HashMap<>();
        map.put("cpuUsage", cpu.getUsed());
        map.put("cpuUser", cpu.getUser());
        map.put("cpuSys", cpu.getSys());
        map.put("cpuWait", cpu.getWait());
        map.put("cpuFree", cpu.getFree());
        map.put("cpuNum", cpu.getCpuNum());
        return map;
    }

    /**
     * 采集内存快照。
     *
     * @return 内存指标映射
     */
    public Map<String, Object> memorySnapshot() {
        Mem mem = Oshi.newMem();
        Map<String, Object> map = new HashMap<>();
        map.put("total", mem.getTotal());
        map.put("used", mem.getUsed());
        map.put("free", mem.getFree());
        map.put("usage", mem.getUsage());
        return map;
    }

    /**
     * 采集磁盘快照列表。
     *
     * @return 每个磁盘的指标映射列表
     */
    public List<Map<String, Object>> diskSnapshot() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SysFile sysFile : Oshi.newSysFile()) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", sysFile.getDirName());
            map.put("type", sysFile.getTypeName());
            map.put("total", sysFile.getTotal());
            map.put("used", sysFile.getUsed());
            map.put("free", sysFile.getFree());
            map.put("usage", sysFile.getUsage());
            list.add(map);
        }
        return list;
    }

    /**
     * 采集网卡快照列表。
     *
     * @return 每个网卡的指标映射列表
     */
    public List<Map<String, Object>> networkSnapshot() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Network network : Oshi.newNetwork()) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", network.getName());
            map.put("displayName", network.getDisplayName());
            map.put("mac", network.getMac());
            map.put("receiveBytes", network.getReceiveBytes());
            map.put("transmitBytes", network.getTransmitBytes());
            map.put("speed", network.getSpeed());
            list.add(map);
        }
        return list;
    }

    /**
     * 采集系统基本信息快照。
     *
     * @return 系统信息映射
     */
    public Map<String, Object> systemSnapshot() {
        Sys sys = Oshi.newSys();
        Map<String, Object> map = new HashMap<>();
        map.put("ipAddress", sys.getComputerIp());
        map.put("osName", sys.getOsName());
        map.put("osVersion", "");
        map.put("osArch", sys.getOsArch());
        return map;
    }
}
