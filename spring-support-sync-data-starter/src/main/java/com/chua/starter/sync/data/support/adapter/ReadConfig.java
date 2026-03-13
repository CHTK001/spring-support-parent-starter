package com.chua.starter.sync.data.support.adapter;

import lombok.Data;

/**
 * 读取配置类
 *
 * @author System
 * @since 2026/03/09
 */
@Data
public class ReadConfig {

    /**
     * SQL查询语句（JDBC）
     */
    private String sql;

    /**
     * 集合名称（MongoDB）
     */
    private String collection;

    /**
     * 过滤条件（MongoDB）
     */
    private String filter;

    /**
     * 主题名称（Kafka）
     */
    private String topic;

    /**
     * 文件路径（File）
     */
    private String filePath;

    /**
     * 文件类型（CSV/EXCEL/JSON）
     */
    private String fileType;

    /**
     * 批次大小
     */
    private int batchSize = 1000;

    /**
     * 获取大小（JDBC fetchSize）
     */
    private int fetchSize = 1000;
}
