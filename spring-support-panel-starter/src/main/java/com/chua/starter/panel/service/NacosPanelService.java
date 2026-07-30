package com.chua.starter.panel.service;

import java.util.Map;

/**
 * Nacos 面板服务。
 * 通过 HTTP OpenAPI 与 Nacos 交互，支持命名空间与配置管理。
 *
 * @author CH
 */
public interface NacosPanelService {

    /**
     * 打开连接并返回连接标识。
     *
     * @param host 主机地址
     * @param port 端口
     * @param username 用户名，可为空
     * @param password 密码，可为空
     * @param protocol 协议，可为空（默认 http）
     * @return 连接标识
     */
    String open(String host, int port, String username, String password, String protocol);

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     */
    void close(String connectionId);

    /**
     * 查询命名空间列表。
     *
     * @param connectionId 连接标识
     * @return 命名空间数据
     */
    Map<String, Object> namespaces(String connectionId);

    /**
     * 创建命名空间。
     *
     * @param connectionId 连接标识
     * @param namespaceId 命名空间 ID
     * @param namespaceName 命名空间名称
     * @param namespaceDesc 命名空间描述
     * @return 创建结果
     */
    boolean createNamespace(String connectionId, String namespaceId, String namespaceName, String namespaceDesc);

    /**
     * 删除命名空间。
     *
     * @param connectionId 连接标识
     * @param namespaceId 命名空间 ID
     * @return 删除结果
     */
    boolean deleteNamespace(String connectionId, String namespaceId);

    /**
     * 查询配置列表。
     *
     * @param connectionId 连接标识
     * @param tenant 命名空间
     * @param dataId dataId
     * @param group group
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @return 配置分页数据
     */
    Map<String, Object> listConfigs(
            String connectionId,
            String tenant,
            String dataId,
            String group,
            int pageNo,
            int pageSize);

    /**
     * 查询单个配置内容。
     *
     * @param connectionId 连接标识
     * @param tenant 命名空间
     * @param dataId dataId
     * @param group group
     * @return 配置详情
     */
    Map<String, Object> getConfig(String connectionId, String tenant, String dataId, String group);

    /**
     * 发布配置。
     *
     * @param connectionId 连接标识
     * @param tenant 命名空间
     * @param dataId dataId
     * @param group group
     * @param content 配置内容
     * @param type 配置类型，可为空
     * @return 发布结果
     */
    boolean publishConfig(
            String connectionId,
            String tenant,
            String dataId,
            String group,
            String content,
            String type);

    /**
     * 删除配置。
     *
     * @param connectionId 连接标识
     * @param tenant 命名空间
     * @param dataId dataId
     * @param group group
     * @return 删除结果
     */
    boolean deleteConfig(String connectionId, String tenant, String dataId, String group);

    /**
     * 克隆配置（读取源配置后发布到目标位置）。
     *
     * @param connectionId 连接标识
     * @param sourceTenant 源命名空间
     * @param sourceDataId 源 dataId
     * @param sourceGroup 源 group
     * @param targetTenant 目标命名空间
     * @param targetDataId 目标 dataId
     * @param targetGroup 目标 group
     * @param type 配置类型
     * @return 克隆结果
     */
    boolean cloneConfig(
            String connectionId,
            String sourceTenant,
            String sourceDataId,
            String sourceGroup,
            String targetTenant,
            String targetDataId,
            String targetGroup,
            String type);

    /**
     * 查询配置历史版本列表。
     *
     * @param connectionId 连接标识
     * @param tenant 命名空间
     * @param dataId dataId
     * @param group group
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @return 历史分页结果
     */
    Map<String, Object> listConfigHistory(
            String connectionId,
            String tenant,
            String dataId,
            String group,
            int pageNo,
            int pageSize);

    /**
     * 根据历史版本 ID 查询历史详情。
     *
     * @param connectionId 连接标识
     * @param tenant 命名空间
     * @param dataId dataId
     * @param group group
     * @param historyId 历史版本 ID
     * @return 历史详情
     */
    Map<String, Object> getHistoryDetail(
            String connectionId,
            String tenant,
            String dataId,
            String group,
            String historyId);

    /**
     * 回滚到历史版本。
     *
     * @param connectionId 连接标识
     * @param tenant 命名空间
     * @param dataId dataId
     * @param group group
     * @param historyId 历史版本 ID
     * @param type 配置类型
     * @return 回滚结果
     */
    boolean rollbackToHistory(
            String connectionId,
            String tenant,
            String dataId,
            String group,
            String historyId,
            String type);
}
