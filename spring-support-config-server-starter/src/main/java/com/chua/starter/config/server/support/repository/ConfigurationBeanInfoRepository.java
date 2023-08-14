package com.chua.starter.config.server.support.repository;

import com.chua.starter.config.server.support.base.ConfigurationRepository;
import com.chua.starter.config.server.support.protocol.ProtocolServer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;

/**
 * TConfigurationCenterInfo
 *
 * @author CH
 * @since 2022/8/1 13:06
 */
@SuppressWarnings("ALL")
@Repository("bean")
public interface ConfigurationBeanInfoRepository extends ConfigurationRepository<ConfigurationBeanInfo> {


    @Override
    default void analysis(String applicationName, Map<String, Object> data) {
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
    default void notifyConfig(ProtocolServer protocolServer, Integer configId, String configValue, Integer disable, Object o) {

    }
}
