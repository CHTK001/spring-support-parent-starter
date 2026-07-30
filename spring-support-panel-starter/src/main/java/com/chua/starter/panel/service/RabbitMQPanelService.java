package com.chua.starter.panel.service;

import java.util.List;
import java.util.Map;

/**
 * RabbitMQ 面板服务。
 * 使用 RabbitMQ HTTP Management API 实现管理操作。
 *
 * @author CH
 */
public interface RabbitMQPanelService {

    /**
     * 打开连接并返回连接标识。
     *
     * @param host         AMQP 主机地址
     * @param port         AMQP 端口（5672）
     * @param username     用户名
     * @param password     密码
     * @param managementPort 管理端口（默认 15672）
     * @return 连接标识
     */
    String open(String host, int port, String username, String password, int managementPort);

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     */
    void close(String connectionId);

    /**
     * 查询节点概览信息。
     *
     * @param connectionId 连接标识
     * @return 节点概览
     */
    Map<String, Object> overview(String connectionId);

    /**
     * 查询节点列表。
     *
     * @param connectionId 连接标识
     * @return 节点列表
     */
    List<Map<String, Object>> listNodes(String connectionId);

    /**
     * 查询队列列表。
     *
     * @param connectionId 连接标识
     * @return 队列列表
     */
    List<Map<String, Object>> listQueues(String connectionId);

    /**
     * 查询队列详情。
     *
     * @param connectionId 连接标识
     * @param queueName    队列名称
     * @return 队列详情
     */
    Map<String, Object> queueDetail(String connectionId, String queueName);

    /**
     * 查询交换机列表。
     *
     * @param connectionId 连接标识
     * @return 交换机列表
     */
    List<Map<String, Object>> listExchanges(String connectionId);

    /**
     * 查询交换机详情。
     *
     * @param connectionId 连接标识
     * @param exchangeName 交换机名称
     * @return 交换机详情
     */
    Map<String, Object> exchangeDetail(String connectionId, String exchangeName);

    /**
     * 查询绑定关系。
     *
     * @param connectionId 连接标识
     * @return 绑定列表
     */
    List<Map<String, Object>> listBindings(String connectionId);

    /**
     * 查询连接列表。
     *
     * @param connectionId 连接标识
     * @return 连接列表
     */
    List<Map<String, Object>> listConnections(String connectionId);

    /**
     * 查询 Channel 列表。
     *
     * @param connectionId 连接标识
     * @return Channel 列表
     */
    List<Map<String, Object>> listChannels(String connectionId);

    /**
     * 发布消息到交换机。
     *
     * @param connectionId 连接标识
     * @param exchangeName 交换机名称（空串表示默认交换机）
     * @param routingKey   路由键
     * @param payload      消息内容
     * @param properties   消息属性（可选）
     * @return 发布结果
     */
    boolean publishMessage(String connectionId, String exchangeName, String routingKey,
                           String payload, Map<String, Object> properties);

    /**
     * 从队列消费消息。
     *
     * @param connectionId 连接标识
     * @param queueName    队列名称
     * @param count        最大条数
     * @param ackMode      确认模式：ack_requeue_true / ack_requeue_false / reject_requeue_true / reject_requeue_false
     * @return 消息列表
     */
    List<Map<String, Object>> getMessages(String connectionId, String queueName, int count, String ackMode);

    /**
     * 清空队列。
     *
     * @param connectionId 连接标识
     * @param queueName    队列名称
     * @return 清空结果
     */
    boolean purgeQueue(String connectionId, String queueName);

    /**
     * 删除队列。
     *
     * @param connectionId 连接标识
     * @param queueName    队列名称
     * @return 删除结果
     */
    boolean deleteQueue(String connectionId, String queueName);

    /**
     * 创建队列。
     *
     * @param connectionId 连接标识
     * @param queueName    队列名称
     * @param durable      是否持久化
     * @param autoDelete   是否自动删除
     * @param arguments    额外参数（可选）
     * @return 创建结果
     */
    boolean createQueue(String connectionId, String queueName, boolean durable, boolean autoDelete, Map<String, Object> arguments);

    /**
     * 创建交换机。
     *
     * @param connectionId 连接标识
     * @param exchangeName 交换机名称
     * @param type         交换机类型（direct/fanout/headers/topic）
     * @param durable      是否持久化
     * @param autoDelete   是否自动删除
     * @param internal     是否内部交换机
     * @param arguments    额外参数（可选）
     * @return 创建结果
     */
    boolean createExchange(String connectionId, String exchangeName, String type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments);

    /**
     * 删除交换机。
     *
     * @param connectionId 连接标识
     * @param exchangeName 交换机名称
     * @return 删除结果
     */
    boolean deleteExchange(String connectionId, String exchangeName);

    /**
     * 创建绑定（交换机 → 队列）。
     *
     * @param connectionId 连接标识
     * @param exchangeName 交换机名称
     * @param queueName    队列名称
     * @param routingKey   路由键
     * @param arguments    额外参数（可选）
     * @return 创建结果
     */
    boolean createBinding(String connectionId, String exchangeName, String queueName, String routingKey, Map<String, Object> arguments);

    // ─── 用户管理 ────────────────────────────────────────────────────────

    /** 查询用户列表 */
    List<Map<String, Object>> listUsers(String connectionId);

    /** 查询用户详情 */
    Map<String, Object> userDetail(String connectionId, String username);

    /** 创建/更新用户 */
    boolean createUser(String connectionId, String username, String password, String tags);

    /** 删除用户 */
    boolean deleteUser(String connectionId, String username);

    /** 查询用户权限 */
    List<Map<String, Object>> getUserPermissions(String connectionId, String username);

    /** 设置用户权限 */
    boolean setUserPermissions(String connectionId, String username, String vhost, String configure, String write, String read);

    // ─── 虚拟主机管理 ────────────────────────────────────────────────────

    /** 查询虚拟主机列表 */
    List<Map<String, Object>> listVhosts(String connectionId);

    /** 查询虚拟主机详情 */
    Map<String, Object> vhostDetail(String connectionId, String vhostName);

    /** 创建虚拟主机 */
    boolean createVhost(String connectionId, String vhostName);

    /** 删除虚拟主机 */
    boolean deleteVhost(String connectionId, String vhostName);
}
