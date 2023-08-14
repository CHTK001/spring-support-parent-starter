package com.chua.starter.config.server.support.manager;

import com.chua.common.support.function.InitializingAware;

import java.util.Map;

/**
 * 数据管理器
 *
 * @author CH
 */
public interface DataManager extends InitializingAware {
    /**
     * 注册数据
     *
     * @param applicationName 数据应用
     * @param dataType        数据类型
     * @param stringObjectMap 数据
     */
    void register(String applicationName, String dataType, Map<String, Object> stringObjectMap);

    /**
     * 获取订阅数据
     *
     * @param subscribe 订阅的名称
     * @param dataType 数据类型
     * @param profile   环境
     * @return 数据
     */
    String getSubscribe(String subscribe, String dataType, String profile);

    /**
     * 注销
     *
     * @param applicationName 应用
     * @param dataType        数据类型
     */
    void unregister(String applicationName, String dataType);
}
