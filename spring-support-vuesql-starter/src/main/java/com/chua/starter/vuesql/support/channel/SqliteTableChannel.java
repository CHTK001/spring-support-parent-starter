package com.chua.starter.vuesql.support.channel;

import com.alibaba.fastjson2.JSONArray;
import com.chua.common.support.utils.FileUtils;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.DatabaseType;
import com.chua.starter.vuesql.pojo.*;
import com.chua.starter.vuesql.utils.JdbcDriver;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.util.List;

/**
 * 表
 *
 * @author CH
 */
@Component("sqlite")
public class SqliteTableChannel implements TableChannel{

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
                return JdbcDriver.createConnection(DatabaseType.SQLITE, websqlConfig);
            }, Connection::isClosed);
            return JdbcDriver.doConstruct(connection, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Keyword> getKeyword(WebsqlConfig config) {
        try  {
            Connection connection = channelFactory.getConnection(config, Connection.class, websqlConfig -> {
                return JdbcDriver.createConnection(DatabaseType.SQLITE, websqlConfig);
            }, Connection::isClosed);
            return JdbcDriver.doKeyword(connection, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SqlResult execute(WebsqlConfig config, String sql, Integer pageNum, Integer pageSize, String sortColumn, String sortType) {
        try{
            Connection connection = channelFactory.getConnection(config, Connection.class, websqlConfig -> {
                return JdbcDriver.createConnection(DatabaseType.SQLITE, websqlConfig);
            }, Connection::isClosed);
            return JdbcDriver.execute(connection,
                    sortColumn, sortType,
                    sql,
                    sql.toLowerCase().contains("limit") ? sql : sql + " limit " + (pageNum - 1) * pageSize + "," + pageSize);
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
        try{
            Connection connection = channelFactory.getConnection(config, Connection.class, websqlConfig -> {
                return JdbcDriver.createConnection(DatabaseType.SQLITE, websqlConfig);
            }, Connection::isClosed);
            String sql = "SELECT * FROM " + tableName + " limit " + (pageNum - 1) * pageSize + "," + pageSize;
            return JdbcDriver.query(connection, sql);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String check(WebsqlConfig websqlConfig, MultipartFile file) {
        if(null == file) {
            return "sqlite当前支支持文件上传,且文件不能为空";
        }
        File file1 = new File(SQLITE_PATH, websqlConfig.getConfigName() + ".db");
        try {
            FileUtils.copyFile(file.getInputStream(), file1);
            websqlConfig.setConfigUrl(file1.getAbsolutePath());
            websqlConfig.setConfigFile(file1.getAbsolutePath());
        } catch (IOException e) {
            return "sqlite数据库上传失败请重试";
        }

        return null;
    }

    @Override
    public Boolean update(WebsqlConfig config, String table, JSONArray data) {
        try {
            Connection connection = channelFactory.getConnection(config, Connection.class, websqlConfig -> {
                return JdbcDriver.createConnection(DatabaseType.SQLITE, websqlConfig);
            }, Connection::isClosed);
            return JdbcDriver.update(connection, table, data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public OperatorResult doExecute(WebsqlConfig config, String tableName, String action) {
        OperatorResult rs = new OperatorResult();
        try {
            Connection connection = channelFactory.getConnection(config, Connection.class, websqlConfig -> {
                return JdbcDriver.createConnection(DatabaseType.MYSQL8, websqlConfig);
            }, Connection::isClosed);
            return JdbcDriver.doExecute(connection, tableName, action);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
