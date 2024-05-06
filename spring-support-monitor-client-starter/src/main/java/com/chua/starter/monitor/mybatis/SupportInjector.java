package com.chua.starter.monitor.mybatis;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonArray;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.protocol.annotations.ServiceMapping;
import com.chua.common.support.protocol.boot.*;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.factory.MonitorFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
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

import static io.lettuce.core.pubsub.PubSubOutput.Type.subscribe;

/**
 * 支撑注射器
 *
 * @author CH
 * @since 2023/11/20
 */
@Slf4j
public class SupportInjector extends DefaultSqlInjector implements EnvironmentAware, BeanFactoryAware, ApplicationContextAware, CommandLineRunner {

    private BootProtocolClient protocolClient;
    private BootProtocolServer protocolServer;


    private final Map<String, DynamicSqlMethod> methodMap = new ConcurrentHashMap<>();

    @Getter
    private final Map<String, MappedStatement> statementMap = new ConcurrentHashMap<>();
    private ApplicationContext applicationContext;
    private Configuration configuration;
    private ConfigurableListableBeanFactory beanFactory;

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
        JsonObject jsonObject = Json.getJsonObject(content);
        try {
            register(jsonObject);
        } catch (Exception e) {
            return BootResponse.notSupport(e.getMessage());
        }
        return BootResponse.ok();
    }

    @Override
    public void setEnvironment(Environment environment) {

    }

    private void register(JsonArray jsonArray) {
        int size = jsonArray.size();
        for (int i = 0; i < size; i++) {
            JsonObject jsonObject = jsonArray.getJsonObject(i);
            if(null == jsonObject) {
                continue;
            }

            register(jsonObject);
        }
    }

    private void register(JsonObject jsonObject) {
        String monitorMybatisName = jsonObject.getString("monitorMybatisName");
        if(StringUtils.isBlank(monitorMybatisName)) {
            return;
        }

        String monitorMybatisSql = jsonObject.getString("monitorMybatisSql");

        if(StringUtils.isBlank(monitorMybatisSql)) {
            return;
        }

        String monitorMybatisModelType = jsonObject.getString("monitorMybatisModelType");
        if(!ClassUtils.isPresent(monitorMybatisModelType)) {
            return;
        }

        Class<?> modelType = ClassUtils.forName(monitorMybatisModelType);

        String monitorMybatisMapperType = jsonObject.getString("monitorMybatisMapperType");
        if(!ClassUtils.isPresent(monitorMybatisMapperType)) {
            return;
        }

        Class<?> mapperType = ClassUtils.forName(monitorMybatisMapperType);

        String sqlType = MapUtils.getString(jsonObject, "monitorMybatisSqlType", "XML");

        SqlType sqlType1 = SqlType.valueOf(sqlType.toUpperCase());
        refreshStatement(monitorMybatisName, monitorMybatisSql, sqlType1, mapperType, modelType, jsonObject);
    }

    private void refreshStatement(String monitorMybatisName, String monitorMybatisSql, SqlType sqlType, Class<?> mapperType, Class<?> modelType, JsonObject jsonObject) {
        refreshSqlMethod(methodMap.get(monitorMybatisName), monitorMybatisName, monitorMybatisSql, sqlType, modelType, mapperType, jsonObject);
        refreshStatement(statementMap.getOrDefault(monitorMybatisName,
                statementMap.get(mapperType.getTypeName() + "." + monitorMybatisName)), monitorMybatisSql, sqlType, mapperType, modelType);
    }

    private void refreshStatement(MappedStatement mappedStatement, String monitorMybatisSql, SqlType sqlType1, Class<?> mapperType, Class<?> modelType) {
        if(null == mappedStatement) {
            return;
        }
        if(log.isDebugEnabled()) {
            log.debug("sql => {}", monitorMybatisSql);
        }
        try {
            MybatisStatementUtils.refresh(configuration, mappedStatement, monitorMybatisSql, sqlType1, mapperType, modelType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void refreshSqlMethod(DynamicSqlMethod dynamicSqlMethod, String monitorMybatisName, String monitorMybatisSql, SqlType sqlType, Class<?> modelType, Class<?> mapperType, JsonObject jsonObject) {
        if(null == dynamicSqlMethod) {
            methodMap.put(monitorMybatisName, new DynamicSqlMethod(monitorMybatisName, monitorMybatisSql, sqlType, modelType, mapperType, jsonObject));
            return;
        }

        dynamicSqlMethod.refresh(monitorMybatisSql, sqlType);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(String... args) throws Exception {
        if(!MonitorFactory.getInstance().isEnable()) {
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

//        UnifiedClientProperties.SubscribeOption subscribeOption = monitorClientProperties.getSubscribeOption(ModuleType.MYBATIS);
//
//        if(null == subscribeOption || !subscribeOption.isAutoConfig()) {
//            return;
//        }

        for (Map.Entry<String, DynamicSqlMethod> entry : methodMap.entrySet()) {
            register(entry.getValue().getConfig());
        }
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalArgumentException(
                    "ConfigValueAnnotationBeanPostProcessor requires a ConfigurableListableBeanFactory");
        }
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        String[] beanNamesForType = this.beanFactory.getBeanNamesForType(BootProtocolServer.class);
        if(beanNamesForType.length == 0) {
            return;
        }

        if(!MonitorFactory.getInstance().isEnable()) {
            return;
        }

        this.protocolServer = this.beanFactory.getBean(BootProtocolServer.class);
        this.protocolClient = this.beanFactory.getBean(BootProtocolClient.class);
        this.protocolServer.addMapping(this);
        MonitorFactory monitorFactory = MonitorFactory.getInstance();
        if(!MonitorFactory.getInstance().isEnable()) {
            return;
        }

        if(!MonitorFactory.getInstance().containsKey("MYBATIS")) {
            return;
        }

        BootResponse response = protocolClient.get(BootRequest.builder()
                .moduleType("MYBATIS")
                .commandType(CommandType.SUBSCRIBE)
                .appName(monitorFactory.getAppName())
                .profile(monitorFactory.getActive())
                .content(MonitorFactory.getInstance().getSubscribeApps())
                .build()
        );


        if(response.getCommandType() != CommandType.RESPONSE) {
            log.error("MYBATIS 订阅: {}失败 => {}", subscribe,MapUtils.getString(response.getData(), "data"));
            return;
        }

        log.info("MYBATIS 订阅: {} 成功", subscribe);
        try {
            JsonArray jsonArray = Json.getJsonArray(MapUtils.getString(response.getData(), "data"));
            register(jsonArray);
        } catch (Exception ignored) {
        }
    }
}
