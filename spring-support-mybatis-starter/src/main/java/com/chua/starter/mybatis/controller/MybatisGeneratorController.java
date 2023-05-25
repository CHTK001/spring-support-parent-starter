package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.generator.AutoGenerator;
import com.baomidou.mybatisplus.generator.config.*;
import com.chua.common.support.bean.BeanMap;
import com.chua.common.support.collection.TypeHashMap;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.NetAddress;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 代码生成器
 *
 * @author CH
 */
@Controller
@RequestMapping("${plugin.shell.server.context-path:/generator}")
public class MybatisGeneratorController implements InitializingBean {

    private static final Map<DataSource, DataSourceConfig> CONFIG_MAP = new HashMap<>();
    private final Map<String, DataSource> dataSourceMap = new HashMap<>();
    @Resource
    private ApplicationContext applicationContext;

    /**
     * 输出目录
     */
    private String output = ".";

    @GetMapping
    public ResponseEntity<byte[]> generator(Generator generator) {
        DataSource dataSource = dataSourceMap.get(generator.getName());
        if (null == dataSource) {
            return ResponseEntity.noContent().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
        }

        File newOutput = new File(output + "/" + DateTime.now().toStandard() + "/" + IdUtils.createTimeId());
        try {
            FileUtils.forceMkdir(newOutput);
            GlobalConfig globalConfig = buildGlobal(newOutput, generator);
            PackageConfig packageConfig = buildPackage(generator);
            StrategyConfig strategyConfig = buildStrategy(generator);

            DataSourceConfig dataSourceConfig = builderDataSourceConfig(dataSource);
            AutoGenerator generator1 =
                    new AutoGenerator(dataSourceConfig)
                            .strategy(strategyConfig)
                            .global(globalConfig)
                            .packageInfo(packageConfig);
            generator1.execute();
        } catch (IOException ignored) {
        } finally {
            try {
                newOutput.delete();
            } catch (Exception ignored) {
            }
        }

        return ResponseEntity.noContent().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
    }

    private DataSourceConfig builderDataSourceConfig(DataSource dataSource) {
        return CONFIG_MAP.computeIfAbsent(dataSource, dataSource1 -> {
            TypeHashMap typeHashMap = new TypeHashMap();
            typeHashMap.addProfile(BeanMap.of(dataSource1));
            NetAddress netAddress = NetAddress.of(typeHashMap.getString("driver-url", "url", "jdbc-url"));
            IDbQuery dbQuery = ServiceProvider.of(IDbQuery.class).getExtension("com.baomidou.mybatisplus.generator.config.querys." + netAddress.getProtocol() + "Query");
            ITypeConvert typeConvert = ServiceProvider.of(ITypeConvert.class).getExtension("com.baomidou.mybatisplus.generator.config.converts." + netAddress.getProtocol() + "TypeConvert");
            IKeyWordsHandler keyWordsHandler = ServiceProvider.of(IKeyWordsHandler.class).getExtension("com.baomidou.mybatisplus.generator.keywords." + netAddress.getProtocol() + "KeyWordsHandler");
            return new DataSourceConfig.Builder(dataSource1)
                    .dbQuery(dbQuery)
                    .typeConvert(typeConvert)
                    .keyWordsHandler(keyWordsHandler)
                    .build();
        });
    }

    private StrategyConfig buildStrategy(Generator generator) {
        StrategyConfig.Builder strategyBuilder = new StrategyConfig.Builder();
        buildEntity(strategyBuilder.entityBuilder(), generator.getEntity());
        buildMapper(strategyBuilder.mapperBuilder(), generator.getMapper());

        return strategyBuilder.addInclude(generator.getInclude().toArray(new String[0])).build();
    }

    private void buildMapper(com.baomidou.mybatisplus.generator.config.builder.Mapper.Builder mapperBuilder, Mapper mapper) {
        if (mapper.isMapperAnnotation()) {
            mapperBuilder.mapperAnnotation(org.apache.ibatis.annotations.Mapper.class);
        }
    }

