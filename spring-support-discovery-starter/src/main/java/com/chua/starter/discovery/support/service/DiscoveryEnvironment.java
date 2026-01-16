package com.chua.starter.discovery.support.service;

import java.util.Properties;

/**
 * 发现环境接口
 * 用于定义服务发现所需的环境配置信息
 *
 * @author CH
 */
public interface DiscoveryEnvironment {

    /**
     * 获取环境名称
     *
     * @return 环境名称
     */
    String getName();

    /**
     * 获取环境属性配置
     *
     * @return 属性配置对象
     */
    Properties getProperties();
}
