package com.chua.starter.sync.data.support.adapter;

import lombok.Data;

import java.util.Map;

/**
 * 数据源配置类
 *
 * @author System
 * @since 2026/03/09
 */
@Data
public class DataSourceConfig {

    /**
     * 数据源类型
     */
    private String type;

    /**
     * 连接URL
     */
    private String url;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 驱动类名
     */
    private String driverClassName;

    /**
     * 其他配置属性
     */
    private Map<String, Object> properties;

    /**
     * 文件路径（用于文件型数据源）
     */
    private String filePath;

    /**
     * 最大连接池大小
     */
    private Integer maxPoolSize;

    /**
     * 最小空闲连接数
     */
    private Integer minIdle;

    /**
     * 分组ID
     */
    private String groupId;

    /**
     * 数据库名称
     */
    private String database;
}
