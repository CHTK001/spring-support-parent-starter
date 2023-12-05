package com.chua.starter.unified.client.support.mybatis;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.chua.common.support.function.Joiner;
import com.chua.common.support.protocol.boot.*;
import com.chua.common.support.protocol.server.annotations.ServiceMapping;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.unified.client.support.properties.UnifiedClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支撑注射器
 *
 * @author CH
 * @since 2023/11/20
 */
@Slf4j
public class SupportInjector extends DefaultSqlInjector implements EnvironmentAware, ApplicationContextAware, CommandLineRunner {

    private final ProtocolClient protocolClient;
    private final ProtocolServer protocolServer;

    private final UnifiedClientProperties unifiedClientProperties;

    private final Map<String, DynamicSqlMethod> methodMap = new ConcurrentHashMap<>();
    private final Map<String, MappedStatement> statementMap = new ConcurrentHashMap<>();
    private ApplicationContext applicationContext;
    private Configuration configuration;

    public SupportInjector(ProtocolClient protocolClient, ProtocolServer protocolServer, UnifiedClientProperties unifiedClientProperties) {
        this.protocolClient = protocolClient;
        this.protocolServer = protocolServer;
        this.unifiedClientProperties = unifiedClientProperties;
    }

    @Override
    public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> list = new ArrayList<>();

        List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);
        list.addAll(methodList);
        list.addAll(methodMap.values());

        return list;
    }

    @ServiceMapping("mybatis")
    public BootResponse mybatis(BootRequest request) {
        String content = request.getContent();
        if(StringUtils.isBlank(content)) {
            return BootResponse.empty();
        }

        log.info("监听到Mybatis推送数据");
        JSONObject jsonObject = JSON.parseObject(content);
        try {
            register(jsonObject);
        } catch (Exception e) {
            return BootResponse.notSupport(e.getMessage());
        }
        return BootResponse.ok();
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.protocolServer.addListen(this);
        if(!unifiedClientProperties.isOpen()) {
            return;
        }
        UnifiedClientProperties.SubscribeOption subscribeOption = unifiedClientProperties.getSubscribeOption(ModuleType.MYBATIS);
        List<String> subscribe = null == subscribeOption ? null : subscribeOption.getSubscribe();
        if(CollectionUtils.isEmpty(subscribe)) {
            return;
        }

        BootResponse response = protocolClient.get(BootRequest.builder()
                .moduleType(ModuleType.MYBATIS)
                .commandType(CommandType.SUBSCRIBE)
                .appName(environment.getProperty("spring.application.name"))
                .profile(environment.getProperty("spring.profiles.active", "default"))
                .content(Joiner.on(",").join(subscribe))
                .build()
        );


        if(response.getCommandType() != CommandType.RESPONSE) {
            log.error("MYBATIS 订阅: {}失败 => {}", subscribe, response.getContent());
            return;
        }

        log.info("MYBATIS 订阅: {} 成功", subscribe);
        try {
            JSONArray jsonArray = JSON.parseArray(response.getContent());
            register(jsonArray);
        } catch (Exception ignored) {
        }
    }

    private void register(JSONArray jsonArray) {
        int size = jsonArray.size();
        for (int i = 0; i < size; i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            if(null == jsonObject) {
                continue;
            }

            register(jsonObject);
        }
    }

    private void register(JSONObject jsonObject) {
        String unifiedMybatisName = jsonObject.getString("unifiedMybatisName");
        if(StringUtils.isBlank(unifiedMybatisName)) {
            return;
        }

        String unifiedMybatisSql = jsonObject.getString("unifiedMybatisSql");

        if(StringUtils.isBlank(unifiedMybatisSql)) {
            return;
        }

        String unifiedMybaticModelType = jsonObject.getString("unifiedMybaticModelType");
        if(!ClassUtils.isPresent(unifiedMybaticModelType)) {
            return;
        }

        Class<?> modelType = ClassUtils.forName(unifiedMybaticModelType);

        String unifiedMybaticMapperType = jsonObject.getString("unifiedMybaticMapperType");
        if(!ClassUtils.isPresent(unifiedMybaticMapperType)) {
            return;
        }

        Class<?> mapperType = ClassUtils.forName(unifiedMybaticMapperType);

        String sqlType = MapUtils.getString(jsonObject, "unifiedMybatisSqlType", "XML");

        SqlType sqlType1 = SqlType.valueOf(sqlType.toUpperCase());
        refreshStatement(unifiedMybatisName, unifiedMybatisSql, sqlType1, mapperType, modelType, jsonObject);
    }

    private void refreshStatement(String unifiedMybatisName, String unifiedMybatisSql, SqlType sqlType, Class<?> mapperType, Class<?> modelType, JSONObject jsonObject) {
        refreshSqlMethod(methodMap.get(unifiedMybatisName), unifiedMybatisName, unifiedMybatisSql, sqlType, modelType, mapperType, jsonObject);
        refreshStatement(statementMap.getOrDefault(unifiedMybatisName,
                statementMap.get(mapperType.getTypeName() + "." + unifiedMybatisName)), unifiedMybatisSql, sqlType, mapperType, modelType);
    }

    private void refreshStatement(MappedStatement mappedStatement, String unifiedMybatisSql, SqlType sqlType1, Class<?> mapperType, Class<?> modelType) {
        if(null == mappedStatement) {
            return;
        }
        if(log.isDebugEnabled()) {
            log.debug("sql => {}", unifiedMybatisSql);
        }
        try {
            MybatisStatementUtils.refresh(configuration, mappedStatement, unifiedMybatisSql, sqlType1, mapperType, modelType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void refreshSqlMethod(DynamicSqlMethod dynamicSqlMethod, String unifiedMybatisName, String unifiedMybatisSql, SqlType sqlType, Class<?> modelType, Class<?> mapperType, JSONObject jsonObject) {
        if(null == dynamicSqlMethod) {
            methodMap.put(unifiedMybatisName, new DynamicSqlMethod(unifiedMybatisName, unifiedMybatisSql, sqlType, modelType, mapperType, jsonObject));
            return;
        }

        dynamicSqlMethod.refresh(unifiedMybatisSql, sqlType);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) throws Exception {
        if(!unifiedClientProperties.isOpen()) {
            return;
        }
        this.configuration = applicationContext.getBean(SqlSessionFactory.class).getConfiguration();
        Collection<String> mappedStatementNames = configuration.getMappedStatementNames();
        for (String mappedStatementName : mappedStatementNames) {
            if(methodMap.containsKey(mappedStatementName)) {
                continue;
            }

            MappedStatement mappedStatement = null;
            try {
                mappedStatement = configuration.getMappedStatement(mappedStatementName, false);
            } catch (Exception e) {
                continue;
            }
            statementMap.put(mappedStatementName, mappedStatement);
        }

        UnifiedClientProperties.SubscribeOption subscribeOption = unifiedClientProperties.getSubscribeOption(ModuleType.MYBATIS);

        if(null == subscribeOption || !subscribeOption.isAutoConfig()) {
            return;
        }

        for (Map.Entry<String, DynamicSqlMethod> entry : methodMap.entrySet()) {
            register(entry.getValue().getConfig());
        }
    }
}
