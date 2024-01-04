package com.chua.starter.common.support.configuration;

import com.chua.common.support.datasource.driver.JdbcDriver;
import com.chua.common.support.datasource.engine.DefaultEngine;
import com.chua.common.support.datasource.engine.Engine;
import com.chua.common.support.datasource.enums.ActionType;
import com.chua.common.support.datasource.executor.DdlExecutor;
import com.chua.common.support.datasource.jdbc.JdbcEngineDataSource;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.properties.CreateTableProperties;
import com.chua.starter.common.support.utils.BeanDefinitionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Map;

/**
 * 自动建表
 *
 * @author CH
 */
@Slf4j
@ConditionalOnProperty(prefix = CreateTableProperties.PRE, name = "open", havingValue = "true")
@EnableConfigurationProperties(CreateTableProperties.class)
public class AutoCreateTableConfiguration implements ApplicationContextAware {

    CreateTableProperties createTableProperties;
    private Map<String, DataSource> dataSources;
    private DdlExecutor ddlExecutor;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        createTableProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(CreateTableProperties.PRE, CreateTableProperties.class);
        if(!createTableProperties.isOpen() || ArrayUtils.isEmpty(createTableProperties.getPackages())) {
            return;
        }
        this.dataSources = applicationContext.getBeansOfType(DataSource.class);
        Engine engine = new DefaultEngine();
        engine.register(new JdbcDriver());
        for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
            engine.register(JdbcEngineDataSource.builder()
                    .name(entry.getKey())
                    .dataSource(entry.getValue())
                    .build()
            );
        }

        this.ddlExecutor = engine.toDdl();
        if(createTableProperties.isAsync()) {
            ThreadUtils.newStaticThreadPool().execute(this::doCreateTable);
            return;
        }

        doCreateTable();
    }


    void doCreateTable() {
        PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        String[] packages = createTableProperties.getPackages();
        for (String aPackage : packages) {
            Resource[] resources = new Resource[0];
            try {
                resources = resourcePatternResolver.getResources(aPackage);
            } catch (IOException ignored) {
            }
            register(resources);
        }
    }

    /**
     * 注册
     *
     * @param resources 资源
     */
    private void register(Resource[] resources) {
        for (Resource resource : resources) {
            try {
                register(BeanDefinitionUtils.getType(resource));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
    }

    /**
     * 注册
     *
     * @param type 类型
     */
    private void register(Class<?> type) {
       ddlExecutor.execute(type, ActionType.UPDATE);
    }
}
