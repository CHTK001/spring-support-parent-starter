package com.chua.starter.sync.data.support.adapter;

import lombok.Data;

/**
 * 写入配置类
 *
 * @author System
 * @since 2026/03/09
 */
@Data
public class WriteConfig {

    /**
     * 表名（JDBC）
     */
    private String tableName;

    /**
     * 集合名称（MongoDB）
     */
    private String collection;

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
     * 是否批量写入
     */
    private boolean batchWrite = true;

    /**
     * 批次大小
     */
    private int batchSize = 1000;
}
