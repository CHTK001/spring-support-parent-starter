package com.chua.starter.monitor.server.doc;

import cn.smallbun.screw.core.Configuration;
import cn.smallbun.screw.core.engine.*;
import cn.smallbun.screw.core.metadata.Column;
import cn.smallbun.screw.core.metadata.Database;
import cn.smallbun.screw.core.metadata.PrimaryKey;
import cn.smallbun.screw.core.metadata.Table;
import cn.smallbun.screw.core.metadata.model.ColumnModel;
import cn.smallbun.screw.core.metadata.model.DataModel;
import cn.smallbun.screw.core.metadata.model.TableModel;
import cn.smallbun.screw.core.process.DataModelProcess;
import cn.smallbun.screw.core.process.ProcessConfig;
import cn.smallbun.screw.core.query.DatabaseQuery;
import cn.smallbun.screw.core.query.DatabaseQueryFactory;
import cn.smallbun.screw.core.query.DatabaseType;
import cn.smallbun.screw.core.util.ExceptionUtils;
import cn.smallbun.screw.core.util.JdbcUtils;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.datasource.jdbc.option.DataSourceOptions;
import com.chua.common.support.doc.Document;
import com.chua.common.support.doc.query.DocQuery;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.media.MediaTypeFactory;
import com.chua.common.support.oss.result.GetObjectResult;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static cn.smallbun.screw.core.constant.DefaultConstants.*;

/**
 * jdbc会话文档
 *
 * @author CH
 * @since 2023/10/02
 */
@Spi("JDBC")
@Slf4j
public class JdbcDocument implements Document {

    private final DataSourceOptions databaseOptions;
    private HikariDataSource hikariDataSource = null;

    public JdbcDocument(DataSourceOptions databaseOptions) {
        this.databaseOptions = databaseOptions;
    }

    public static boolean supportQuery(String genDriver) {
        DatabaseType dbType = JdbcUtils.getDbType(genDriver);
        return null != dbType;
    }


