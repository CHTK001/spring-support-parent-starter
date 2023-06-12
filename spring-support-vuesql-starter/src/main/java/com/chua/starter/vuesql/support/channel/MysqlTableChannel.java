package com.chua.starter.vuesql.support.channel;

import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.pojo.Table;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 表
 *
 * @author CH
 */
@Configuration("mysql")
public class MysqlTableChannel implements TableChannel {

    @Override
    public List<Table> getAllTable(String dbName, WebsqlConfig config) {
        return null;
    }

    @Override
    public List<Table> getTableInfo(String dbName, String tableName, WebsqlConfig config) {
        return null;
    }
}
