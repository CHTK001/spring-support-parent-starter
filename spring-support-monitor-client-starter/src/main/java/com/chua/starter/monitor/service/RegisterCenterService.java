package com.chua.starter.monitor.service;

/**
 * 注册中心服务
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/05
 */
public interface RegisterCenterService {


    /**
     * 获取服务
     *
     * @param appName 应用程序名称
     * @return {@link ServiceInstance}
     */
    ServiceInstance getService(String appName);
}
