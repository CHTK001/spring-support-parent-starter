package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.generator.AutoGenerator;
import com.baomidou.mybatisplus.generator.config.*;
import com.baomidou.mybatisplus.generator.config.querys.DbQueryDecorator;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;
import com.baomidou.mybatisplus.generator.keywords.BaseKeyWordsHandler;
import com.chua.common.support.bean.BeanMap;
import com.chua.common.support.collection.TypeHashMap;
import com.chua.common.support.database.inquirer.JdbcInquirer;
import com.chua.common.support.file.compress.ZipCompressFile;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.NetAddress;
import com.chua.starter.common.support.result.ResultData;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;

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

    /**
     * 页面
     *
     * @return 页面
     */
    @GetMapping("index")
    public String index() {
        return "index";
    }

    /**
     * 数据库
     *
     * @return 数据库
     */
    @ResponseBody
    @GetMapping("db")
    public ResultData<Collection<String>> db() throws Exception {
        return ResultData.success(dataSourceMap.keySet());
    }

    /**
     * 生成代码
     *
     * @param dataSource 条件
     * @return 生成代码
     */
    @ResponseBody
    @GetMapping("find")
    public ResultData<List<Map<String, Object>>> pageForTable(String dataSource) throws Exception {
        DataSource dataSource1 = dataSourceMap.get(dataSource);
        if (null == dataSource1) {
            return ResultData.success(Collections.emptyList());
        }
        DataSourceConfig dataSourceConfig = builderDataSourceConfig(dataSource1);
        DbQueryDecorator dbQueryDecorator = new DbQueryDecorator(dataSourceConfig, new StrategyConfig.Builder().build());
        String tablesSql = dbQueryDecorator.tablesSql();
        JdbcInquirer jdbcInquirer = new JdbcInquirer(dataSource1, true);
        List<Map<String, Object>> query = jdbcInquirer.query(tablesSql);
        return ResultData.success(query);
    }

    /**
     * 生成代码
     *
     * @param generator 条件
     * @return 生成代码
     */
    @PostMapping
    public ResponseEntity<byte[]> generator(@RequestBody Generator generator) {
        DataSource dataSource = dataSourceMap.get(generator.getName());
        if (null == dataSource) {
            return ResponseEntity.noContent().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
        }

        String include = generator.getInclude();
        String[] split = Splitter.on(',').omitEmptyStrings().trimResults().split(include);
        File newOutput = new File(output + "/" + IdUtils.createTimeId());
        File zip = new File(newOutput, DateTime.now().toFormat("yyyyMMdd") + ".zip");
        if (split.length == 1) {
            zip = new File(newOutput, split[0] + ".zip");
        }
        try {
            FileUtils.forceMkdir(newOutput);
            GlobalConfig globalConfig = buildGlobal(newOutput, generator);
            PackageConfig packageConfig = buildPackage(newOutput, generator);
            StrategyConfig strategyConfig = buildStrategy(generator);

            DataSourceConfig dataSourceConfig = builderDataSourceConfig(dataSource);
            AutoGenerator generator1 =
                    new AutoGenerator(dataSourceConfig)
                            .strategy(strategyConfig)
                            .global(globalConfig)
                            .packageInfo(packageConfig);
            generator1.execute(new FreemarkerTemplateEngine());
            ZipCompressFile zipCompressFile = new ZipCompressFile(zip);
            zipCompressFile.pack(newOutput.getAbsolutePath(), false, "*");
            try (FileInputStream fileInputStream = new FileInputStream(zip)) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + zip.getName())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).body(
                                IoUtils.toByteArray(fileInputStream)
                        );
            }
        } catch (Exception ignored) {
        } finally {
            try {
                FileUtils.forceDeleteDirectory(newOutput);
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
            String protocol = netAddress.getProtocol().replace("jdbc:", "");

            IDbQuery dbQuery = ServiceProvider.of(IDbQuery.class).getExtension("com.baomidou.mybatisplus.generator.config.querys." + protocol + "Query");
            ITypeConvert typeConvert = ServiceProvider.of(ITypeConvert.class).getExtension("com.baomidou.mybatisplus.generator.config.converts." + protocol + "TypeConvert");
            IKeyWordsHandler keyWordsHandler = ServiceProvider.of(BaseKeyWordsHandler.class).getExtension("com.baomidou.mybatisplus.generator.keywords." + protocol + "KeyWordsHandler");
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

        return strategyBuilder.addInclude(Splitter.on(",").trimResults().omitEmptyStrings().split(generator.getInclude())).build();
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

    private PackageConfig buildPackage(File newOutput, Generator generator) {
        Package aPackage = generator.getPackages();
        String parent = aPackage.getParent();
        Map<OutputFile, String> path = new HashMap<>();
        path.put(OutputFile.xml, newOutput.getAbsolutePath() + "/resources/" + aPackage.xml);
        path.put(OutputFile.entity, newOutput.getAbsolutePath() + "/java/" + ((StringUtils.isBlank(parent) ? aPackage.entity : (parent + StringPool.DOT + aPackage.entity))).replace(StringPool.DOT, StringPool.SLASH));
        path.put(OutputFile.mapper, newOutput.getAbsolutePath() + "/java/" + ((StringUtils.isBlank(parent) ? aPackage.mapper : (parent + StringPool.DOT + aPackage.mapper))).replace(StringPool.DOT, StringPool.SLASH));
        path.put(OutputFile.service, newOutput.getAbsolutePath() + "/java/" + ((StringUtils.isBlank(parent) ? aPackage.service : (parent + StringPool.DOT + aPackage.service))).replace(StringPool.DOT, StringPool.SLASH));
        path.put(OutputFile.serviceImpl,newOutput.getAbsolutePath() + "/java/" + ((StringUtils.isBlank(parent) ? aPackage.serviceImpl : (parent + StringPool.DOT + aPackage.serviceImpl))).replace(StringPool.DOT, StringPool.SLASH));
        path.put(OutputFile.controller, newOutput.getAbsolutePath() + "/java/" + ((StringUtils.isBlank(parent) ? aPackage.controller : (parent + StringPool.DOT + aPackage.controller))).replace(StringPool.DOT, StringPool.SLASH));
        return new PackageConfig.Builder()
                .pathInfo(path)
                .moduleName(aPackage.moduleName)
                .entity(aPackage.entity)
                .service(aPackage.service)
                .serviceImpl(aPackage.serviceImpl)
                .controller(aPackage.controller)
                .xml(aPackage.xml)
                .mapper(aPackage.mapper)
                .build();
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
        return builder.disableOpenDir().outputDir(newOutput.getAbsolutePath()).commentDate("yyyy-MM-dd").build();
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
        private String xml = "mapper";

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
        private boolean mapperAnnotation = true;
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
        private boolean chain = true;

        /**
         * 【实体】是否为lombok模型（默认 false）<br>
         * <a href="https://projectlombok.org/">document</a>
         */
        private boolean lombok = true;
    }

    @Data
    public static class Generator {
        /**
         * 需要包含的表名，允许正则表达式（与exclude二选一配置）<br/>
         */
        private String include;
        /**
         * 数据库名称
         */
        private String name;
        /**
         * 启用 schema 默认 false
         */
        private boolean enableSchema;
        private Entity entity = new Entity();


        private Mapper mapper = new Mapper();
        private Package packages = new Package();

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
