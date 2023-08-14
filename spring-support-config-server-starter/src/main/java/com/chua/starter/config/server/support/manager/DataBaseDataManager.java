package com.chua.starter.config.server.support.manager;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.base.ConfigurationRepository;
import com.chua.starter.config.server.support.protocol.ProtocolServer;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

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

    private ProtocolServer protocolServer;

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
    public Page<?> findAll(String dataType, int page, Integer pageSize, String profile) {
        ConfigurationRepository crudRepository = ServiceProvider.of(ConfigurationRepository.class).getExtension(dataType);
        Pageable pageable = Pageable.ofSize(pageSize).withPage(page);
        if(null == crudRepository) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }
        return crudRepository.findAll(profile, pageable);
    }

    @Override
    public void save(String dataType, Object data) {
        ConfigurationRepository crudRepository = ServiceProvider.of(ConfigurationRepository.class).getExtension(dataType);
        if(null == crudRepository) {
            return;
        }
        crudRepository.save(data);
    }

    @Override
    public void delete(String dataType, Serializable dataId) {
        ConfigurationRepository crudRepository = ServiceProvider.of(ConfigurationRepository.class).getExtension(dataType);
        if(null == crudRepository) {
            return;
        }
        crudRepository.deleteById(dataId);
    }

    @Override
    public Set<String> profile(String dataType) {
        ConfigurationRepository crudRepository = ServiceProvider.of(ConfigurationRepository.class).getExtension(dataType);
        if(null == crudRepository) {
            return Collections.emptySet();
        }
        return crudRepository.profile();
    }

    @Override
    public Set<String> applications(String dataType) {
        ConfigurationRepository crudRepository = ServiceProvider.of(ConfigurationRepository.class).getExtension(dataType);
        if(null == crudRepository) {
            return Collections.emptySet();
        }
        return crudRepository.applications();
    }

    @Override
    public void notifyConfig(String dataType, Integer configId, String configValue, Integer disable, Object o) {
        ConfigurationRepository crudRepository = ServiceProvider.of(ConfigurationRepository.class).getExtension(dataType);
        if(null == crudRepository) {
            return;
        }
        crudRepository.notifyConfig(protocolServer, configId, configValue, disable, o);
    }

    @Override
    public void setProtocol(ProtocolServer protocolServer) {
        this.protocolServer = protocolServer;
    }


    @Override
    public void afterPropertiesSet() {

    }
}
