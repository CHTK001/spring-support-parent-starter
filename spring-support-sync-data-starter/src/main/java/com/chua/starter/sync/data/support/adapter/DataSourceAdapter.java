package com.chua.starter.sync.data.support.adapter;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 数据源适配器接口
 *
 * @author System
 * @since 2026/03/09
 */
public interface DataSourceAdapter {

    /**
     * 连接数据源
     *
     * @param config 数据源配置
     * @throws DataSourceException 连接异常
     */
    void connect(DataSourceConfig config) throws DataSourceException;

    /**
     * 读取数据（流式）
     *
     * @param config 读取配置
     * @return 数据流
     */
    Stream<Map<String, Object>> read(ReadConfig config);

    /**
     * 写入数据（批量）
     *
     * @param records 记录列表
     * @param config 写入配置
     */
    void write(List<Map<String, Object>> records, WriteConfig config);

    /**
     * 测试连接
     *
     * @return 连接是否正常
     */
    boolean testConnection();

    /**
     * 关闭连接
     */
    void close();

    /**
     * 获取元数据
     *
     * @return 数据源元数据
     */
    DataSourceMetadata getMetadata();
}
