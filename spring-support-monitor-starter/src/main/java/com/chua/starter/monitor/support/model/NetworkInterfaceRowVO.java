package com.chua.starter.monitor.support.model;

import lombok.Data;

/**
 * 网卡视图。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class NetworkInterfaceRowVO {

    /**
     * 网卡标识名
     */
    private String name;

    /**
     * 网卡显示名，缺失时回落到标识名
     */
    private String displayName;

    /**
     * 链路状态（UP/DOWN），通道未上报时按 UP 处理
     */
    private String status;

    /**
     * MAC 地址
     */
    private String mac;

    /**
     * IPv4 地址，多地址以逗号连接
     */
    private String ipv4;

    /**
     * IPv6 地址，多地址以逗号连接
     */
    private String ipv6;

    /**
     * 最大传输单元
     */
    private long mtu;

    /**
     * 链路速率展示文本，如 "1.0 Gbps"
     */
    private String speed;
}
