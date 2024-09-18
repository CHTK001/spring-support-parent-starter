package com.chua.report.client.starter.report.event;

import lombok.Data;

import java.util.List;

/**
 * 系统信息
 * @author CH
 * @since 2024/9/18
 */
@Data
public class SysEvent {

    /**
     * 主机名
     */
    private String hostName;

    /**
     * 操作系统
     */
    private String osName;

    /**
     * 系统架构
     */
    private String osArch;

    /**
     * 网卡信息
     */
    private List<String> ifconfig;
    /**
     * 外网IP
     */
    private String publicAddress;
    /**
     * 时间戳
     */
    private long timestamp = System.currentTimeMillis();
}
