package com.chua.starter.config.server.manager;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.crypto.Encrypt;
import com.chua.common.support.json.Json;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.config.entity.KeyValue;
import com.chua.starter.config.server.entity.NotifyConfig;
import com.chua.starter.config.server.pojo.ConfigurationCenterInfoRepository;
import com.chua.starter.config.server.pojo.ConfigurationDistributeInfoRepository;
import com.chua.starter.config.server.pojo.TConfigurationCenterInfo;
import com.chua.starter.config.server.pojo.TConfigurationDistributeInfo;
import com.chua.starter.config.server.properties.ConfigServerProperties;
import com.chua.starter.config.server.protocol.ProtocolServer;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

import static com.chua.starter.core.support.constant.Constant.DEFAULT_SER;


/**
 * 文件管理
 *
 * @author CH
 * @since 2022/8/1 9:38
 */
@Slf4j
@Spi("database")
public class DatabaseConfigurationManager implements ConfigurationManager, ApplicationContextAware {


    private ConfigurationCenterInfoRepository configurationCenterInfoRepository;

    private ConfigurationDistributeInfoRepository configurationDistributeInfoRepository;

    @Resource
    private ConfigServerProperties configServerProperties;

    @Resource
    private ApplicationContext applicationContext;
    @PostConstruct
    public void initial() {
        DataSource dataSource = getDataSource();
        DataSourceRepositoryFactory factory = new DataSourceRepositoryFactory(dataSource);
        configurationCenterInfoRepository = factory.buildRepository(ConfigurationCenterInfoRepository.class, dataSource);
        configurationDistributeInfoRepository = factory.buildRepository(ConfigurationDistributeInfoRepository.class, dataSource);

    }

    protected DataSource getDataSource() {
        Map<String, DataSource> beansOfType = applicationContext.getBeansOfType(DataSource.class);
        return (DataSource) MapUtils.getObject(beansOfType, configServerProperties.getDatabase(), CollectionUtils.findFirst(beansOfType.values()));
    }


    @Override
    public void register(Map<String, Object> data, String binderName) {
        Boolean refresh = MapUtils.getBoolean(data, "binder-auto-refresh", false);
        TConfigurationCenterInfo tConfigurationCenterInfo = new TConfigurationCenterInfo();
        tConfigurationCenterInfo.setConfigItem(binderName);

        boolean exists = configurationCenterInfoRepository.exists(tConfigurationCenterInfo);
        log.info("检测到 {}:{}注入配置", MapUtils.getString(data, "binder-client"), MapUtils.getString(data, "binder-port"));
        if (refresh) {
            configurationCenterInfoRepository.delete(binderName);
        }

        if (!exists) {
            intoDatabase(data, binderName);
        }

        updateOrSave(data, "binder-port", binderName, true);
        updateOrSave(data, "binder-client", binderName, true);
    }

    /**
     * 保存或者更新数据
     *
     * @param data       数据
     * @param name       key
     * @param binderName 标识
     * @param refresh
     */
    private void updateOrSave(Map<String, Object> data, String name, String binderName, Boolean refresh) {
        String port = MapUtils.getString(data, name);

        TConfigurationCenterInfo query = new TConfigurationCenterInfo();
        query.setConfigName(name);
        query.setConfigItem(binderName);

        TConfigurationCenterInfo tConfigurationCenterInfo1 = configurationCenterInfoRepository
                .findOne(query);
        if (null != tConfigurationCenterInfo1 && refresh) {
            configurationCenterInfoRepository.update(name, port, binderName);
        } else {
            tConfigurationCenterInfo1 = new TConfigurationCenterInfo();
            tConfigurationCenterInfo1.setConfigName(name);
            tConfigurationCenterInfo1.setConfigItem(binderName);
            tConfigurationCenterInfo1.setConfigValue(port);
            configurationCenterInfoRepository.save(tConfigurationCenterInfo1);
        }
    }

