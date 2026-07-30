package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 以太网适配器视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerEthernetView {

    private String name;
    private String displayName;
    private String connectionType;
    private String ipv4;
    private String ipv6;
    private String macAddress;
    private Double sentBytesPerSecond;
    private Double receivedBytesPerSecond;
    private Long sentPackets;
    private Long receivedPackets;
}
