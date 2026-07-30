package com.chua.starter.server.support.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerMetricsDetail {

    private Integer serverId;
    private String serverCode;
    private String hostName;
    private String publicIp;
    private String actualOsName;
    private String actualKernel;
    private String gccVersion;
    private Long collectTimestamp;
    private List<ServerDiskPartitionView> diskPartitions;
    private List<ServerNetworkInterfaceView> networkInterfaces;
    private List<ServerExposurePortView> listeningPorts;
    private List<ServerGpuView> gpus;
    private ServerMemoryDetailView memoryDetail;
    private List<ServerDiskView> disks;
    private ServerCpuDetailView cpuDetail;
    private List<ServerEthernetView> ethernetInterfaces;
}
