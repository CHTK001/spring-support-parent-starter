package com.chua.starter.config.server.support.manager;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.protocol.ProtocolServer;
import com.chua.starter.config.server.support.query.DetailUpdate;
import com.chua.starter.config.server.support.repository.ConfigurationSubscribeInfo;
import com.chua.starter.config.server.support.service.ConfigurationService;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.chua.starter.config.constant.ConfigConstant.APP;
import static com.chua.starter.config.constant.ConfigConstant.SUBSCRIBE;

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

    @Resource
    private ApplicationContext applicationContext;

    @Override
    public void register(String applicationName, String dataType, String applicationProfile, Map<String, Object> data) {
        Boolean refresh = MapUtils.getBoolean(data, ConfigConstant.REFRESH, false);
        if (refresh) {
            unregister(applicationName, dataType, applicationProfile);
        }

        ConfigurationService crudRepository = applicationContext.getBean(dataType, ConfigurationService.class);
        ConfigurationService configurationService = applicationContext.getBean(APP, ConfigurationService.class);
        try {
            configurationService.register(applicationName, applicationProfile, data);
        } catch (Exception e) {
        }
        try {
            crudRepository.register(applicationName, applicationProfile, data);
        } catch (Exception e) {
        }
    }

    @Override
    public String getSubscribe(String subscribe, String dataType, String applicationName, String profile, Map<String, Object> data) {
        if (Strings.isNullOrEmpty(subscribe)) {
            return "{}";
        }
        ConfigurationService crudRepository = null;
        try {
            crudRepository = applicationContext.getBean(dataType, ConfigurationService.class);
        } catch (BeansException e) {
            return "{}";
        }
        ConfigurationService configurationService = applicationContext.getBean(SUBSCRIBE, ConfigurationService.class);
        data.put("dataType", dataType);
        configurationService.register(applicationName, profile, data);
        return crudRepository.getSubscribe(subscribe, profile);
    }

    @Override
    public void unregister(String applicationName, String dataType, String applicationProfile) {
        ConfigurationService crudRepository = applicationContext.getBean(dataType, ConfigurationService.class);
        if (null == crudRepository) {
            return;
        }
        crudRepository.unregister(applicationName, applicationProfile);
    }

    @Override
    public Page<?> findAll(String dataType, int page, Integer pageSize, String profile) {
        ConfigurationService crudRepository = applicationContext.getBean(dataType, ConfigurationService.class);
        Pageable pageable = Pageable.ofSize(pageSize).withPage(page);
        if (null == crudRepository) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }
        return crudRepository.findAll(profile, pageable);
    }

    @Override
    public void save(String dataType, Object data) {
        ConfigurationService crudRepository = applicationContext.getBean(dataType, ConfigurationService.class);
        if (null == crudRepository) {
            return;
        }
        crudRepository.saveData(data);
    }

    @Override
    public void delete(String dataType, Serializable dataId) {
        ConfigurationService crudRepository = applicationContext.getBean(dataType, ConfigurationService.class);
        if (null == crudRepository) {
            return;
        }
        crudRepository.deleteById(dataId);
    }

    @Override
    public Set<String> profile(String dataType) {
        ConfigurationService crudRepository = applicationContext.getBean(dataType, ConfigurationService.class);
        if (null == crudRepository) {
            return Collections.emptySet();
        }
        return crudRepository.profile();
    }

    @Override
    public Set<String> applications(String dataType) {
        ConfigurationService crudRepository = applicationContext.getBean(dataType, ConfigurationService.class);
        if (null == crudRepository) {
            return Collections.emptySet();
        }
        return crudRepository.applications();
    }

    @Override
    public void notifyConfig(String dataType, ConfigurationSubscribeInfo subscribeInfo, Object configValue) {
        ConfigurationService crudRepository = applicationContext.getBean(dataType, ConfigurationService.class);
        if (null == crudRepository) {
            return;
        }
        crudRepository.notifyConfig(protocolServer, subscribeInfo, configValue);
    }


    @Override
    public void setProtocol(ProtocolServer protocolServer) {
        this.protocolServer = protocolServer;
    }

    @Override
    public Object getDetail(String dataType, String configId) {
        ConfigurationService crudRepository = applicationContext.getBean(dataType, ConfigurationService.class);
        if (null == crudRepository) {
            return null;
        }
        return crudRepository.getDetail(configId);
    }

    @Override
    public Object detailUpdate(String dataType, DetailUpdate detailUpdate) {
        ConfigurationService crudRepository = applicationContext.getBean(dataType, ConfigurationService.class);
        if (null == crudRepository) {
            return null;
        }
        return crudRepository.detailUpdate(detailUpdate);
    }


    @Override
    public void afterPropertiesSet() {

    }
}
