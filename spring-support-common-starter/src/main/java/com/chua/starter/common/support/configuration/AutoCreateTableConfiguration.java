package com.chua.starter.common.support.configuration;

import com.chua.common.support.lang.engine.JdbcEngine;
import com.chua.common.support.lang.engine.datasource.JdbcEngineDataSource;
import com.chua.common.support.utils.*;
import com.chua.starter.common.support.properties.CreateTableProperties;
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
import java.util.Arrays;
import java.util.Map;

/**
 * 自动建表
 *
 * @author CH
 */
@Slf4j
@ConditionalOnProperty(prefix = CreateTableProperties.PRE, name = "enable", havingValue = "true")
@EnableConfigurationProperties(CreateTableProperties.class)
public class AutoCreateTableConfiguration implements ApplicationContextAware {

    CreateTableProperties createTableProperties;
    private Map<String, DataSource> dataSources;
    private JdbcEngine engine;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        createTableProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(CreateTableProperties.PRE, CreateTableProperties.class);
        if(!createTableProperties.isEnable() || ArrayUtils.isEmpty(createTableProperties.getPackages())) {
            return;
        }

        log.info(">>>>>>> 开启自动建表功能[{}]", Arrays.toString(createTableProperties.getPackages()));
        this.dataSources = applicationContext.getBeansOfType(DataSource.class);
        engine = new JdbcEngine();
        for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
            engine.addDataSource(JdbcEngineDataSource.builder()
                    .name(entry.getKey())
                    .dataSource(entry.getValue())
                    .build()
            );
        }


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
                resources = resourcePatternResolver
                        .getResources(FileUtils.normalize(aPackage.replace(".", "/") + "/**/*.class"));
            } catch (IOException ignored) {
            }
            register(resources, aPackage);
        }
    }

    /**
     * 注册
     *
     * @param resources 资源
     * @param aPackage  aPackage
     */
    private void register(Resource[] resources, String aPackage) {
        for (Resource resource : resources) {
            try {
                String replace = StringUtils.after(resource.getFile().getAbsolutePath().replace("\\", "/"),
                        aPackage.replace(".", "/"), 1);
                register(ClassUtils.forName(aPackage + "."+ replace.substring(1).replace(".class", "")));
            } catch (Exception e) {
                continue;
            }

        }
    }

    /**
     * 注册
     *
     * @param type 类型
     */
    private void register(Class<?> type) {
        if(null == type) {
            return;
        }
         engine.build().doIt(type, createTableProperties.getType());
    }
}
