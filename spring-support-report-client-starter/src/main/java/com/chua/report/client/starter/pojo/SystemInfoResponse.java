package com.chua.report.client.starter.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统信息响应
 * @author CH
 * @since 2024/12/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemInfoResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 操作系统信息
     */
    private OperatingSystemInfo operatingSystem;

    /**
     * 硬件信息
     */
    private HardwareInfo hardware;

    /**
     * 运行时信息
     */
    private RuntimeInfo runtime;

    /**
     * 网络信息
     */
    private NetworkInfo network;

    /**
     * 存储信息
     */
    private StorageInfo storage;

    /**
     * 进程信息
     */
    private List<ProcessInfo> processes;

    /**
     * 服务信息
     */
    private List<ServiceInfo> services;

    /**
     * 环境变量
     */
    private Map<String, String> environmentVariables;

    /**
     * 系统属性
     */
    private Map<String, String> systemProperties;

    /**
     * 收集时间
     */
    private LocalDateTime collectTime;

    /**
     * 操作系统信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperatingSystemInfo implements Serializable {
        private String name;
        private String version;
        private String arch;
        private String manufacturer;
        private String family;
        private int bitness;
        private long uptime;
        private int processCount;
        private int threadCount;
    }

    /**
     * 硬件信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HardwareInfo implements Serializable {
        private CpuInfo cpu;
        private MemoryInfo memory;
        private List<DiskInfo> disks;
        private String manufacturer;
        private String model;
        private String serialNumber;
    }

    /**
     * CPU信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CpuInfo implements Serializable {
        private String name;
        private String vendor;
        private String family;
        private String model;
        private String stepping;
        private int physicalCores;
        private int logicalCores;
        private long maxFreq;
        private double usage;
        private double systemUsage;
        private double userUsage;
    }

    /**
     * 内存信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryInfo implements Serializable {
        private long total;
        private long available;
        private long used;
        private double usage;
        private long swapTotal;
        private long swapUsed;
        private double swapUsage;
    }

    /**
     * 磁盘信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskInfo implements Serializable {
        private String name;
        private String model;
        private String serial;
        private long size;
        private String type;
        private List<PartitionInfo> partitions;
    }

    /**
     * 分区信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartitionInfo implements Serializable {
        private String name;
        private String mountPoint;
        private String fileSystem;
        private long totalSpace;
        private long usableSpace;
        private long usedSpace;
        private double usage;
    }

    /**
     * 运行时信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuntimeInfo implements Serializable {
        private String javaVersion;
        private String javaVendor;
        private String javaHome;
        private String jvmName;
        private String jvmVersion;
        private String jvmVendor;
        private long jvmUptime;
        private long maxMemory;
        private long totalMemory;
        private long freeMemory;
        private long usedMemory;
        private double memoryUsage;
        private int availableProcessors;
        private String workingDirectory;
        private String userName;
        private String userHome;
        private String tempDirectory;
    }

    /**
     * 网络信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkInfo implements Serializable {
        private String hostName;
        private String domainName;
        private List<NetworkInterfaceInfo> interfaces;
    }

    /**
     * 网络接口信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkInterfaceInfo implements Serializable {
        private String name;
        private String displayName;
        private String macAddress;
        private List<String> ipAddresses;
        private boolean up;
        private boolean loopback;
        private boolean virtual;
        private long mtu;
        private long speed;
        private long bytesReceived;
        private long bytesSent;
        private long packetsReceived;
        private long packetsSent;
    }

    /**
     * 存储信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageInfo implements Serializable {
        private long totalSpace;
        private long usableSpace;
        private long usedSpace;
        private double usage;
        private List<FileSystemInfo> fileSystems;
    }

    /**
     * 文件系统信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileSystemInfo implements Serializable {
        private String name;
        private String type;
        private String mountPoint;
        private long totalSpace;
        private long usableSpace;
        private long usedSpace;
        private double usage;
    }

    /**
     * 创建成功响应
     */
    public static SystemInfoResponse success() {
        return SystemInfoResponse.builder()
                .collectTime(LocalDateTime.now())
                .build();
    }

    /**
     * 获取格式化的系统信息摘要
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        
        if (operatingSystem != null) {
            summary.append("操作系统: ").append(operatingSystem.getName())
                   .append(" ").append(operatingSystem.getVersion())
                   .append(" (").append(operatingSystem.getArch()).append(")\n");
        }
        
        if (hardware != null && hardware.getCpu() != null) {
            summary.append("CPU: ").append(hardware.getCpu().getName())
                   .append(" (").append(hardware.getCpu().getPhysicalCores()).append("核)\n");
        }
        
        if (hardware != null && hardware.getMemory() != null) {
            MemoryInfo memory = hardware.getMemory();
            summary.append("内存: ").append(formatBytes(memory.getUsed()))
                   .append("/").append(formatBytes(memory.getTotal()))
                   .append(" (").append(String.format("%.1f%%", memory.getUsage())).append(")\n");
        }
        
        if (runtime != null) {
            summary.append("Java: ").append(runtime.getJavaVersion())
                   .append(" (").append(runtime.getJavaVendor()).append(")\n");
        }
        
        return summary.toString();
    }

    /**
     * 格式化字节数
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