    /**
     * 保存到數據庫
     *
     * @param data       數據
     * @param binderName 标识
     */
    private void intoDatabase(Map<String, Object> data, String binderName) {
        String profile = MapUtils.getString(data, "binder-profile", "dev");
        Map transfer = (Map) MapUtils.getObject(data, "transfer");
        data.remove("transfer");
        List<TConfigurationCenterInfo> tConfigurationCenterInfoList = new ArrayList<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if ("binder-key".equalsIgnoreCase(key)) {
                continue;
            }

            Object value = entry.getValue();
            if (value instanceof Map) {
                render(transfer, (Map) value, null, profile, tConfigurationCenterInfoList, binderName);
                continue;
            }

            TConfigurationCenterInfo item = new TConfigurationCenterInfo();
            item.setConfigItem(binderName);
            item.setConfigName(key);
            item.setConfigDesc(MapUtils.getString(transfer, key));
            item.setConfigProfile(profile);
            item.setConfigMappingName(key);
            item.setConfigValue(null == value ? "" : value.toString());
            item.setDisable(0);

            tConfigurationCenterInfoList.add(item);
        }
        configurationCenterInfoRepository.saveBatch(tConfigurationCenterInfoList);
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
    @SuppressWarnings("ALL")
    private void render(Map tranfer, Map map, String mapping, String profile, List<TConfigurationCenterInfo> tConfigurationCenterInfoList, String binderName) {
        map.forEach((key, value) -> {
            if (value instanceof Map) {
                render(tranfer, (Map) value, key.toString(), profile, tConfigurationCenterInfoList, binderName);
            }
            TConfigurationCenterInfo item = new TConfigurationCenterInfo();
            item.setConfigItem(binderName);
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
    public void unregister(String binderName) {
        configurationCenterInfoRepository.delete(binderName);
    }

    @Override
    public String findValue(String binderName, String profile) {
        List<TConfigurationCenterInfo> list = configurationCenterInfoRepository.list(binderName, profile);
        Map<String, List<TConfigurationCenterInfo>> valueMapping = new HashMap<>();
        for (TConfigurationCenterInfo info : list) {
            valueMapping.computeIfAbsent(info.getConfigItem(), it -> new ArrayList<>()).add(info);
        }
        Map<String, Map<String, Object>> rs = new HashMap<>();

        for (Map.Entry<String, List<TConfigurationCenterInfo>> entry : valueMapping.entrySet()) {
            String key = entry.getKey();
            List<TConfigurationCenterInfo> entryValue = entry.getValue();
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
    private Map<String, Object> render(String key, List<TConfigurationCenterInfo> entryValue) {
        Map<String, Object> rs1 = new HashMap<>(1);
        Map<String, Object> rs = new HashMap<>(entryValue.size());

        rs1.put(key, rs);

        for (TConfigurationCenterInfo tConfigurationCenterInfo : entryValue) {
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
    private boolean isMatcher(String configCondition) {
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
    public String[] named() {
        return new String[]{"database"};
    }

    @Override
    public void notifyConfig(List<NotifyConfig> notifyConfig, ProtocolServer protocolServer) {
        ServiceProvider<KeyManagerProvider> providerServiceProvider = ServiceProvider.of(KeyManagerProvider.class);
        KeyManagerProvider keyManagerProvider = providerServiceProvider.getExtension(configServerProperties.getKeyManager());
        ServiceProvider<Encrypt> serviceProvider = ServiceProvider.of(Encrypt.class);
        Encrypt encrypt = serviceProvider.getExtension(configServerProperties.getEncrypt());

        for (NotifyConfig config : notifyConfig) {
            String providerKey = configServerProperties.isOpenKey() ? keyManagerProvider.getKey(config.getConfigItem()) : DEFAULT_SER;
            KeyValue keyValue = new KeyValue();
            keyValue.setDataId(config.getConfigName());
            keyValue.setData(config.getConfigValue());

            protocolServer.notifyClient(config, encrypt.encodeHex(Json.toJson(keyValue), providerKey));
        }
    }


    @Override
    public void notifyConfig(Integer configId, String configValue, Integer disable, ProtocolServer protocolServer) {
        List<NotifyConfig> notifyConfig = new ArrayList<>();
        try {
            TConfigurationCenterInfo referenceById = configurationCenterInfoRepository.findById(configId);
            referenceById.setConfigValue(configValue);
            referenceById.setDisable(disable);
            configurationCenterInfoRepository.update(configValue, disable, configId);
            if (disable != 0) {
                return;
            }

            Map<String, List<TConfigurationCenterInfo>> temp = new HashMap<>();
            List<TConfigurationCenterInfo> tConfigurationCenterInfos = configurationCenterInfoRepository.listByConfigId(configId);
            for (TConfigurationCenterInfo tConfigurationCenterInfo : tConfigurationCenterInfos) {
                temp.computeIfAbsent(tConfigurationCenterInfo.getConfigItem(), it -> new ArrayList<>()).add(tConfigurationCenterInfo);
            }

            for (Map.Entry<String, List<TConfigurationCenterInfo>> entry : temp.entrySet()) {
                String key = entry.getKey();
                List<TConfigurationCenterInfo> value = entry.getValue();

                NotifyConfig item = new NotifyConfig();
                item.setConfigName(referenceById.getConfigName()).setConfigValue(configValue);
                item.setConfigItem(key);
                if (value.isEmpty()) {
                    continue;
                }
                try {
                    item.setBinderPort(value.stream().filter(it -> "binder-port".equalsIgnoreCase(it.getConfigName())).map(TConfigurationCenterInfo::getConfigValue).collect(Collectors.toList()).get(0));
                    item.setBinderIp(value.stream().filter(it -> "binder-client".equalsIgnoreCase(it.getConfigName())).map(TConfigurationCenterInfo::getConfigValue).collect(Collectors.toList()).get(0));

                    notifyConfig.add(item);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (notifyConfig.isEmpty()) {
            return;
        }
        notifyConfig(notifyConfig, protocolServer);
    }

    @Override
    public Page<TConfigurationCenterInfo> findAll(Integer page, Integer pageSize, String profile) {
        TConfigurationCenterInfo query = new TConfigurationCenterInfo();
        query.setConfigProfile(profile);

        SearchResult<TConfigurationCenterInfo> result = configurationCenterInfoRepository.query(MapUtils.builder().field(TConfigurationCenterInfo::getConfigProfile, profile)
                .limit((long) page * pageSize, pageSize));

        if(null == result) {
            return new PageImpl<>(Collections.emptyList());
        }
        return new PageImpl<>(result.getData(), PageRequest.of(page, pageSize), result.getTotal().longValue());
    }

    @Override
    public void distributeUpdate(List<Integer> configId, String configItem) {
        configurationDistributeInfoRepository.deleteByConfigItem(configItem);

        List<TConfigurationDistributeInfo> rs = new ArrayList<>(configId.size());
        for (Integer integer : configId) {
            TConfigurationDistributeInfo item = new TConfigurationDistributeInfo();

            item.setConfigId(integer);
            item.setConfigItem(configItem);

            rs.add(item);
        }

        configurationDistributeInfoRepository.saveBatch(rs);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if(null != configurationCenterInfoRepository) {
            return;
        }

        initial();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.configServerProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(ConfigServerProperties.PRE, ConfigServerProperties.class);
    }
}
