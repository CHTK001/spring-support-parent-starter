package com.chua.starter.config.server.support.manager;

import com.chua.common.support.function.InitializingAware;
import com.chua.starter.config.server.support.protocol.ProtocolServer;
import org.springframework.data.domain.Page;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

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
     * @param dataType  数据类型
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

    /**
     * 查询数据
     *
     * @param dataType 数据类型
     * @param page     页码
     * @param pageSize 每页数量
     * @param profile  配置
     * @return 结果
     */
    Page<?> findAll(String dataType, int page, Integer pageSize, String profile);

    /**
     * 保存数据
     *
     * @param dataType 数据类型
     * @param data     数据
     */
    void save(String dataType, Object data);

    /**
     * 删除数据
     *
     * @param dataType 数据类型
     * @param dataId   数据ID
     */
    void delete(String dataType, Serializable dataId);

    /**
     * 获取环境
     *
     * @param dataType 数据类型
     * @return 环境
     */
    Set<String> profile(String dataType);

    /**
     * 获取应用
     *
     * @param dataType 数据类型
     * @return 环境
     */
    Set<String> applications(String dataType);

    /**
     * 通知
     *
     * @param dataType    数据类型
     * @param configId    ID
     * @param configValue 值
     * @param disable     是否禁用
     * @param o           o
     */
    void notifyConfig(String dataType, Integer configId, String configValue, Integer disable, Object o);

    /**
     * 设置协议
     * @param protocolServer 协议
     */
    void setProtocol(ProtocolServer protocolServer);
}
