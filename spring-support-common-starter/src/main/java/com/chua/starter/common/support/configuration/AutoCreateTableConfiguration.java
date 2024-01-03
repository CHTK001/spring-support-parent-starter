package com.chua.starter.common.support.configuration;

import com.chua.common.support.datasource.driver.JdbcDriver;
import com.chua.common.support.datasource.engine.DefaultEngine;
import com.chua.common.support.datasource.engine.Engine;
import com.chua.common.support.datasource.jdbc.JdbcEngineDataSource;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.properties.TablePluginProperties;
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

/**
 * 自动建表
 *
 * @author CH
 */
@Slf4j
@ConditionalOnProperty(prefix = TablePluginProperties.PRE, name = "open", havingValue = "true")
@EnableConfigurationProperties(TablePluginProperties.class)
public class AutoCreateTableConfiguration implements ApplicationContextAware {

    TablePluginProperties tablePluginProperties;
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        tablePluginProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(TablePluginProperties.PRE, TablePluginProperties.class);
        if(!tablePluginProperties.isOpen() || ArrayUtils.isEmpty(tablePluginProperties.getPackages())) {
            return;
        }

        if(tablePluginProperties.isAsync()) {
            ThreadUtils.newStaticThreadPool().execute(this::doCreateTable);
            return;
        }

        doCreateTable();
    }


    void doCreateTable() {
        PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        String[] packages = tablePluginProperties.getPackages();
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
        Engine engine = new DefaultEngine();
        engine.register(new JdbcDriver())
                .register(JdbcEngineDataSource.builder()
                        .dataSource(applicationContext)
                        .build()
                );

    }
}
