package com.chua.starter.monitor.server.pojo;

import lombok.Data;

/**
 * 服务目标
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/05
 */
@Data
public class ServiceTarget {


    private String name;
    private String sourceName = "HOST";
    private String sourceHost;

    private int sourcePort;
    private String targetHost;

    private int targetPort;
}
