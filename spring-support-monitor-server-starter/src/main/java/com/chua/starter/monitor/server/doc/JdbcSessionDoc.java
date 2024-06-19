package com.chua.starter.monitor.server.doc;

import cn.smallbun.screw.core.Configuration;
import cn.smallbun.screw.core.engine.*;
import cn.smallbun.screw.core.metadata.model.DataModel;
import cn.smallbun.screw.core.process.DataModelProcess;
import cn.smallbun.screw.core.process.ProcessConfig;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.datasource.jdbc.option.DataSourceOptions;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.session.doc.SessionDoc;
import com.chua.common.support.session.query.DocQuery;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;

import static cn.smallbun.screw.core.constant.DefaultConstants.DESCRIPTION;

/**
 * jdbc会话文档
 *
 * @author CH
 * @since 2023/10/02
 */
@Spi("JDBC")
public class JdbcSessionDoc implements SessionDoc {

    private final DataSourceOptions databaseOptions;

    public JdbcSessionDoc(DataSourceOptions databaseOptions) {
        this.databaseOptions = databaseOptions;
    }


    @Override
    public byte[] create(DocQuery query) {
        EngineFileType engineFileType = EngineFileType.HTML;

        if(StringUtils.isNotEmpty(query.getType())) {
            try {
                engineFileType = EngineFileType.valueOf(query.getType());
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 生成配置
        EngineConfig engineConfig = EngineConfig.builder()
                // 生成文件路径
                .fileOutputDir("./doc")
                // 打开目录 设置为true执行完代码后会自动打开对应路径文件夹
                .openOutputDir(false)
                // 文件类型,支持三种类型
                .fileType(engineFileType)
//                .fileType(EngineFileType.WORD)
                // 生成模板实现
                .produceType(EngineTemplateType.velocity).build();

        ProcessConfig processConfig = ProcessConfig.builder()
                // 忽略表名
                .ignoreTableName(Splitter.on(',').trimResults().omitEmptyStrings().splitToList(query.getIgnoreTableName())).build();
        // 配置
        HikariDataSource hikariDataSource = null;
        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setUsername(databaseOptions.getUsername());
            hikariConfig.setPassword(databaseOptions.getPassword());
            hikariConfig.setJdbcUrl(databaseOptions.getUrl());
            hikariConfig.setDriverClassName(databaseOptions.getDriver());
            hikariDataSource = new HikariDataSource(hikariConfig);
            Configuration config = Configuration.builder()
                    // 版本
                    .version("1.0.0")
                    // 描述
                    .description("数据库说明文档")
                    // 数据源
                    .dataSource(hikariDataSource)
                    // 生成配置
                    .engineConfig(engineConfig)
                    // 生成配置
                    .produceConfig(processConfig).build();

            // 执行生成
            try {
                long start = System.currentTimeMillis();
                //处理数据
                DataModel dataModel = new DataModelProcess(config).process();
                String docName = getDocName(dataModel.getDatabase(), config);
                //产生文档
                TemplateEngine produce = new EngineFactory(config.getEngineConfig()).newInstance();
                produce.produce(dataModel, docName);
                File file = new File("./doc", docName + "." + engineFileType.name().toLowerCase());

                String string = IoUtils.toString(file);
                string = string.replace("<style type=\"text/css\">", "<style type=\"text/css\">::-webkit-scrollbar {width: 5px;height: 5px;}\n" +
                        "::-webkit-scrollbar-thumb {background-color: rgba(50, 50, 50, 0.3);}\n" +
                        "::-webkit-scrollbar-thumb:hover {background-color: rgba(50, 50, 50, 0.6);}\n" +
                        "::-webkit-scrollbar-track {background-color: rgba(50, 50, 50, 0.1);}\n" +
                        "::-webkit-scrollbar-track:hover {background-color: rgba(50, 50, 50, 0.2);}");
                return string.getBytes();
            } catch (Exception ignored) {
            }
        } finally {
            IoUtils.closeQuietly(hikariDataSource);
        }
        return new byte[0];
    }

    /**
     * 获取文档名称
     *
     * @param database {@link String}
     * @param config
     * @return {@link String} 名称
     */
    String getDocName(String database, Configuration config) {
        //自定义文件名称不为空
        if (cn.smallbun.screw.core.util.StringUtils.isNotBlank(config.getEngineConfig().getFileName())) {
            return config.getEngineConfig().getFileName();
        }
        //描述
        String description = config.getDescription();
        if (cn.smallbun.screw.core.util.StringUtils.isBlank(description)) {
            description = DESCRIPTION;
        }
        //版本号
        String version = config.getVersion();
        if (cn.smallbun.screw.core.util.StringUtils.isBlank(version)) {
            return database + "_" + description;
        }
        return database + "_" + description + "_" + version;
    }
}
