package com.chua.starter.datasource.configuration;

import com.chua.common.support.lang.engine.JdbcEngine;
import com.chua.common.support.lang.engine.datasource.JdbcEngineDataSource;
import com.chua.common.support.utils.*;
import com.chua.starter.datasource.properties.CreateTableProperties;
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
 * 自动建表配置
 * <p>
 * 根据实体类自动创建或更新数据库表结构。
 * </p>
 *
 * <h3>功能特性：</h3>
 * <ul>
 *   <li>支持多数据源</li>
 *   <li>支持异步建表</li>
 *   <li>支持CREATE/UPDATE/DROP_CREATE等模式</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
@Slf4j
@ConditionalOnProperty(prefix = CreateTableProperties.PRE, name = "enable", havingValue = "true")
@EnableConfigurationProperties(CreateTableProperties.class)
public class AutoCreateTableConfiguration implements ApplicationContextAware {

    private CreateTableProperties createTableProperties;
    private Map<String, DataSource> dataSources;
    private JdbcEngine engine;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        createTableProperties = Binder.get(applicationContext.getEnvironment())
                .bindOrCreate(CreateTableProperties.PRE, CreateTableProperties.class);
        
        if (!createTableProperties.isEnable() || ArrayUtils.isEmpty(createTableProperties.getPackages())) {
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

        if (createTableProperties.isAsync()) {
            ThreadUtils.newStaticThreadPool().execute(this::doCreateTable);
            return;
        }

        doCreateTable();
    }

    /**
     * 执行建表操作
     */
    private void doCreateTable() {
        PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        String[] packages = createTableProperties.getPackages();
        
        for (String aPackage : packages) {
            Resource[] resources;
            try {
                resources = resourcePatternResolver
                        .getResources(FileUtils.normalize(aPackage.replace(".", "/") + "/**/*.class"));
            } catch (IOException e) {
                log.warn("扫描包 {} 失败: {}", aPackage, e.getMessage());
                continue;
            }
            register(resources, aPackage);
        }
    }

    /**
     * 注册实体类
     *
     * @param resources 资源
     * @param aPackage  包名
     */
    private void register(Resource[] resources, String aPackage) {
        for (Resource resource : resources) {
            try {
                String replace = StringUtils.after(resource.getFile().getAbsolutePath().replace("\\", "/"),
                        aPackage.replace(".", "/"), 1);
                register(ClassUtils.forName(aPackage + "." + replace.substring(1).replace(".class", "")));
            } catch (Exception e) {
                log.debug("注册实体类失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 注册单个实体类
     *
     * @param type 类型
     */
    private void register(Class<?> type) {
        if (type == null) {
            return;
        }
        
        log.info("开始创建<{}>任务", type.getTypeName());
        
        if (type.isEnum() || type.isInterface() || type.isAnonymousClass() || type.isLocalClass()) {
            return;
        }

        String typeName = type.getTypeName();
        if (typeName.contains("$")) {
            return;
        }
        
        engine.createTable().doIt(type, createTableProperties.getType());
    }
}
