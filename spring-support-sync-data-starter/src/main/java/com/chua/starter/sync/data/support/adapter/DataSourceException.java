package com.chua.starter.sync.data.support.adapter;

/**
 * 数据源异常
 *
 * @author System
 * @since 2026/03/09
 */
public class DataSourceException extends RuntimeException {

    public DataSourceException(String message) {
        super(message);
    }

    public DataSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
