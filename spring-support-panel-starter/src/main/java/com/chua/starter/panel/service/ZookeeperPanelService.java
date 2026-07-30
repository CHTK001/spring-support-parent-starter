package com.chua.starter.panel.service;

import java.util.List;
import java.util.Map;

/**
 * ZooKeeper 面板服务。
 *
 * @author CH
 */
public interface ZookeeperPanelService {

    /**
     * 打开连接并返回连接标识。
     *
     * @param host 主机地址
     * @param port 端口
     * @param sessionTimeoutMs 会话超时（毫秒）
     * @return 连接标识
     */
    String open(String host, int port, int sessionTimeoutMs);

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     */
    void close(String connectionId);

    /**
     * 列出子节点。
     *
     * @param connectionId 连接标识
     * @param path 节点路径
     * @return 子节点列表
     */
    List<String> ls(String connectionId, String path);

    /**
     * 读取节点数据。
     *
     * @param connectionId 连接标识
     * @param path 节点路径
     * @return 节点数据
     */
    String get(String connectionId, String path);

    /**
     * 写入节点数据。
     *
     * @param connectionId 连接标识
     * @param path 节点路径
     * @param data 节点数据
     */
    void set(String connectionId, String path, String data);

    /**
     * 删除节点。
     *
     * @param connectionId 连接标识
     * @param path 节点路径
     * @param recursive 是否递归删除
     */
    void delete(String connectionId, String path, boolean recursive);

    /**
     * 查询节点元信息。
     *
     * @param connectionId 连接标识
     * @param path 节点路径
     * @return 元信息
     */
    Map<String, Object> stat(String connectionId, String path);

    /**
     * 判断节点是否存在。
     *
     * @param connectionId 连接标识
     * @param path 节点路径
     * @return 是否存在
     */
    boolean exists(String connectionId, String path);

    /**
     * 创建节点。
     *
     * @param connectionId 连接标识
     * @param path 节点路径
     * @param data 节点数据
     * @param mode 创建模式
     * @return 创建后的节点路径
     */
    String create(String connectionId, String path, String data, String mode);
}
