package com.chua.starter.config.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.bean.BeanMap;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.entity.ConfigurationBeanInfo;
import com.chua.starter.config.server.support.entity.ConfigurationSubscribeInfo;
import com.chua.starter.config.server.support.mapper.ConfigurationBeanInfoMapper;
import com.chua.starter.config.server.support.protocol.ProtocolServer;
import com.chua.starter.config.server.support.service.ConfigurationBeanInfoService;
import com.chua.starter.config.server.support.service.ConfigurationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static com.chua.starter.config.constant.ConfigConstant.BEAN;

/**
 *    
 * @author CH
 */     
@Service(BEAN)
public class ConfigurationBeanInfoServiceImpl extends ServiceImpl<ConfigurationBeanInfoMapper, ConfigurationBeanInfo>
        implements ConfigurationBeanInfoService, ConfigurationService<ConfigurationBeanInfo> {

    @Override
    public void register(String applicationName, String applicationProfile, Map<String, Object> data) {

    }


    @Override
    public String getSubscribe(String subscribe, String profile) {
        List<ConfigurationBeanInfo> list = baseMapper.selectList(Wrappers.<ConfigurationBeanInfo>lambdaQuery()
                .eq(ConfigurationBeanInfo::getBeanProfile, profile)
                .eq(ConfigurationBeanInfo::getDisable, 0)
                .in(ConfigurationBeanInfo::getBeanApplicationName, Splitter.on(',').omitEmptyStrings().trimResults().splitToSet(subscribe))
        );
        Map<String, List<ConfigurationBeanInfo>> valueMapping = new HashMap<>();
        for (ConfigurationBeanInfo info : list) {
            valueMapping.computeIfAbsent(info.getBeanApplicationName(), it -> new ArrayList<>()).add(info);
        }
        Map<String, Map<String, Object>> rs = new HashMap<>();

        for (Map.Entry<String, List<ConfigurationBeanInfo>> entry : valueMapping.entrySet()) {
            String key = entry.getKey();
            List<ConfigurationBeanInfo> entryValue = entry.getValue();
            rs.put(key + ".data", render(key, entryValue));
        }

        return Json.toJson(rs);
    }

    /**
     * 渲染数据
     *
     * @param key        标识
     * @param entryValue 权限
     * @return 数据
     */
    Map<String, Object> render(String key, List<ConfigurationBeanInfo> entryValue) {
        Map<String, Object> rs1 = new HashMap<>(1);

        List<Map<String, Object>> rs = new LinkedList<>();
        rs1.put(key, rs);

        for (ConfigurationBeanInfo tConfigurationCenterInfo : entryValue) {
            Map<String, Object> item = new HashMap<>(entryValue.size());
            try {
                String beanFilePath = tConfigurationCenterInfo.getBeanFilePath();
                try {
                    item.put(ConfigConstant.FILE_TYPE_CONTENT, IoUtils.toByteArray(new FileInputStream(beanFilePath)));
                } catch (IOException ignored) {
                }
                item.put(ConfigConstant.FILE_TYPE, FileUtils.getExtension(beanFilePath));
                item.putAll(BeanMap.create(tConfigurationCenterInfo));

                rs.add(item);
            } catch (Exception ignored) {
            }
        }
        return rs1;
    }

    @Override
    public void unregister(String applicationName, String applicationProfile) {
        baseMapper.delete(Wrappers.<ConfigurationBeanInfo>lambdaUpdate()
                .eq(ConfigurationBeanInfo::getBeanApplicationName, applicationName)
                .eq(ConfigurationBeanInfo::getBeanProfile, applicationName)
        );
    }

    @Override
    public Page<?> findAll(String profile, Pageable pageable) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ConfigurationBeanInfo> selectPage = baseMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageable.getPageNumber(), pageable.getPageSize()
                ), Wrappers.<ConfigurationBeanInfo>lambdaQuery().eq(StringUtils.isNotEmpty(profile), ConfigurationBeanInfo::getBeanProfile, profile));

        return  new PageImpl<>(selectPage.getRecords(), pageable, selectPage.getTotal());
    }

    @Override
    public Set<String> profile() {
        List<ConfigurationBeanInfo> configurationApplicationInfos = baseMapper.selectList(Wrappers.<ConfigurationBeanInfo>query()
                .select("bean_profile").groupBy("bean_profile")
        );
        return configurationApplicationInfos.stream().map(ConfigurationBeanInfo::getBeanProfile).collect(Collectors.toSet());
    }

    @Override
    public Set<String> applications() {
        List<ConfigurationBeanInfo> configurationApplicationInfos = baseMapper.selectList(Wrappers.<ConfigurationBeanInfo>query()
                .select("bean_application_name").groupBy("bean_application_name")
        );
        return configurationApplicationInfos.stream().map(ConfigurationBeanInfo::getBeanApplicationName).collect(Collectors.toSet());
    }


    @Override
    public Object getDetail(String configId) {
        ConfigurationBeanInfo configurationBeanInfo = getById(Integer.valueOf(configId));
        if(null == configurationBeanInfo) {
            return null;
        }

        try {
            return IoUtils.toString(new File(configurationBeanInfo.getBeanFilePath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object detailUpdate(ConfigurationBeanInfo detailUpdate) {
        return null;
    }

    @Override
    public void saveData(ConfigurationBeanInfo data) {
        if (null != data.getBeanId()) {
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
