package com.chua.starter.vuesql.support.channel;

import com.alibaba.fastjson2.JSONArray;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.database.factory.DelegateDataSource;
import com.chua.common.support.table.ConnectorFactory;
import com.chua.common.support.utils.FileUtils;
import com.chua.datasource.support.table.CalciteConnectorFactory;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.DatabaseType;
import com.chua.starter.vuesql.pojo.*;
import com.chua.starter.vuesql.utils.JdbcDriver;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 表
 *
 * @author CH
 */
@Spi({"csv", "xls", "xlsx", "dbf", "tsv"})
public class FileTableChannel implements TableChannel {

    @Resource
    private ChannelFactory channelFactory;
    private static final String SQLITE_PATH;

    static {
        SQLITE_PATH = TableChannel.create("/sqlite");

    }

    @Override
    public String createUrl(WebsqlConfig config) {
        return "jdbc:sqlite:" + config.getConfigUrl();
    }

    @Override
    public List<Construct> getDataBaseConstruct(WebsqlConfig config) {
        try {
            Connection connection = channelFactory.getConnection(config, Connection.class, websqlConfig -> {
                return createConnection(config.getConfigType(), websqlConfig);
            }, Connection::isClosed);
            return JdbcDriver.doConstruct(connection, config, false, false, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public OperatorResult doExecute(WebsqlConfig websqlConfig, String tableName, String action) {
        return new OperatorResult();
    }

    @Override
    public List<Keyword> getKeyword(WebsqlConfig config) {
        try {
            Connection connection = channelFactory.getConnection(config, Connection.class, websqlConfig -> {
                return createConnection(config.getConfigType(), websqlConfig);
            }, Connection::isClosed);
            return JdbcDriver.doKeyword(connection, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SqlResult execute(WebsqlConfig config, String sql, Integer pageNum, Integer pageSize, String sortColumn, String sortType) {
        try {
            Connection connection = channelFactory.getConnection(config, Connection.class, websqlConfig -> {
                return createConnection(config.getConfigType(), websqlConfig);
            }, Connection::isClosed);
            return JdbcDriver.execute(connection,
                    sortColumn, sortType,
                    sql,
                    sql.toLowerCase().contains("limit") ? sql : sql + " limit " + pageSize + " offset " + (pageNum - 1) * pageSize);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SqlResult explain(WebsqlConfig websqlConfig, String sql) {
        return null;
    }

    @Override
    public OpenResult openTable(WebsqlConfig config, String tableName, Integer pageNum, Integer pageSize) {
        try {
            Connection connection = channelFactory.getConnection(config, Connection.class, websqlConfig -> {
                return createConnection(config.getConfigType(), websqlConfig);
            }, Connection::isClosed);
            String sql = "SELECT * FROM " + tableName + " limit " + pageSize + " offset " + (pageNum - 1) * pageSize;
            return JdbcDriver.query(connection, sql);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String check(WebsqlConfig websqlConfig, MultipartFile file) {
        if (null == file) {
            return websqlConfig.getConfigType() + "当前支持文件上传,且文件不能为空";
        }
        File file1 = new File(SQLITE_PATH, websqlConfig.getConfigName() + "." + websqlConfig.getConfigType().name());
        try {
            FileUtils.copyFile(file.getInputStream(), file1);
            websqlConfig.setConfigUrl(file1.getAbsolutePath());
            websqlConfig.setConfigFile(file1.getAbsolutePath());
        } catch (IOException e) {
            return websqlConfig.getConfigType() + "数据库上传失败请重试";
        }

        return null;
    }

    @Override
    public Boolean update(WebsqlConfig websqlConfig, String table, JSONArray data) {
        throw new RuntimeException("不支持修改操作");
    }


    private Connection createConnection(DatabaseType configType, WebsqlConfig websqlConfig) {
        ConnectorFactory connectorFactory = new CalciteConnectorFactory();
        connectorFactory.register(websqlConfig.getConfigFile());
        try {
            DelegateDataSource dataSource = (DelegateDataSource) connectorFactory.getDataSource();
            dataSource.afterPropertiesSet();
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
