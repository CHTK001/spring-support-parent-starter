package com.chua.starter.panel.service;

import java.util.List;
import java.util.Map;

/**
 * InfluxDB 3.x 面板服务。
 * 通过 HTTP API v3（SQL 查询）与 InfluxDB 3.x 交互，无需额外依赖。
 *
 * @author CH
 */
public interface InfluxV3PanelService {

    /**
     * 打开连接并返回连接标识。
     *
     * @param host 主机地址
     * @param port 端口
     * @param token Token
     * @param defaultDatabase 默认数据库
     * @return 连接标识
     */
    String open(String host, int port, String token, String defaultDatabase);

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     */
    void close(String connectionId);

    /**
     * 列出数据库。
     *
     * @param connectionId 连接标识
     * @return 数据库列表
     */
    List<String> listDatabases(String connectionId);

    /**
     * 列出指定数据库下的数据表。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @return 表列表
     */
    List<String> listTables(String connectionId, String database);

    /**
     * 执行 SQL 查询。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param sql SQL 语句
     * @return 查询结果
     */
    List<Map<String, Object>> query(String connectionId, String database, String sql);

    /**
     * 写入 Line Protocol 数据。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param lineProtocol Line Protocol 文本
     */
    void write(String connectionId, String database, String lineProtocol);

    /**
     * 查询健康状态。
     *
     * @param connectionId 连接标识
     * @return 健康状态
     */
    Map<String, Object> health(String connectionId);
}
