package com.chua.starter.config.server.support.service;

import com.chua.starter.config.server.support.entity.ConfigurationSubscribeInfo;
import com.chua.starter.config.server.support.protocol.ProtocolServer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * 配置服务
 *
 * @author CH
 */
public interface ConfigurationService<T> {

    /**
     * 登记
     * 数据解析
     *
     * @param applicationName    应用
     * @param data               数据
     * @param applicationProfile 应用程序配置文件
     */
    void register(String applicationName, String applicationProfile, Map<String, Object> data);

    /**
     * 获取订阅
     *
     * @param subscribe 订阅
     * @param profile   环境
     * @return 结果
     */
    String getSubscribe(String subscribe, String profile);

    /**
     * 注销
     * 注销数据
     *
     * @param applicationName    应用
     * @param applicationProfile 应用程序配置文件
     */
    void unregister(String applicationName, String applicationProfile);

    /**
     * 查询数据
     *
     * @param profile  环境
     * @param pageable 分页
     * @return 结果
     */
    Page<?> findAll(String profile, Pageable pageable);

    /**
     * 获取环境
     *
     * @return 环境
     */
    Set<String> profile();

    /**
     * 获取应用
     *
     * @return 环境
     */
    Set<String> applications();

    /**
     * 详情
     *
     * @param configId ID
     * @return 详情
     */
    Object getDetail(String configId);

    /**
     * 修改脚本
     *
     * @param detailUpdate 请求
     * @return 结果
     */
    Object detailUpdate(T detailUpdate);

    /**
     * 保存
     *
     * @param data 数据
     */
    void saveData(T data);

    /**
     * 删除
     * @param dataId ID
     */
    void deleteById(Serializable dataId);

    /**
     * 通知配置
     *
     * @param protocolServer 协议服务器
     * @param subscribeInfo  订阅信息
     * @param configValue    config值
     */
    void notifyConfig(ProtocolServer protocolServer, ConfigurationSubscribeInfo subscribeInfo, Object configValue);
}
