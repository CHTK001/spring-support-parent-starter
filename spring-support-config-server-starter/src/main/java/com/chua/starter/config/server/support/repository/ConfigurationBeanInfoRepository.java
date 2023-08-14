package com.chua.starter.config.server.support.repository;

import com.chua.starter.config.server.support.base.ConfigurationRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;

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
}
