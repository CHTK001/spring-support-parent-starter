package com.chua.report.client.starter.service.impl;

import com.chua.report.client.starter.pojo.ProcessInfo;
import com.chua.report.client.starter.pojo.ServiceInfo;
import com.chua.report.client.starter.pojo.SystemInfoResponse;
import com.chua.report.client.starter.service.SystemInfoService;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统信息服务实现
 * 
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
public class SystemInfoServiceImpl implements SystemInfoService {

    @Override
    public SystemInfoResponse getSystemInfo() {
        return SystemInfoResponse.builder()
                .operatingSystem(getOperatingSystemInfo())
                .hardware(getHardwareInfo())
                .runtime(getRuntimeInfo())
                .network(getNetworkInfo())
                .storage(getStorageInfo())
                .processes(getProcessList(1, 50, "cpu", "desc"))
                .services(getServiceList(1, 50, "name", "asc"))
                .environmentVariables(getEnvironmentVariables())
                .systemProperties(getSystemProperties())
                .collectTime(LocalDateTime.now())
                .build();
    }

    @Override
    public SystemInfoResponse.OperatingSystemInfo getOperatingSystemInfo() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        
        return SystemInfoResponse.OperatingSystemInfo.builder()
                .name(System.getProperty("os.name"))
                .version(System.getProperty("os.version"))
                .arch(System.getProperty("os.arch"))
                .manufacturer(getOsManufacturer())
                .family(getOsFamily())
                .bitness(getBitness())
                .uptime(getSystemUptime())
                .processCount(osBean.getAvailableProcessors())
                .threadCount(ManagementFactory.getThreadMXBean().getThreadCount())
                .build();
    }

    @Override
    public SystemInfoResponse.HardwareInfo getHardwareInfo() {
        return SystemInfoResponse.HardwareInfo.builder()
                .cpu(getCpuInfo())
                .memory(getMemoryInfo())
                .disks(getDiskInfo())
                .manufacturer("Unknown")
                .model("Unknown")
                .serialNumber("Unknown")
                .build();
    }

    @Override
    public SystemInfoResponse.CpuInfo getCpuInfo() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        
        return SystemInfoResponse.CpuInfo.builder()
                .name(System.getProperty("java.vm.name"))
                .vendor(System.getProperty("java.vm.vendor"))
                .family("Unknown")
                .model("Unknown")
                .stepping("Unknown")
                .physicalCores(osBean.getAvailableProcessors())
                .logicalCores(osBean.getAvailableProcessors())
                .maxFreq(0L)
                .usage(getCpuUsage())
                .systemUsage(0.0)
                .userUsage(0.0)
                .build();
    }

    @Override
    public SystemInfoResponse.MemoryInfo getMemoryInfo() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Runtime runtime = Runtime.getRuntime();
        
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = total - free;
        double usage = (double) used / total * 100;
        
        return SystemInfoResponse.MemoryInfo.builder()
                .total(total)
                .available(free)
                .used(used)
                .usage(usage)
                .swapTotal(0L)
                .swapUsed(0L)
                .swapUsage(0.0)
                .build();
    }

    @Override
    public List<SystemInfoResponse.DiskInfo> getDiskInfo() {
        List<SystemInfoResponse.DiskInfo> disks = new ArrayList<>();
        
        File[] roots = File.listRoots();
        for (File root : roots) {
            List<SystemInfoResponse.PartitionInfo> partitions = new ArrayList<>();
            
            long totalSpace = root.getTotalSpace();
            long usableSpace = root.getUsableSpace();
            long usedSpace = totalSpace - usableSpace;
            double usage = totalSpace > 0 ? (double) usedSpace / totalSpace * 100 : 0;
            
            SystemInfoResponse.PartitionInfo partition = SystemInfoResponse.PartitionInfo.builder()
                    .name(root.getPath())
                    .mountPoint(root.getPath())
                    .fileSystem("Unknown")
                    .totalSpace(totalSpace)
                    .usableSpace(usableSpace)
                    .usedSpace(usedSpace)
                    .usage(usage)
                    .build();
            
            partitions.add(partition);
            
            SystemInfoResponse.DiskInfo disk = SystemInfoResponse.DiskInfo.builder()
                    .name(root.getPath())
                    .model("Unknown")
                    .serial("Unknown")
                    .size(totalSpace)
                    .type("Unknown")
                    .partitions(partitions)
                    .build();
            
            disks.add(disk);
        }
        
        return disks;
    }

    @Override
    public SystemInfoResponse.RuntimeInfo getRuntimeInfo() {
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        Runtime runtime = Runtime.getRuntime();
        
        return SystemInfoResponse.RuntimeInfo.builder()
                .javaVersion(System.getProperty("java.version"))
                .javaVendor(System.getProperty("java.vendor"))
                .javaHome(System.getProperty("java.home"))
                .jvmName(runtimeBean.getVmName())
                .jvmVersion(runtimeBean.getVmVersion())
                .jvmVendor(runtimeBean.getVmVendor())
                .jvmUptime(runtimeBean.getUptime())
                .maxMemory(runtime.maxMemory())
                .totalMemory(runtime.totalMemory())
                .freeMemory(runtime.freeMemory())
                .usedMemory(runtime.totalMemory() - runtime.freeMemory())
                .memoryUsage((double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.totalMemory() * 100)
                .availableProcessors(runtime.availableProcessors())
                .workingDirectory(System.getProperty("login.dir"))
                .userName(System.getProperty("login.name"))
                .userHome(System.getProperty("login.home"))
                .tempDirectory(System.getProperty("java.io.tmpdir"))
                .build();
    }

    @Override
    public SystemInfoResponse.NetworkInfo getNetworkInfo() {
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            String domainName = InetAddress.getLocalHost().getCanonicalHostName();
            
            List<SystemInfoResponse.NetworkInterfaceInfo> interfaces = new ArrayList<>();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                
                List<String> ipAddresses = new ArrayList<>();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    ipAddresses.add(inetAddress.getHostAddress());
                }
                
                byte[] mac = networkInterface.getHardwareAddress();
                String macAddress = mac != null ? formatMacAddress(mac) : "Unknown";
                
                SystemInfoResponse.NetworkInterfaceInfo interfaceInfo = SystemInfoResponse.NetworkInterfaceInfo.builder()
                        .name(networkInterface.getName())
                        .displayName(networkInterface.getDisplayName())
                        .macAddress(macAddress)
                        .ipAddresses(ipAddresses)
                        .up(networkInterface.isUp())
                        .loopback(networkInterface.isLoopback())
                        .virtual(networkInterface.isVirtual())
                        .mtu(networkInterface.getMTU())
                        .speed(0L)
                        .bytesReceived(0L)
                        .bytesSent(0L)
                        .packetsReceived(0L)
                        .packetsSent(0L)
                        .build();
                
                interfaces.add(interfaceInfo);
            }
            
            return SystemInfoResponse.NetworkInfo.builder()
                    .hostName(hostName)
                    .domainName(domainName)
                    .interfaces(interfaces)
                    .build();
                    
        } catch (Exception e) {
            log.error("获取网络信息失败", e);
            return SystemInfoResponse.NetworkInfo.builder()
                    .hostName("Unknown")
                    .domainName("Unknown")
                    .interfaces(new ArrayList<>())
                    .build();
        }
    }

    @Override
    public SystemInfoResponse.StorageInfo getStorageInfo() {
        File[] roots = File.listRoots();
        long totalSpace = 0;
        long usableSpace = 0;
        
        List<SystemInfoResponse.FileSystemInfo> fileSystems = new ArrayList<>();
        
        for (File root : roots) {
            long rootTotal = root.getTotalSpace();
            long rootUsable = root.getUsableSpace();
            long rootUsed = rootTotal - rootUsable;
            double rootUsage = rootTotal > 0 ? (double) rootUsed / rootTotal * 100 : 0;
            
            totalSpace += rootTotal;
            usableSpace += rootUsable;
            
            SystemInfoResponse.FileSystemInfo fileSystem = SystemInfoResponse.FileSystemInfo.builder()
                    .name(root.getPath())
                    .type("Unknown")
                    .mountPoint(root.getPath())
                    .totalSpace(rootTotal)
                    .usableSpace(rootUsable)
                    .usedSpace(rootUsed)
                    .usage(rootUsage)
                    .build();
            
            fileSystems.add(fileSystem);
        }
        
        long usedSpace = totalSpace - usableSpace;
        double usage = totalSpace > 0 ? (double) usedSpace / totalSpace * 100 : 0;
        
        return SystemInfoResponse.StorageInfo.builder()
                .totalSpace(totalSpace)
                .usableSpace(usableSpace)
                .usedSpace(usedSpace)
                .usage(usage)
                .fileSystems(fileSystems)
                .build();
    }

    @Override
    public List<ProcessInfo> getProcessList() {
        return getProcessList(1, 100, "name", "asc");
    }

    @Override
    public List<ProcessInfo> getProcessList(int page, int pageSize, String sortBy, String sortOrder) {
        // 简单实现，实际应该调用系统API获取进程信息
        List<ProcessInfo> processes = new ArrayList<>();
        
        // 获取当前Java进程信息
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        String jvmName = runtimeBean.getName();
        String[] parts = jvmName.split("@");
        long currentPid = Long.parseLong(parts[0]);
        
        ProcessInfo currentProcess = ProcessInfo.builder()
                .processId(currentPid)
                .name("java")
                .path(System.getProperty("java.home"))
                .commandLine(String.join(" ", runtimeBean.getInputArguments()))
                .state("RUNNING")
                .user(System.getProperty("login.name"))
                .startTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(runtimeBean.getStartTime()),
                    ZoneId.systemDefault()))
                .upTime(runtimeBean.getUptime())
                .build();
        
        processes.add(currentProcess);
        
        return processes;
    }

    @Override
    public ProcessInfo getProcessInfo(Long processId) {
        List<ProcessInfo> processes = getProcessList();
        return processes.stream()
                .filter(p -> processId.equals(p.getProcessId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<ProcessInfo> searchProcesses(String processName) {
        List<ProcessInfo> processes = getProcessList();
        return processes.stream()
                .filter(p -> p.getName() != null && p.getName().contains(processName))
                .collect(Collectors.toList());
    }

    @Override
    public boolean killProcess(Long processId) {
        // 实际实现应该调用系统API终止进程
        log.warn("终止进程功能未实现: {}", processId);
        return false;
    }

    @Override
    public boolean forceKillProcess(Long processId) {
        // 实际实现应该调用系统API强制终止进程
        log.warn("强制终止进程功能未实现: {}", processId);
        return false;
    }

    @Override
    public List<ServiceInfo> getServiceList() {
        return getServiceList(1, 100, "name", "asc");
    }

    @Override
    public List<ServiceInfo> getServiceList(int page, int pageSize, String sortBy, String sortOrder) {
        // 简单实现，实际应该调用系统API获取服务信息
        List<ServiceInfo> services = new ArrayList<>();
        
        // 模拟一些常见服务
        services.add(ServiceInfo.createSimple("java-app", "Java Application", "RUNNING", "AUTOMATIC"));
        
        return services;
    }

    @Override
    public ServiceInfo getServiceInfo(String serviceName) {
        List<ServiceInfo> services = getServiceList();
        return services.stream()
                .filter(s -> serviceName.equals(s.getName()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<ServiceInfo> searchServices(String serviceName) {
        List<ServiceInfo> services = getServiceList();
        return services.stream()
                .filter(s -> s.getName() != null && s.getName().contains(serviceName))
                .collect(Collectors.toList());
    }

    @Override
    public boolean startService(String serviceName) {
        log.warn("启动服务功能未实现: {}", serviceName);
        return false;
    }

    @Override
    public boolean stopService(String serviceName) {
        log.warn("停止服务功能未实现: {}", serviceName);
        return false;
    }

    @Override
    public boolean restartService(String serviceName) {
        log.warn("重启服务功能未实现: {}", serviceName);
        return false;
    }

    @Override
    public Map<String, String> getEnvironmentVariables() {
        return System.getenv();
    }

    @Override
    public Map<String, String> getSystemProperties() {
        Properties props = System.getProperties();
        Map<String, String> result = new HashMap<>();
        for (Object key : props.keySet()) {
            result.put(key.toString(), props.getProperty(key.toString()));
        }
        return result;
    }

    @Override
    public SystemLoadInfo getSystemLoad() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        
        SystemLoadInfo loadInfo = new SystemLoadInfo();
        loadInfo.setCpuUsage(getCpuUsage());
        loadInfo.setMemoryUsage(getMemoryUsage());
        loadInfo.setDiskUsage(getDiskUsage());
        loadInfo.setRunningProcesses(1);
        loadInfo.setTotalProcesses(1);
        
        return loadInfo;
    }

    @Override
    public List<NetworkConnectionInfo> getNetworkConnections() {
        // 简单实现，实际应该调用系统API获取网络连接信息
        return new ArrayList<>();
    }

    // 私有辅助方法
    private String getOsManufacturer() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            return "Microsoft";
        } else if (osName.contains("linux")) {
            return "Linux Foundation";
        } else if (osName.contains("mac")) {
            return "Apple";
        }
        return "Unknown";
    }

    private String getOsFamily() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            return "Windows";
        } else if (osName.contains("linux")) {
            return "Linux";
        } else if (osName.contains("mac")) {
            return "macOS";
        }
        return "Unknown";
    }

    private int getBitness() {
        String arch = System.getProperty("os.arch");
        return arch.contains("64") ? 64 : 32;
    }

    private long getSystemUptime() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }

    private double getCpuUsage() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad() * 100;
        }
        return 0.0;
    }

    private double getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        return (double) (total - free) / total * 100;
    }

    private double getDiskUsage() {
        File[] roots = File.listRoots();
        long totalSpace = 0;
        long usableSpace = 0;
        
        for (File root : roots) {
            totalSpace += root.getTotalSpace();
            usableSpace += root.getUsableSpace();
        }
        
        if (totalSpace > 0) {
            return (double) (totalSpace - usableSpace) / totalSpace * 100;
        }
        return 0.0;
    }

    private String formatMacAddress(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
        }
        return sb.toString();
    }
}
