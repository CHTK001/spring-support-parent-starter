package com.chua.starter.vuesql.utils;

import com.chua.starter.vuesql.entity.system.WebsqlConfig;
import com.chua.starter.vuesql.enums.DatabaseType;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;

/**
 * 数据库驱动
 *
 * @author CH
 */
@Slf4j
public class JdbcDriver {

    /**
     * 创建链接
     *
     * @param databaseType 数据库类型
     * @param config       配置
     * @return connection
     */
    public static Connection createConnection(DatabaseType databaseType, WebsqlConfig config) {
        String driver = databaseType.getDriver();
        try
        {
            Class.forName(driver);
            return DriverManager.getConnection(config.getConfigUrl(),
                    config.getConfigUsername(), config.getConfigPassword());
        }
        catch (Exception e)
        {
            log.error(e.getMessage(), e);
            return null;
        }
    }
}
