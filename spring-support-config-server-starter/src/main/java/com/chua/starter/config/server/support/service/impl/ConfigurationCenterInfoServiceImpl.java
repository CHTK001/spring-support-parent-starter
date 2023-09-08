package com.chua.starter.config.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.config.NotifyConfig;
import com.chua.starter.config.server.support.entity.ConfigurationCenterInfo;
import com.chua.starter.config.server.support.mapper.ConfigurationCenterInfoMapper;
import com.chua.starter.config.server.support.protocol.ProtocolServer;
import com.chua.starter.config.server.support.query.DetailUpdate;
import com.chua.starter.config.server.support.repository.ConfigurationSubscribeInfo;
import com.chua.starter.config.server.support.service.ConfigurationCenterInfoService;
import com.chua.starter.config.server.support.service.ConfigurationService;
import com.google.common.base.Strings;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static com.chua.starter.config.constant.ConfigConstant.*;

/**
 * CONFIG
 * @author CH
 */     
@Service(CONFIG)
@SuppressWarnings("ALL")
public class ConfigurationCenterInfoServiceImpl extends ServiceImpl<ConfigurationCenterInfoMapper, ConfigurationCenterInfo>
        implements ConfigurationCenterInfoService, ConfigurationService<ConfigurationCenterInfo> {

    @Override
    public void register(String applicationName, String applicationProfile, Map<String, Object> data) {
        boolean exists = baseMapper.exists(Wrappers.<ConfigurationCenterInfo>lambdaQuery()
                .eq(ConfigurationCenterInfo::getConfigApplicationName, applicationName)
                .eq(ConfigurationCenterInfo::getConfigProfile, applicationProfile)
        );

        List<ConfigurationCenterInfo> configurationCenterInfos = new LinkedList<>();
        if (!exists) {
            intoDatabase(data, applicationName, applicationProfile, configurationCenterInfos);
        }

    }


    public static boolean isBaseData(String name) {
        return APPLICATION_HOST.equals(name) ||
                APPLICATION_PORT.equals(name) ||
                APPLICATION_DATA.equals(name) ||
                APPLICATION_NAME.equals(name) ||
                APPLICATION_DATA_TYPE.equals(name) ||
                REFRESH.equals(name) ||
                PROFILE.equals(name) ||
                CONTEXT_PATH.equals(name) ||
                SPRING_PORT.equals(name) ||
                ACTUATOR.equals(name) ||
                APPLICATION_SUBSCRIBE.equals(name);
    }
    /**
     * 保存到數據庫
     *
     * @param data                     數據
     * @param applicationName          应用名称
     * @param configurationCenterInfos 数据
     * @param applicationProfile       应用程序配置文件
     */
    void intoDatabase(Map<String, Object> data, String applicationName, String applicationProfile, List<ConfigurationCenterInfo> configurationCenterInfos) {
        String profile = applicationProfile;
        Map transfer = (Map) MapUtils.getObject(data, "transfer");
        data.remove("transfer");
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (ConfigConstant.KEY.equalsIgnoreCase(key)) {
                continue;
            }

            if(isBaseData(key)) {
                continue;
            }

            Object value = entry.getValue();
            if (value instanceof Map) {
                render(transfer, (Map) value, null, profile, configurationCenterInfos, applicationName);
                continue;
            }

            ConfigurationCenterInfo item = new ConfigurationCenterInfo();
            item.setConfigApplicationName(applicationName);
            item.setConfigName(key);
            item.setConfigDesc(MapUtils.getString(transfer, key));
            item.setConfigProfile(profile);
            item.setConfigMappingName(key);
            item.setConfigValue(null == value ? "" : value.toString());
            item.setDisable(0);

            configurationCenterInfos.add(item);
        }

        this.saveBatch(configurationCenterInfos);
    }

    /**
     * 渲染对象
     *
     * @param tranfer                      翻译
     * @param map                          数据
     * @param mapping                      映射
     * @param profile
     * @param tConfigurationCenterInfoList 结果
     * @param binderName                   标识
     */
    void render(Map tranfer, Map map, String mapping, String profile, List<ConfigurationCenterInfo> tConfigurationCenterInfoList, String binderName) {
        map.forEach((key, value) -> {
            if (value instanceof Map) {
                render(tranfer, (Map) value, key.toString(), profile, tConfigurationCenterInfoList, binderName);
            }
            ConfigurationCenterInfo item = new ConfigurationCenterInfo();
            item.setConfigApplicationName(binderName);
            item.setConfigName(key.toString());
            item.setConfigDesc(MapUtils.getString(tranfer, key));
            item.setConfigMappingName(Optional.ofNullable(mapping).ofNullable(key.toString()).toString());
            item.setConfigValue(null == value ? "" : value.toString());
            item.setDisable(0);
            item.setConfigProfile(profile);


            tConfigurationCenterInfoList.add(item);
        });
    }


    @Override
    public String getSubscribe(String subscribe, String profile) {
        List<ConfigurationCenterInfo> list = baseMapper.selectList(Wrappers.<ConfigurationCenterInfo>lambdaQuery()
                .eq(ConfigurationCenterInfo::getConfigProfile, profile)
                .in(ConfigurationCenterInfo::getConfigApplicationName, Splitter.on(',').omitEmptyStrings().trimResults().splitToSet(subscribe))
        );
        Map<String, List<ConfigurationCenterInfo>> valueMapping = new HashMap<>();
        for (ConfigurationCenterInfo info : list) {
            valueMapping.computeIfAbsent(info.getConfigApplicationName(), it -> new ArrayList<>()).add(info);
        }
        Map<String, Map<String, Object>> rs = new HashMap<>();

        for (Map.Entry<String, List<ConfigurationCenterInfo>> entry : valueMapping.entrySet()) {
            String key = entry.getKey();
            List<ConfigurationCenterInfo> entryValue = entry.getValue();
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
    Map<String, Object> render(String key, List<ConfigurationCenterInfo> entryValue) {
        Map<String, Object> rs1 = new HashMap<>(1);
        Map<String, Object> rs = new HashMap<>(entryValue.size());

        rs1.put(key, rs);

        for (ConfigurationCenterInfo tConfigurationCenterInfo : entryValue) {
            String configCondition = tConfigurationCenterInfo.getConfigCondition();
            if (!Strings.isNullOrEmpty(configCondition) && isMatcher(configCondition)) {
                continue;
            }
            rs.put(tConfigurationCenterInfo.getConfigName(), tConfigurationCenterInfo.getConfigValue());
        }
        return rs1;
    }
    /**
     * 匹配信息
     *
     * @param configCondition 条件
     * @return 结果
     */
    boolean isMatcher(String configCondition) {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext standardEvaluationContext = new StandardEvaluationContext(SpringBeanUtils.getApplicationContext());
        standardEvaluationContext.addPropertyAccessor(new BeanFactoryAccessor());
        Expression expression = parser.parseExpression(configCondition, new TemplateParserContext());
        try {
            return expression.getValue(standardEvaluationContext, Boolean.class);
        } catch (EvaluationException e) {
            return false;
        }
    }

    @Override
    public void unregister(String applicationName, String applicationProfile) {
        baseMapper.delete(Wrappers.<ConfigurationCenterInfo>lambdaUpdate()
                .eq(ConfigurationCenterInfo::getConfigApplicationName, applicationName)
                .eq(ConfigurationCenterInfo::getConfigProfile, applicationName)
        );
    }

    @Override
    public Page<?> findAll(String profile, Pageable pageable) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ConfigurationCenterInfo> selectPage = baseMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageable.getPageNumber(), pageable.getPageSize()
                ), Wrappers.<ConfigurationCenterInfo>lambdaQuery().eq(StringUtils.isNotEmpty(profile), ConfigurationCenterInfo::getConfigProfile, profile));

        return  new PageImpl<>(selectPage.getRecords(), pageable, selectPage.getTotal());
    }

    @Override
    public Set<String> profile() {
        List<ConfigurationCenterInfo> configurationApplicationInfos = baseMapper.selectList(Wrappers.<ConfigurationCenterInfo>query()
                .select("config_profile").groupBy("config_profile")
        );
        return configurationApplicationInfos.stream().map(ConfigurationCenterInfo::getConfigProfile).collect(Collectors.toSet());
    }

    @Override
    public Set<String> applications() {
        List<ConfigurationCenterInfo> configurationApplicationInfos = baseMapper.selectList(Wrappers.<ConfigurationCenterInfo>query()
                .select("config_application_name").groupBy("config_application_name")
        );
        return configurationApplicationInfos.stream().map(ConfigurationCenterInfo::getConfigApplicationName).collect(Collectors.toSet());
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
    public void saveData(ConfigurationCenterInfo data) {
        baseMapper.insert(data);
    }

    @Override
    public void deleteById(Serializable dataId) {
        baseMapper.deleteById(dataId);
    }

    @Override
    public void notifyConfig(ProtocolServer protocolServer, ConfigurationSubscribeInfo subscribeInfo, Object configValue) {
        List<NotifyConfig> rs = new LinkedList<>();
        NotifyConfig config = new NotifyConfig();
//        config.setDataType()

        protocolServer.notifyClient(rs);
    }
}
