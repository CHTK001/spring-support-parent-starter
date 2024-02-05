package com.chua.starter.monitor.service;

import lombok.Data;

/**
 * 服务实例
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/05
 */
@Data
public class ServiceInstance {

    private String profile;

    private String name;

    private String host;

    private int port;
}
