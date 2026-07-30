package com.chua.starter.panel.service;

import com.chua.starter.panel.model.*;

import java.util.List;

/**
 * Redis 面板服务。
 *
 * @author CH
 */
public interface RedisPanelService {

    /**
     * 打开 Redis 连接。
     *
     * @param host 主机地址
     * @param port 端口
     * @param password 密码
     * @param db 数据库序号
     * @return 连接标识
     */
    String open(String host, int port, String password, int db);

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     */
    void close(String connectionId);

    /**
     * 查询服务器信息。
     *
     * @param connectionId 连接标识
     * @return 服务器信息
     */
    PanelRedisInfoView info(String connectionId);

    /**
     * 切换数据库。
     *
     * @param connectionId 连接标识
     * @param db 数据库序号
     */
    void selectDb(String connectionId, int db);

    /**
     * 扫描 key 列表。
     *
     * @param connectionId 连接标识
     * @param pattern 匹配模式（支持 *、?）
     * @param count 单次扫描数量
     * @return key 列表
     */
    List<PanelRedisKeyView> scan(String connectionId, String pattern, int count);

    /**
     * 获取 key 的值。
     *
     * @param connectionId 连接标识
     * @param key key 名称
     * @return 值视图
     */
    PanelRedisValueView get(String connectionId, String key);

    /**
     * 设置字符串值。
     *
     * @param connectionId 连接标识
     * @param request 设置参数
     */
    void set(String connectionId, PanelRedisSetRequest request);

    /**
     * 删除 key。
     *
     * @param connectionId 连接标识
     * @param keys key 列表
     * @return 删除数量
     */
    long del(String connectionId, String... keys);

    /**
     * 设置过期时间。
     *
     * @param connectionId 连接标识
     * @param key key 名称
     * @param seconds TTL 秒数
     * @return 是否成功
     */
    boolean expire(String connectionId, String key, long seconds);

    /**
     * 执行原始命令。
     *
     * @param connectionId 连接标识
     * @param command 命令文本
     * @return 执行结果
     */
    Object execute(String connectionId, String command);
}
