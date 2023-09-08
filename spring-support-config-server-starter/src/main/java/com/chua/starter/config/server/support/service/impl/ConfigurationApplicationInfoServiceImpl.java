package com.chua.starter.config.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.entity.ConfigurationApplicationInfo;
import com.chua.starter.config.server.support.entity.ConfigurationSubscribeInfo;
import com.chua.starter.config.server.support.mapper.ConfigurationApplicationInfoMapper;
import com.chua.starter.config.server.support.protocol.ProtocolServer;
import com.chua.starter.config.server.support.query.DetailUpdate;
import com.chua.starter.config.server.support.service.ConfigurationApplicationInfoService;
import com.chua.starter.config.server.support.service.ConfigurationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.chua.starter.config.constant.ConfigConstant.APP;

/**
 *    
 * @author CH
 */     
@Service(APP)
public class ConfigurationApplicationInfoServiceImpl
        extends ServiceImpl<ConfigurationApplicationInfoMapper, ConfigurationApplicationInfo>
        implements ConfigurationApplicationInfoService, ConfigurationService<ConfigurationApplicationInfo> {

    @Override
    public void register(String applicationName, String applicationProfile, Map<String, Object> data) {
        ConfigurationApplicationInfo configurationApplicationInfo = new ConfigurationApplicationInfo();
        configurationApplicationInfo.setAppHost(MapUtils.getString(data, ConfigConstant.APPLICATION_HOST));
        configurationApplicationInfo.setAppPort(MapUtils.getInteger(data, ConfigConstant.APPLICATION_PORT));
        configurationApplicationInfo.setAppProfile(applicationProfile);
        configurationApplicationInfo.setAppName(applicationName);
        configurationApplicationInfo.setAppContextPath(MapUtils.getString(data, ConfigConstant.CONTEXT_PATH));
        configurationApplicationInfo.setAppSpringPort(MapUtils.getInteger(data, ConfigConstant.SPRING_PORT));
        configurationApplicationInfo.setAppActuator(MapUtils.getString(data, ConfigConstant.ACTUATOR));

        saveOrUpdate(configurationApplicationInfo, Wrappers.<ConfigurationApplicationInfo>lambdaUpdate()
                .eq(ConfigurationApplicationInfo::getAppName, applicationName)
                .eq(ConfigurationApplicationInfo::getAppProfile, applicationProfile)
        );
    }

    @Override
    public String getSubscribe(String subscribe, String profile) {
        return "{}";
    }

    @Override
    public void unregister(String applicationName, String applicationProfile) {
        baseMapper.delete(Wrappers.<ConfigurationApplicationInfo>lambdaUpdate()
                .eq(ConfigurationApplicationInfo::getAppName, applicationName)
                .eq(ConfigurationApplicationInfo::getAppProfile, applicationName)
        );
    }

    @Override
    public Page<?> findAll(String profile, Pageable pageable) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ConfigurationApplicationInfo> selectPage = baseMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<ConfigurationApplicationInfo>(pageable.getPageNumber(), pageable.getPageSize()
                ), Wrappers.<ConfigurationApplicationInfo>lambdaQuery().eq(ConfigurationApplicationInfo::getAppProfile, profile));

        return  new PageImpl<>(selectPage.getRecords(), pageable, selectPage.getTotal());
    }

    @Override
    public Set<String> profile() {
        List<ConfigurationApplicationInfo> configurationApplicationInfos = baseMapper.selectList(Wrappers.<ConfigurationApplicationInfo>query()
                .select("app_profile").groupBy("app_profile")
        );
        return configurationApplicationInfos.stream().map(ConfigurationApplicationInfo::getAppProfile).collect(Collectors.toSet());
    }

    @Override
    public Set<String> applications() {
        List<ConfigurationApplicationInfo> configurationApplicationInfos = baseMapper.selectList(Wrappers.<ConfigurationApplicationInfo>query()
                .select("app_name").groupBy("app_name")
        );
        return configurationApplicationInfos.stream().map(ConfigurationApplicationInfo::getAppName).collect(Collectors.toSet());
    }

    @Override
    public Object getDetail(String configId) {
        return baseMapper.selectById(configId);
    }

    @Override
    public Object detailUpdate(DetailUpdate detailUpdate) {
        return null;
    }

    @Override
    public void saveData(ConfigurationApplicationInfo data) {
        if (null != data.getAppId()) {
            baseMapper.updateById(data);
            return;
        }
        baseMapper.insert(data);
    }

    @Override
    public void deleteById(Serializable dataId) {
        baseMapper.deleteById(dataId);
    }

    @Override
    public void notifyConfig(ProtocolServer protocolServer, ConfigurationSubscribeInfo subscribeInfo, Object configValue) {

    }
}
