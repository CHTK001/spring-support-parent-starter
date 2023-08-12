package com.chua.starter.config.server.protocol;

import com.chua.starter.config.server.entity.NotifyConfig;
import com.chua.starter.config.server.pojo.ConfigurationCenterInfo;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 协议数据解释服务
 *
 * @author CH
 * @since 2022/8/1 8:58
 */
public interface ProtocolResolver {
    /**
     * 通知
     *
     * @param notifyConfig   通知
     * @param protocolServer server
     */
    void notifyConfig(List<NotifyConfig> notifyConfig, ProtocolServer protocolServer);

    /**
     * 通知
     *
     * @param configurationCenterInfo 配置ID
     * @param protocolServer          server
     */
    void notifyConfig(ConfigurationCenterInfo configurationCenterInfo, ProtocolServer protocolServer);

    /**
     * 通知
     *
     * @param configId       配置ID
     * @param configValue    配置值
     * @param disable        是否可用
     * @param protocolServer server
     */
    void notifyConfig(Integer configId, String configValue, Integer disable, ProtocolServer protocolServer);

    /**
     * 分页数据
     *
     * @param page     页码
     * @param pageSize 每页数量
     * @param profile
     * @return 结果
     */
    Page<ConfigurationCenterInfo> findAll(Integer page, Integer pageSize, String profile);

    /**
     * 权限分类
     *
     * @param configId   配置ID
     * @param configItem 配置分类
     */
    void distributeUpdate(List<Integer> configId, String configItem);
}
