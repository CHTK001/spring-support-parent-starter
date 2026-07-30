package com.chua.starter.panel.service;

import java.util.List;
import java.util.Map;

/**
 * MongoDB 面板服务。
 *
 * @author CH
 */
public interface MongoPanelService {

    /**
     * 打开 MongoDB 连接并返回连接标识。
     *
     * @param host 主机地址
     * @param port 端口
     * @param username 用户名
     * @param password 密码
     * @param authDatabase 认证库
     * @return 连接标识
     */
    String open(String host, int port, String username, String password, String authDatabase);

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
     * 列出指定数据库的集合。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @return 集合列表
     */
    List<String> listCollections(String connectionId, String database);

    /**
     * 查询文档（支持 filter/sort/limit）。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @param filterJson 过滤条件 JSON
     * @param sortJson 排序条件 JSON
     * @param limit 返回条数
     * @param skip 偏移量
     * @return 文档列表
     */
    List<Map<String, Object>> find(String connectionId, String database, String collection,
                                   String filterJson, String sortJson, int limit, int skip);

    /**
     * 插入单个文档。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @param documentJson 文档 JSON
     * @return 插入结果
     */
    Map<String, Object> insertOne(String connectionId, String database, String collection, String documentJson);

    /**
     * 更新单个文档。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @param filterJson 过滤条件 JSON
     * @param updateJson 更新 JSON
     * @return 影响条数
     */
    long updateOne(String connectionId, String database, String collection, String filterJson, String updateJson);

    /**
     * 删除单个文档。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @param filterJson 过滤条件 JSON
     * @return 删除条数
     */
    long deleteOne(String connectionId, String database, String collection, String filterJson);

    /**
     * 聚合查询。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @param pipelineJson 聚合管道 JSON
     * @return 结果列表
     */
    List<Map<String, Object>> aggregate(String connectionId, String database, String collection, String pipelineJson);

    /**
     * 创建索引。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @param keysJson 索引键 JSON
     * @param unique 是否唯一索引
     * @return 索引名称
     */
    String createIndex(String connectionId, String database, String collection, String keysJson, boolean unique);

    /**
     * 删除集合。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     */
    void dropCollection(String connectionId, String database, String collection);

    /**
     * 查询集合统计信息。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param collection 集合名称
     * @return 统计信息
     */
    Map<String, Object> stats(String connectionId, String database, String collection);

    /**
     * 查询数据库统计信息。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @return 统计信息
     */
    Map<String, Object> dbStats(String connectionId, String database);

    /**
     * 执行原始命令。
     *
     * @param connectionId 连接标识
     * @param database 数据库名称
     * @param commandJson 命令 JSON
     * @return 执行结果
     */
    Object executeCommand(String connectionId, String database, String commandJson);
}
