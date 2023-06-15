package com.chua.starter.vuesql.support.channel;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.DatabaseType;
import com.chua.starter.vuesql.pojo.Construct;
import com.chua.starter.vuesql.pojo.Keyword;
import com.chua.starter.vuesql.pojo.OpenResult;
import com.chua.starter.vuesql.pojo.SqlResult;
import com.chua.starter.vuesql.utils.JdbcDriver;

import javax.annotation.Resource;
import java.sql.Connection;
import java.util.List;

/**
 * 表
 *
 * @author CH
 */
@Spi({"mysql8", "mysql5"})
public class MysqlTableChannel implements TableChannel {
    @Resource
    private ChannelFactory channelFactory;
    @Override
    public String createUrl(WebsqlConfig config) {
        return StringUtils.format("jdbc:mysql://{}:{}/{}?{}",
                config.getConfigIp(), config.getConfigPort(), config.getConfigDatabase(), StringUtils.defaultString(config.getConfigParam(), ""));
    }

    @Override
    public List<Construct> getDataBaseConstruct(WebsqlConfig config) {
        try  {
            Connection connection = channelFactory.getConnection(config, Connection.class, websqlConfig -> {
                return JdbcDriver.createConnection(DatabaseType.MYSQL8, websqlConfig);
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
                return JdbcDriver.createConnection(DatabaseType.MYSQL8, websqlConfig);
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
                return JdbcDriver.createConnection(DatabaseType.MYSQL8, websqlConfig);
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
    public SqlResult explain(WebsqlConfig config, String sql) {
        try  {
            Connection connection = channelFactory.getConnection(config, Connection.class, websqlConfig -> {
                return JdbcDriver.createConnection(DatabaseType.MYSQL8, websqlConfig);
            }, Connection::isClosed);
            return JdbcDriver.execute(connection, null, null, "explain " + sql, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public OpenResult openTable(WebsqlConfig config, String tableName, Integer pageNum, Integer pageSize) {
        try  {
            Connection connection = channelFactory.getConnection(config, Connection.class, websqlConfig -> {
                return JdbcDriver.createConnection(DatabaseType.MYSQL8, websqlConfig);
            }, Connection::isClosed);
            String sql = "SELECT * FROM " + tableName + " limit " + (pageNum - 1) * pageSize + "," + pageSize;
            return JdbcDriver.query(connection, sql);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Boolean update(WebsqlConfig config, JSONObject newData, JSONObject oldData, JSONObject table) {
        try  {
            Connection connection = channelFactory.getConnection(config, Connection.class, websqlConfig -> {
                return JdbcDriver.createConnection(DatabaseType.MYSQL8, websqlConfig);
            }, Connection::isClosed);
            return JdbcDriver.update(connection, newData, oldData, table.getString("realName"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
