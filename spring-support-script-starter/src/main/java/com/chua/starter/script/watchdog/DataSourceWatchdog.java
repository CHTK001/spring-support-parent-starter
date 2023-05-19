package com.chua.starter.script.watchdog;

import javax.sql.DataSource;

/**
 * 监听器
 *
 * @author CH
 */
public interface DataSourceWatchdog extends Watchdog {
    /**
     * 注册bean
     *
     * @param dataSource dataSource
     */
    void register(DataSource dataSource);

}
