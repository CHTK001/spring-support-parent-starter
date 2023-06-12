package com.chua.starter.vuesql.support.channel;

import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.pojo.Table;

import java.util.List;

/**
 * 表
 *
 * @author CH
 */
public interface TableChannel {

    /**
     * 获取所有表
     *
     * @param dbName 数据库
     * @param config 配置
     * @return 获取所有表
     */
    List<Table> getAllTable(String dbName, WebsqlConfig config);

    /**
     * 获取所有表
     *
     * @param dbName    数据库
     * @param tableName 表名
     * @param config    配置
     * @return 获取所有表
     */
    List<Table> getTableInfo(String dbName, String tableName, WebsqlConfig config);
}
