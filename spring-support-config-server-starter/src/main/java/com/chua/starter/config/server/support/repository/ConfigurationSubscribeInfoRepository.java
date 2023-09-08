package com.chua.starter.config.server.support.repository;

import com.chua.common.support.bean.BeanMap;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.base.ConfigurationRepository;
import com.chua.starter.config.server.support.config.NotifyConfig;
import com.chua.starter.config.server.support.protocol.ProtocolServer;
import com.chua.starter.config.server.support.query.DetailUpdate;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * TConfigurationCenterInfo
 *
 * @author CH
 * @since 2022/8/1 13:06
 */
@SuppressWarnings("ALL")
@Deprecated
@Repository("subscribe")
public interface ConfigurationSubscribeInfoRepository extends ConfigurationRepository<ConfigurationSubscribeInfo> {

    @Override
    default void analysis(String applicationName, Map<String, Object> data) {
        updateOrSave(data, applicationName);
    }

    /**
     * 保存或者更新数据
     *
     * @param data       数据
     * @param name       key
     * @param applicationName 标识
     * @param refresh
     */
    default void updateOrSave(Map<String, Object> data, String applicationName) {
        String host = MapUtils.getString(data, ConfigConstant.APPLICATION_HOST);
        String port = MapUtils.getString(data, ConfigConstant.APPLICATION_PORT);
        String dataType = MapUtils.getString(data, ConfigConstant.APPLICATION_DATA_TYPE);
        String profile = MapUtils.getString(data, ConfigConstant.PROFILE, "dev");

        ConfigurationSubscribeInfo query = new ConfigurationSubscribeInfo();
        query.setSubscribeApplicationName(applicationName);
        query.setSubscribeProfile(profile);
        query.setSubscribeType(dataType);

        ConfigurationSubscribeInfo tConfigurationCenterInfo1 = null;
        try {
            tConfigurationCenterInfo1 = findOne(Example.of(query)).get();
        } catch (Exception e) {
        }
        if (null == tConfigurationCenterInfo1) {
            tConfigurationCenterInfo1 = query;
        }
        tConfigurationCenterInfo1.setSubscribeHost(host);
        tConfigurationCenterInfo1.setSubscribePort(port);
        save(tConfigurationCenterInfo1);
    }
    @Override
    default String getSubscribe(String subscribe, String profile) {
        return null;
    }

    @Override
    default void unregister(String applicationName) {

    }

    @Override
    default Page<?> findAll(String profile, Pageable pageable) {
        return null;
    }

    @Override
    default Set<String> profile() {
        return null;
    }

    @Override
    default Set<String> applications() {
        return null;
    }

    @Override
    default void notifyConfig(ProtocolServer protocolServer, ConfigurationSubscribeInfo subscribeInfo, Object configValue) {
        List<ConfigurationSubscribeInfo> configurationSubscribeInfos = findAll(Example.of(subscribeInfo));
        Executor threadPool = ThreadUtils.getDefaultThreadPool();
        threadPool.execute(() -> {
            try {
                notifyConfig(configurationSubscribeInfos, protocolServer, configValue);
            } catch (Throwable e) {
            }
        });
    }
    default void notifyConfig(List<ConfigurationSubscribeInfo> configurationSubscribeInfos, ProtocolServer protocolServer, Object configValue) {
        List<NotifyConfig> rs = new LinkedList<>();
        for (ConfigurationSubscribeInfo configurationSubscribeInfo : configurationSubscribeInfos) {
            NotifyConfig notifyConfig = createNotifyConfig(configurationSubscribeInfo, protocolServer, configValue);
            if(null == notifyConfig) {
                continue;
            }
            rs.add(notifyConfig);
        }

        protocolServer.notifyClient(rs);
    }
    default NotifyConfig createNotifyConfig(ConfigurationSubscribeInfo configurationSubscribeInfo, ProtocolServer protocolServer, Object configValue) {
        try {
            NotifyConfig item = new NotifyConfig();
            item.setConfigValue(Json.toJson(BeanMap.create(configValue)));
            item.setConfigItem(configurationSubscribeInfo.getSubscribeApplicationName());
            item.setBinderPort(configurationSubscribeInfo.getSubscribePort());
            item.setBinderIp(configurationSubscribeInfo.getSubscribeHost());
            item.setDataType(configurationSubscribeInfo.getSubscribeType());

            if (item.isEmpty()) {
                return null;
            }
            return item;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    default Object getDetail(String configId) {
        return getById(Integer.valueOf(configId));
    }

    @Override
    default Object detailUpdate(DetailUpdate detailUpdate) {
        return null;
    }
}
