package com.chua.starter.vuesql.support.channel;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.database.ResultSetUtils;
import com.chua.common.support.database.factory.DelegateDataSource;
import com.chua.common.support.extra.el.expression.token.KeyWord;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.DatabaseType;
import com.chua.starter.vuesql.enums.Type;
import com.chua.starter.vuesql.pojo.Construct;
import com.chua.starter.vuesql.pojo.Keyword;
import com.chua.starter.vuesql.pojo.Keyword.ColumnKeyword;
import com.chua.starter.vuesql.pojo.SqlResult;
import com.chua.starter.vuesql.utils.JdbcDriver;
import org.apache.commons.dbutils.QueryRunner;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 表
 *
 * @author CH
 */
@Component("mysql8")
public class MysqlTableChannel implements TableChannel {

    @Override
    public String createUrl(WebsqlConfig config) {
        return StringUtils.format("jdbc:mysql://{}:{}/{}?{}",
                config.getConfigIp(), config.getConfigPort(), config.getConfigDatabase(), config.getConfigParam());
    }

    @Override
    public List<Construct> getDataBaseConstruct(WebsqlConfig config) {
        List<Construct> rs = new LinkedList<>();
        rs.add(Construct.builder().type(Type.DATABASE).id(1).pid(0).name(config.getConfigDatabase()).build());
        rs.add(Construct.builder().type(Type.TABLE).pid(1).id(2).name("表").build());
        rs.add(Construct.builder().type(Type.VIEW).pid(1).id(3).name("视图").build());

        AtomicInteger index = new AtomicInteger(4);
        try (Connection connection = JdbcDriver.createConnection(DatabaseType.MYSQL8, config)) {
            ResultSet resultSet = connection.getMetaData().getTables(config.getConfigDatabase(), null, null, new String[]{"TABLE"});
            ResultSetUtils.doLine(resultSet, tables -> {
                rs.add(Construct.builder().type(Type.TABLE).pid(2).id(index.getAndIncrement()).name(tables.getString("TABLE_NAME")).build());
            });

            ResultSet resultSet1 = connection.getMetaData().getTables(config.getConfigDatabase(), null, null, new String[]{"VIEW"});
            ResultSetUtils.doLine(resultSet1, tables -> {
                rs.add(Construct.builder().type(Type.VIEW).pid(3).id(index.getAndIncrement()).name(tables.getString("TABLE_NAME")).build());
            });

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rs;
    }

    @Override
    public List<Keyword> getKeyword(WebsqlConfig config) {
        List<Keyword> rs = new LinkedList<>();
        try (Connection connection = JdbcDriver.createConnection(DatabaseType.MYSQL8, config)) {
            ResultSet resultSet = connection.getMetaData().getTables(config.getConfigDatabase(), null, null, new String[]{"TABLE"});
            Map<String, Keyword> tpl = new LinkedHashMap<>();
            ResultSetUtils.doLine(resultSet, tables -> {
                Keyword keyword = Keyword.builder().text(tables.getString("TABLE_NAME")).build();
                tpl.put(keyword.getText(), keyword);
                rs.add(keyword);
            });

            Map<String, List<String>> columns = new HashMap<>();
            ResultSet resultSet1 = connection.getMetaData().getColumns(config.getConfigDatabase(), null,null, null);
            ResultSetUtils.doLine(resultSet1, tables1 -> {
                columns.computeIfAbsent(tables1.getString("TABLE_NAME"), it -> new LinkedList<>()).add(tables1.getString("COLUMN_NAME"));
            });

            for (Map.Entry<String, Keyword> entry : tpl.entrySet()) {
                List<String> strings = columns.get(entry.getKey());
                entry.getValue().setColumns(strings.stream().map(it -> {
                    return ColumnKeyword.builder().text(it).displayText(it).build();
                }).collect(Collectors.toList()));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rs;
    }

    @Override
    public SqlResult execute(WebsqlConfig websqlConfig, String sql, Integer pageNum, Integer pageSize) {
        try (Connection connection = JdbcDriver.createConnection(DatabaseType.MYSQL8, websqlConfig)) {
            return JdbcDriver.execute(connection, sql, pageNum, pageSize);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
