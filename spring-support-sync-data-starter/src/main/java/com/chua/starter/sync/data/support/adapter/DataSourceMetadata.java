package com.chua.starter.sync.data.support.adapter;

import lombok.Data;

import java.util.List;

/**
 * 数据源元数据
 *
 * @author System
 * @since 2026/03/09
 */
@Data
public class DataSourceMetadata {

    /**
     * 数据源类型
     */
    private String type;

    /**
     * 数据源名称
     */
    private String name;

    /**
     * 版本信息
     */
    private String version;

    /**
     * 表/集合列表
     */
    private List<String> tables;

    /**
     * 是否支持事务
     */
    private boolean supportsTransaction;

    /**
     * 数据库类型
     */
    private String databaseType;

    /**
     * 数据库版本
     */
    private String databaseVersion;

    /**
     * 驱动名称
     */
    private String driverName;

    /**
     * 驱动版本
     */
    private String driverVersion;
}
