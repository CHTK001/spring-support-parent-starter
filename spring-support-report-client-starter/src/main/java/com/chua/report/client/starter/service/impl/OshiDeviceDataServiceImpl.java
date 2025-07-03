package com.chua.report.client.starter.service.impl;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.net.NetUtils;
import com.chua.report.client.starter.pojo.DeviceMetrics;
import com.chua.report.client.starter.pojo.DiskPartitionInfo;
import com.chua.report.client.starter.properties.ReportClientProperties;
import com.chua.report.client.starter.service.DeviceDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OSFileStore;
import cn.hutool.system.oshi.OshiUtil;
import cn.hutool.system.oshi.CpuInfo;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * 基于OSHI的设备数据服务实现
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Service
@Spi("oshi")
@RequiredArgsConstructor
public class OshiDeviceDataServiceImpl implements DeviceDataService {

    private final ReportClientProperties properties;
    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();
    private final OperatingSystem os = systemInfo.getOperatingSystem();

    @Override
    public DeviceMetrics getDeviceMetrics() {
        DeviceMetrics metrics = new DeviceMetrics();
        
        try {
            // 设备基本信息
            setDeviceBasicInfo(metrics);
            
            // CPU信息
            setCpuMetrics(metrics);
            
            // 内存信息
            setMemoryMetrics(metrics);
            
            // 磁盘信息
            setDiskMetrics(metrics);
            
            // 网络信息
            setNetworkMetrics(metrics);
            
            // 系统信息
            setSystemMetrics(metrics);
            
            // 温度信息
            setTemperatureMetrics(metrics);
            
            metrics.setOnline(true);
            metrics.setCollectTime(LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("获取设备指标数据失败", e);
            metrics.setOnline(false);
        }
        
        return metrics;
    }

    @Override
    public DeviceMetrics getDeviceInfo() {
        DeviceMetrics info = new DeviceMetrics();
        
        try {
            setDeviceBasicInfo(info);
            info.setOnline(true);
            info.setCollectTime(LocalDateTime.now());
        } catch (Exception e) {
            log.error("获取设备基本信息失败", e);
            info.setOnline(false);
        }
        
        return info;
    }

    @Override
    public boolean isDeviceOnline() {
        try {
            // 检查网络连接
            return NetUtils.isConnectable("127.0.0.1", 80);
        } catch (Exception e) {
            log.warn("检查设备在线状态失败", e);
            return false;
        }
    }

    /**
     * 设置设备基本信息
     */
    private void setDeviceBasicInfo(DeviceMetrics metrics) {
        try {
            // 设备标识
            metrics.setDeviceId(properties.getDeviceId() != null ? 
                properties.getDeviceId() : getDefaultDeviceId());
            metrics.setDeviceName(properties.getDeviceName() != null ? 
                properties.getDeviceName() : getDefaultDeviceName());
            
            // IP地址
            metrics.setIpAddress(NetUtils.getLocalHost());

            // 操作系统信息
            metrics.setOperatingSystem(os.getFamily());
            metrics.setSystemVersion(os.getVersionInfo().getVersion());
            metrics.setArchitecture(System.getProperty("os.arch"));

            // 主机名
            metrics.setHostname(InetAddress.getLocalHost().getHostName());
            
        } catch (Exception e) {
            log.warn("设置设备基本信息失败", e);
        }
    }

    /**
     * 设置CPU指标
     */
    private void setCpuMetrics(DeviceMetrics metrics) {
        try {
            CentralProcessor processor = hardware.getProcessor();

            // CPU使用率 - 使用HuTool的OshiUtil获取CPU信息
            CpuInfo cpuInfo = OshiUtil.getCpuInfo(1000); // 等待1秒获取准确的CPU使用率
            double cpuUsage = cpuInfo.getUsed();
            metrics.setCpuUsage(cpuUsage);

            // CPU核心数
            metrics.setCpuCores(processor.getLogicalProcessorCount());

            // CPU频率 - DeviceMetrics中没有cpuFrequency字段，暂时跳过
            // metrics.setCpuFrequency(processor.getMaxFreq());

        } catch (Exception e) {
            log.warn("设置CPU指标失败", e);
        }
    }

    /**
     * 设置内存指标
     */
    private void setMemoryMetrics(DeviceMetrics metrics) {
        try {
            GlobalMemory memory = hardware.getMemory();
            
            long totalMemory = memory.getTotal();
            long availableMemory = memory.getAvailable();
            long usedMemory = totalMemory - availableMemory;
            
            metrics.setTotalMemory(totalMemory);
            metrics.setUsedMemory(usedMemory);
            metrics.setAvailableMemory(availableMemory);
            
            // 内存使用率
            double memoryUsage = totalMemory > 0 ? (double) usedMemory / totalMemory * 100 : 0;
            metrics.setMemoryUsage(memoryUsage);
            
        } catch (Exception e) {
            log.warn("设置内存指标失败", e);
        }
    }

    /**
     * 设置磁盘指标
     */
    private void setDiskMetrics(DeviceMetrics metrics) {
        try {
            List<HWDiskStore> diskStores = hardware.getDiskStores();

            long totalDisk = 0;
            long usedDisk = 0;
            List<DiskPartitionInfo> diskPartitions = new ArrayList<>();

            for (HWDiskStore disk : diskStores) {
                totalDisk += disk.getSize();
                // 这里简化处理，实际应该通过文件系统获取使用情况
            }

            // 通过文件系统获取更准确的磁盘使用情况和分区信息
            OperatingSystem os = systemInfo.getOperatingSystem();
            List<OSFileStore> fileStores = os.getFileSystem().getFileStores();

            for (OSFileStore fs : fileStores) {
                long totalSpace = fs.getTotalSpace();
                long freeSpace = fs.getFreeSpace();
                long usedSpace = totalSpace - freeSpace;
                double usagePercent = totalSpace > 0 ? (double) usedSpace / totalSpace * 100 : 0;

                // 累计总磁盘使用情况
                totalDisk += totalSpace;
                usedDisk += usedSpace;

                // 创建磁盘分区信息
                DiskPartitionInfo partitionInfo = DiskPartitionInfo.builder()
                    .name(fs.getName())
                    .mount(fs.getMount())
                    .type(fs.getType())
                    .totalSpace(totalSpace)
                    .freeSpace(freeSpace)
                    .usedSpace(usedSpace)
                    .usagePercent(usagePercent)
                    .isPrimary(fs.getMount().equals("/") || fs.getMount().equals("C:\\"))
                    .deviceName(fs.getName())
                    .build();
                diskPartitions.add(partitionInfo);
            }

            long availableDisk = totalDisk - usedDisk;

            metrics.setTotalDisk(totalDisk);
            metrics.setUsedDisk(usedDisk);
            metrics.setAvailableDisk(availableDisk);
            metrics.setDiskPartitions(diskPartitions);

            // 磁盘使用率
            double diskUsage = totalDisk > 0 ? (double) usedDisk / totalDisk * 100 : 0;
            metrics.setDiskUsage(diskUsage);

        } catch (Exception e) {
            log.warn("设置磁盘指标失败", e);
        }
    }

    /**
     * 设置网络指标
     */
    private void setNetworkMetrics(DeviceMetrics metrics) {
        try {
            List<NetworkIF> networkIFs = hardware.getNetworkIFs();
            
            long totalBytesRecv = 0;
            long totalBytesSent = 0;
            long totalPacketsRecv = 0;
            long totalPacketsSent = 0;
            
            for (NetworkIF net : networkIFs) {
                net.updateAttributes();
                totalBytesRecv += net.getBytesRecv();
                totalBytesSent += net.getBytesSent();
                totalPacketsRecv += net.getPacketsRecv();
                totalPacketsSent += net.getPacketsSent();
            }
            
            metrics.setNetworkBytesReceived(totalBytesRecv);
            metrics.setNetworkBytesSent(totalBytesSent);
            metrics.setNetworkPacketsReceived(totalPacketsRecv);
            metrics.setNetworkPacketsSent(totalPacketsSent);
            
        } catch (Exception e) {
            log.warn("设置网络指标失败", e);
        }
    }

    /**
     * 设置系统指标
     */
    private void setSystemMetrics(DeviceMetrics metrics) {
        try {
            // 系统负载
            double[] loadAverage = hardware.getProcessor().getSystemLoadAverage(3);
            if (loadAverage[0] >= 0) {
                metrics.setLoadAverage1m(loadAverage[0]);
                if (loadAverage.length > 1) {
                    metrics.setLoadAverage5m(loadAverage[1]);
                }
                if (loadAverage.length > 2) {
                    metrics.setLoadAverage15m(loadAverage[2]);
                }
            }
            
            // 系统运行时间
            metrics.setUptime(os.getSystemUptime());
            
            // 进程和线程数
            metrics.setProcessCount(os.getProcessCount());
            metrics.setThreadCount(os.getThreadCount());
            
        } catch (Exception e) {
            log.warn("设置系统指标失败", e);
        }
    }

    /**
     * 设置温度指标
     */
    private void setTemperatureMetrics(DeviceMetrics metrics) {
        try {
            Sensors sensors = hardware.getSensors();
            double cpuTemperature = sensors.getCpuTemperature();
            if (cpuTemperature > 0) {
                metrics.setCpuTemperature(cpuTemperature);
            }
        } catch (Exception e) {
            log.warn("设置温度指标失败", e);
        }
    }

    /**
     * 获取默认设备ID
     */
    private String getDefaultDeviceId() {
        try {
            return hardware.getComputerSystem().getHardwareUUID();
        } catch (Exception e) {
            return "unknown-device-" + System.currentTimeMillis();
        }
    }

    /**
     * 获取默认设备名称
     */
    private String getDefaultDeviceName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-device";
        }
    }
}
