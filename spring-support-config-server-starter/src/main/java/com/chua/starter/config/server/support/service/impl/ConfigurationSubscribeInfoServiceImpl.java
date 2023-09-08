package com.chua.starter.config.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.config.server.support.entity.ConfigurationSubscribeInfo;
import com.chua.starter.config.server.support.mapper.ConfigurationSubscribeInfoMapper;
import com.chua.starter.config.server.support.protocol.ProtocolServer;
import com.chua.starter.config.server.support.query.DetailUpdate;
import com.chua.starter.config.server.support.service.ConfigurationService;
import com.chua.starter.config.server.support.service.ConfigurationSubscribeInfoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import static com.chua.starter.config.constant.ConfigConstant.SUBSCRIBE;

/**
 *    
 * @author CH
 */     
@Service(SUBSCRIBE)
public class ConfigurationSubscribeInfoServiceImpl extends ServiceImpl<ConfigurationSubscribeInfoMapper, ConfigurationSubscribeInfo>
        implements ConfigurationSubscribeInfoService, ConfigurationService<ConfigurationSubscribeInfo> {

    @Override
    @Transactional
    public void register(String applicationName, String applicationProfile, Map<String, Object> data) {
        ConfigurationSubscribeInfo configurationSubscribeInfo = new ConfigurationSubscribeInfo();
        configurationSubscribeInfo.setSubscribeProfile(applicationProfile);
        configurationSubscribeInfo.setSubscribeApplicationName(applicationName);
        configurationSubscribeInfo.setSubscribeType(MapUtils.getString(data, "dataType"));
        baseMapper.delete(Wrappers.<ConfigurationSubscribeInfo>lambdaUpdate()
                .eq(ConfigurationSubscribeInfo::getSubscribeApplicationName, applicationName)
                .eq(ConfigurationSubscribeInfo::getSubscribeProfile, applicationProfile)
                .eq(ConfigurationSubscribeInfo::getSubscribeType, configurationSubscribeInfo.getSubscribeType())
        );

        baseMapper.insert(configurationSubscribeInfo);
    }

    @Override
    public String getSubscribe(String subscribe, String profile) {
        return null;
    }

    @Override
    public void unregister(String applicationName, String applicationProfile) {

    }

    @Override
    public Page<?> findAll(String profile, Pageable pageable) {
        return null;
    }

    @Override
    public Set<String> profile() {
        return null;
    }

    @Override
    public Set<String> applications() {
        return null;
    }

    @Override
    public Object getDetail(String configId) {
        return null;
    }

    @Override
    public Object detailUpdate(DetailUpdate detailUpdate) {
        return null;
    }

    @Override
    public void saveData(ConfigurationSubscribeInfo data) {

    }

    @Override
    public void deleteById(Serializable dataId) {

    }

    @Override
    public void notifyConfig(ProtocolServer protocolServer, com.chua.starter.config.server.support.repository.ConfigurationSubscribeInfo subscribeInfo, Object configValue) {

    }
}