    private void buildEntity(com.baomidou.mybatisplus.generator.config.builder.Entity.Builder entityBuilder, Entity entity) {
        if (entity.isLombok()) {
            entityBuilder.enableLombok();
        }

        if (entity.isChain()) {
            entityBuilder.enableChainModel();
        }

        if (entity.isColumnConstant()) {
            entityBuilder.enableColumnConstant();
        }
    }

    private PackageConfig buildPackage(Generator generator) {
        Package aPackage = generator.getPackages();
        return new PackageConfig.Builder().controller(aPackage.getController())
                .entity(aPackage.entity)
                .mapper(aPackage.mapper)
                .service(aPackage.service)
                .serviceImpl(aPackage.serviceImpl)
                .controller(aPackage.getController())
                .moduleName(aPackage.moduleName)
                .xml(aPackage.xml)
                .parent(aPackage.parent).build();
    }

    private GlobalConfig buildGlobal(File newOutput, Generator generator) {
        GlobalConfig.Builder builder = new GlobalConfig.Builder().author(generator.getAuthor());
        if (generator.isKotlin()) {
            builder.enableKotlin();
        }
        if (generator.isSwagger()) {
            builder.enableSwagger();
        }
        if (generator.isSpringdoc()) {
            builder.enableSpringdoc();
        }
        return builder.outputDir(newOutput.getAbsolutePath()).commentDate("yyyy-MM-dd").build();
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        dataSourceMap.putAll(applicationContext.getBeansOfType(DataSource.class));
    }


    @Data
    public static class Package {

        /**
         * 父包名。如果为空，将下面子包名必须写全部， 否则就只需写子包名
         */
        private String parent = "com.chua";

        /**
         * 父包模块名
         */
        private String moduleName = "";

        /**
         * Entity包名
         */
        private String entity = "entity";

        /**
         * Service包名
         */
        private String service = "service";

        /**
         * Service Impl包名
         */
        private String serviceImpl = "service.impl";

        /**
         * Mapper包名
         */
        private String mapper = "mapper";

        /**
         * Mapper XML包名
         */
        private String xml = "mapper.xml";

        /**
         * Controller包名
         */
        private String controller = "controller";
    }

    @Data
    public static class Mapper {
        /**
         * 是否添加 @Mapper 注解（默认 false）
         *
         * @since 3.5.1
         * @deprecated 3.5.4
         */
        private boolean mapperAnnotation;
    }

    @Data
    public static class Entity {
        /**
         * 【实体】是否生成字段常量（默认 false）<br>
         * -----------------------------------<br>
         * public static final String ID = "test_id";
         */
        private boolean columnConstant;

        /**
         * 【实体】是否为链式模型（默认 false）
         *
         * @since 3.3.2
         */
        private boolean chain;

        /**
         * 【实体】是否为lombok模型（默认 false）<br>
         * <a href="https://projectlombok.org/">document</a>
         */
        private boolean lombok;
    }

    @Data
    public static class Generator {
        /**
         * 需要包含的表名，允许正则表达式（与exclude二选一配置）<br/>
         */
        private final Set<String> include = new HashSet<>();
        /**
         * 数据库名称
         */
        private String name;
        /**
         * 启用 schema 默认 false
         */
        private boolean enableSchema;
        private Entity entity;


        private Mapper mapper;
        private Package packages;

        /**
         * 作者
         */
        private String author = "CH";

        /**
         * 开启 Kotlin 模式（默认 false）
         */
        private boolean kotlin;

        /**
         * 开启 swagger 模式（默认 false 与 springdoc 不可同时使用）
         */
        private boolean swagger;
        /**
         * 开启 springdoc 模式（默认 false 与 swagger 不可同时使用）
         */
        private boolean springdoc;
    }
}
