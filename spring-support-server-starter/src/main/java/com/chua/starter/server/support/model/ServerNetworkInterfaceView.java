package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerNetworkInterfaceView {

    private String name;
    private String displayName;
    private String status;
    private String ipv4;
    private String macAddress;
    private Long receivedBytes;
    private Long transmittedBytes;
    private Long receivedPackets;
    private Long transmittedPackets;
}
