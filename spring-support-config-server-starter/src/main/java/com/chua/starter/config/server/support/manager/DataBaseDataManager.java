package com.chua.starter.config.server.support.manager;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.base.ConfigurationRepository;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 数据管理器
 *
 * @author CH
 */
@Slf4j
@Spi("database")
@SpiDefault
@SuppressWarnings("ALL")
public class DataBaseDataManager implements DataManager {

    @Override
    public void register(String applicationName, String dataType, Map<String, Object> data) {
        Boolean refresh = MapUtils.getBoolean(data, ConfigConstant.REFRESH, false);
        if(refresh) {
            unregister(applicationName, dataType);
        }

        ConfigurationRepository crudRepository = ServiceProvider.of(ConfigurationRepository.class).getExtension(dataType);
        if(null == crudRepository) {
            return;
        }
       crudRepository.analysis(applicationName, data);
    }

    @Override
    public String getSubscribe(String subscribe, String dataType, String profile) {
        if(Strings.isNullOrEmpty(subscribe)) {
            return "{}";
        }
        ConfigurationRepository crudRepository = ServiceProvider.of(ConfigurationRepository.class).getExtension(dataType);
        if(null == crudRepository) {
            return "{}";
        }
        return crudRepository.getSubscribe(subscribe, profile);
    }

    @Override
    public void unregister(String applicationName, String dataType) {
        ConfigurationRepository crudRepository = ServiceProvider.of(ConfigurationRepository.class).getExtension(dataType);
        if(null == crudRepository) {
            return;
        }
        crudRepository.unregister(applicationName);
    }

    @Override
    public void afterPropertiesSet() {

    }
}
