package com.chua.starter.config.server.manager;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.database.DataSourceUtils;

import javax.sql.DataSource;

/**
 * 文件管理
 * @author CH
 * @since 2022/8/1 9:38
 */
@Spi("file")
public class FileConfigurationManager extends DatabaseConfigurationManager{

    protected DataSource getDataSource() {
        return DataSourceUtils.createLocalFileDataSource("../config", "config-db");
    }
}
