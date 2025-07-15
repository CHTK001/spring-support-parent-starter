package com.chua.report.client.starter.service;

import com.chua.report.client.starter.pojo.ProcessInfo;
import com.chua.report.client.starter.pojo.ServiceInfo;
import com.chua.report.client.starter.pojo.SystemInfoResponse;

import java.util.List;

/**
 * 系统信息服务接口
 * 
 * @author CH
 * @since 2024/12/19
 */
public interface SystemInfoService {

    /**
     * 获取系统信息
     * 
     * @return 系统信息
     */
    SystemInfoResponse getSystemInfo();

    /**
     * 获取操作系统信息
     * 
     * @return 操作系统信息
     */
    SystemInfoResponse.OperatingSystemInfo getOperatingSystemInfo();

    /**
     * 获取硬件信息
     * 
     * @return 硬件信息
     */
    SystemInfoResponse.HardwareInfo getHardwareInfo();

    /**
     * 获取CPU信息
     * 
     * @return CPU信息
     */
    SystemInfoResponse.CpuInfo getCpuInfo();

    /**
     * 获取内存信息
     * 
     * @return 内存信息
     */
    SystemInfoResponse.MemoryInfo getMemoryInfo();

    /**
     * 获取磁盘信息
     * 
     * @return 磁盘信息列表
     */
    List<SystemInfoResponse.DiskInfo> getDiskInfo();

    /**
     * 获取运行时信息
     * 
     * @return 运行时信息
     */
    SystemInfoResponse.RuntimeInfo getRuntimeInfo();

    /**
     * 获取网络信息
     * 
     * @return 网络信息
     */
    SystemInfoResponse.NetworkInfo getNetworkInfo();

    /**
     * 获取存储信息
     * 
     * @return 存储信息
     */
    SystemInfoResponse.StorageInfo getStorageInfo();

    /**
     * 获取进程列表
     * 
     * @return 进程列表
     */
    List<ProcessInfo> getProcessList();

    /**
     * 获取进程列表（分页）
     * 
     * @param page     页码（从1开始）
     * @param pageSize 页大小
     * @param sortBy   排序字段
     * @param sortOrder 排序顺序
     * @return 进程列表
     */
    List<ProcessInfo> getProcessList(int page, int pageSize, String sortBy, String sortOrder);

    /**
     * 根据进程ID获取进程信息
     * 
     * @param processId 进程ID
     * @return 进程信息
     */
    ProcessInfo getProcessInfo(Long processId);

    /**
     * 根据进程名称搜索进程
     * 
     * @param processName 进程名称
     * @return 进程列表
     */
    List<ProcessInfo> searchProcesses(String processName);

    /**
     * 终止进程
     * 
     * @param processId 进程ID
     * @return 是否成功
     */
    boolean killProcess(Long processId);

    /**
     * 强制终止进程
     * 
     * @param processId 进程ID
     * @return 是否成功
     */
    boolean forceKillProcess(Long processId);

    /**
     * 获取服务列表
     * 
     * @return 服务列表
     */
    List<ServiceInfo> getServiceList();

    /**
     * 获取服务列表（分页）
     * 
     * @param page     页码（从1开始）
     * @param pageSize 页大小
     * @param sortBy   排序字段
     * @param sortOrder 排序顺序
     * @return 服务列表
     */
    List<ServiceInfo> getServiceList(int page, int pageSize, String sortBy, String sortOrder);

    /**
     * 根据服务名称获取服务信息
     * 
     * @param serviceName 服务名称
     * @return 服务信息
     */
    ServiceInfo getServiceInfo(String serviceName);

    /**
     * 根据服务名称搜索服务
     * 
     * @param serviceName 服务名称
     * @return 服务列表
     */
    List<ServiceInfo> searchServices(String serviceName);

    /**
     * 启动服务
     * 
     * @param serviceName 服务名称
     * @return 是否成功
     */
    boolean startService(String serviceName);

    /**
     * 停止服务
     * 
     * @param serviceName 服务名称
     * @return 是否成功
     */
    boolean stopService(String serviceName);

    /**
     * 重启服务
     * 
     * @param serviceName 服务名称
     * @return 是否成功
     */
    boolean restartService(String serviceName);

    /**
     * 获取环境变量
     * 
     * @return 环境变量映射
     */
    java.util.Map<String, String> getEnvironmentVariables();

    /**
     * 获取系统属性
     * 
     * @return 系统属性映射
     */
    java.util.Map<String, String> getSystemProperties();

    /**
     * 获取系统负载信息
     * 
     * @return 负载信息
     */
    SystemLoadInfo getSystemLoad();

    /**
     * 获取网络连接信息
     * 
     * @return 网络连接列表
     */
    List<NetworkConnectionInfo> getNetworkConnections();

    /**
     * 系统负载信息
     */
    class SystemLoadInfo {
        private double loadAverage1min;
        private double loadAverage5min;
        private double loadAverage15min;
        private double cpuUsage;
        private double memoryUsage;
        private double diskUsage;
        private int runningProcesses;
        private int totalProcesses;

        // getters and setters
        public double getLoadAverage1min() { return loadAverage1min; }
        public void setLoadAverage1min(double loadAverage1min) { this.loadAverage1min = loadAverage1min; }
        
        public double getLoadAverage5min() { return loadAverage5min; }
        public void setLoadAverage5min(double loadAverage5min) { this.loadAverage5min = loadAverage5min; }
        
        public double getLoadAverage15min() { return loadAverage15min; }
        public void setLoadAverage15min(double loadAverage15min) { this.loadAverage15min = loadAverage15min; }
        
        public double getCpuUsage() { return cpuUsage; }
        public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }
        
        public double getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(double memoryUsage) { this.memoryUsage = memoryUsage; }
        
        public double getDiskUsage() { return diskUsage; }
        public void setDiskUsage(double diskUsage) { this.diskUsage = diskUsage; }
        
        public int getRunningProcesses() { return runningProcesses; }
        public void setRunningProcesses(int runningProcesses) { this.runningProcesses = runningProcesses; }
        
        public int getTotalProcesses() { return totalProcesses; }
        public void setTotalProcesses(int totalProcesses) { this.totalProcesses = totalProcesses; }
    }

    /**
     * 网络连接信息
     */
    class NetworkConnectionInfo {
        private String protocol;
        private String localAddress;
        private String localPort;
        private String remoteAddress;
        private String remotePort;
        private String state;
        private Long processId;
        private String processName;

        // getters and setters
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        
        public String getLocalAddress() { return localAddress; }
        public void setLocalAddress(String localAddress) { this.localAddress = localAddress; }
        
        public String getLocalPort() { return localPort; }
        public void setLocalPort(String localPort) { this.localPort = localPort; }
        
        public String getRemoteAddress() { return remoteAddress; }
        public void setRemoteAddress(String remoteAddress) { this.remoteAddress = remoteAddress; }
        
        public String getRemotePort() { return remotePort; }
        public void setRemotePort(String remotePort) { this.remotePort = remotePort; }
        
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        
        public Long getProcessId() { return processId; }
        public void setProcessId(Long processId) { this.processId = processId; }
        
        public String getProcessName() { return processName; }
        public void setProcessName(String processName) { this.processName = processName; }
    }
}