    @Override
    public GetObjectResult create(DocQuery query) {
        EngineFileType engineFileType = EngineFileType.HTML;

        if(StringUtils.isNotEmpty(query.getType())) {
            try {
                if("DOC".equalsIgnoreCase(query.getType())) {
                    engineFileType = EngineFileType.WORD;
                } else {
                    engineFileType = EngineFileType.valueOf(query.getType());
                }
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
                .ignoreTableName(Splitter.on(',').trimResults().omitEmptyStrings().splitToList(query.getIgnoreName())).build();
        // 配置
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
                DataModel dataModel = new CustomDataModelProcess(config).process();
                String docName = getDocName(dataModel.getDatabase(), config);
                //产生文档
                TemplateEngine produce = new EngineFactory(config.getEngineConfig()).newInstance();
                produce.produce(dataModel, docName);
                File file = new File("./doc", docName + "." + StringUtils.defaultString(query.getType(), "html").toLowerCase());


                if(engineFileType != EngineFileType.HTML) {
                    return GetObjectResult.builder()
                            .inputStream(new ByteArrayInputStream(FileUtils.toByteArray(file)))
                            .mediaType(MediaTypeFactory.getNoneMediaType(file.getName()))
                            .build();
                }
                String string = IoUtils.toString(file);
                string = string.replace("<style type=\"text/css\">", "<style type=\"text/css\">::-webkit-scrollbar {width: 5px;height: 5px;}\n" +
                        "::-webkit-scrollbar-thumb {background-color: rgba(50, 50, 50, 0.3);}\n" +
                        "::-webkit-scrollbar-thumb:hover {background-color: rgba(50, 50, 50, 0.6);}\n" +
                        "::-webkit-scrollbar-track {background-color: rgba(50, 50, 50, 0.1);}\n" +
                        "::-webkit-scrollbar-track:hover {background-color: rgba(50, 50, 50, 0.2);}");
                return GetObjectResult.builder()
                        .inputStream(new ByteArrayInputStream(string.getBytes()))
                        .mediaType(MediaTypeFactory.getNoneMediaType(file.getName()))
                        .build();
            } catch (Exception ignored) {
            }
        } finally {
            IoUtils.closeQuietly(hikariDataSource);
        }
        return GetObjectResult.builder().build();
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

    @Override
    public void close() throws Exception {
        IoUtils.closeQuietly(hikariDataSource);
    }


    static class CustomDatabaseQueryFactory extends DatabaseQueryFactory {

        /**
         * 构造函数
         *
         * @param source {@link DataSource}
         */
        public CustomDatabaseQueryFactory(DataSource source) {
            super(source);
        }


        /**
         * 获取配置的数据库类型实例
         *
         * @return {@link DatabaseQuery} 数据库查询对象
         */
        public DatabaseQuery newInstance() {
            try {
                //获取数据库URL 用于判断数据库类型
                String url = this.getDataSource().getConnection().getMetaData().getURL();
                //获取实现类
                DatabaseType dbType = JdbcUtils.getDbType(url);
                Class<? extends DatabaseQuery> query = dbType.getImplClass();
                //获取有参构造
                Constructor<? extends DatabaseQuery> constructor = query
                        .getConstructor(DataSource.class);
                //实例化
                return constructor.newInstance(getDataSource());
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException
                     | InvocationTargetException | SQLException e) {
                throw ExceptionUtils.mpe(e);
            }
        }
    }


    static class CustomDataModelProcess extends DataModelProcess {
        volatile Map<String, List<PrimaryKey>> primaryKeysCaching = new ConcurrentHashMap<>();
        volatile Map<String, List<Column>>          columnsCaching     = new ConcurrentHashMap<>();
        volatile Map<String, List<? extends Table>> tablesCaching      = new ConcurrentHashMap<>();
        /**
         * 构造方法
         *
         * @param configuration {@link Configuration}
         */
        public CustomDataModelProcess(Configuration configuration) {
            super(configuration);
        }

        @Override
        public DataModel process() {
            //获取query对象
            DatabaseQuery query = new CustomDatabaseQueryFactory(config.getDataSource()).newInstance();
            DataModel model = new DataModel();
            //Title
            model.setTitle(config.getTitle());
            //org
            model.setOrganization(config.getOrganization());
            //org url
            model.setOrganizationUrl(config.getOrganizationUrl());
            //version
            model.setVersion(config.getVersion());
            //description
            model.setDescription(config.getDescription());

            /*查询操作开始*/
            long start = System.currentTimeMillis();
            //获取数据库
            Database database = query.getDataBase();
            log.debug("query the database time consuming:{}ms",
                    (System.currentTimeMillis() - start));
            model.setDatabase(database.getDatabase());
            start = System.currentTimeMillis();
            //获取全部表
            List<? extends Table> tables = query.getTables();
            log.debug("query the table time consuming:{}ms", (System.currentTimeMillis() - start));
            //获取全部列
            start = System.currentTimeMillis();
            List<? extends Column> columns = query.getTableColumns();
            log.debug("query the column time consuming:{}ms", (System.currentTimeMillis() - start));
            //根据表名获取主键
            start = System.currentTimeMillis();
            List<? extends PrimaryKey> primaryKeys = query.getPrimaryKeys();
            log.debug("query the primary key time consuming:{}ms",
                    (System.currentTimeMillis() - start));
            /*查询操作结束*/

            /*处理数据开始*/
            start = System.currentTimeMillis();
            List<TableModel> tableModels = new ArrayList<>();
            tablesCaching.put(database.getDatabase(), tables);
            for (Table table : tables) {
                //处理列，表名为key，列名为值
                columnsCaching.put(table.getTableName(),
                        columns.stream().filter(i -> i.getTableName().equals(table.getTableName()))
                                .collect(Collectors.toList()));
                //处理主键，表名为key，主键为值
                primaryKeysCaching.put(table.getTableName(),
                        primaryKeys.stream().filter(i -> i.getTableName().equals(table.getTableName()))
                                .collect(Collectors.toList()));
            }
            for (Table table : tables) {
                /*封装数据开始*/
                TableModel tableModel = new TableModel();
                //表名称
                tableModel.setTableName(table.getTableName());
                //说明
                tableModel.setRemarks(table.getRemarks());
                //添加表
                tableModels.add(tableModel);
                //处理列
                List<ColumnModel> columnModels = new ArrayList<>();
                //处理主键
                List<String> keyList = primaryKeysCaching.get(table.getTableName()).stream()
                        .map(PrimaryKey::getColumnName).collect(Collectors.toList());
                for (Column column : columnsCaching.get(table.getTableName())) {
                    packageColumn(columnModels, keyList, column);
                }
                //放入列
                tableModel.setColumns(columnModels);
            }
            //设置表
            model.setTables(filterTables(tableModels));
            //优化数据
            optimizeData(model);
            /*封装数据结束*/
            log.debug("encapsulation processing data time consuming:{}ms",
                    (System.currentTimeMillis() - start));
            return model;
        }
    }

    /**
     * packageColumn
     * @param columnModels {@link List}
     * @param keyList {@link List}
     * @param column {@link Column}
     */
    private static void packageColumn(List<ColumnModel> columnModels, List<String> keyList,
                                      Column column) {
        ColumnModel columnModel = new ColumnModel();
        //表中的列的索引（从 1 开始）
        columnModel.setOrdinalPosition(column.getOrdinalPosition());
        //列名称
        columnModel.setColumnName(column.getColumnName());
        //类型
        columnModel.setTypeName(column.getTypeName().toLowerCase());
        //指定列大小
        columnModel.setColumnSize(column.getColumnSize());
        //小数位
        columnModel.setDecimalDigits(
                cn.smallbun.screw.core.util.StringUtils.defaultString(column.getDecimalDigits(), ZERO_DECIMAL_DIGITS));
        //可为空
        columnModel.setNullable(ZERO.equals(column.getNullable()) ? N : Y);
        //是否主键
        columnModel.setPrimaryKey(keyList.contains(column.getColumnName()) ? Y : N);
        //默认值
        columnModel.setColumnDef(column.getColumnDef());
        //说明
        columnModel.setRemarks(column.getRemarks());
        //放入集合
        columnModels.add(columnModel);
    }
}
