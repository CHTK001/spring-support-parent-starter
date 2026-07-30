package com.chua.starter.panel.service;

import java.util.List;
import java.util.Map;

/**
 * Kafka 面板服务。
 * 使用 Kafka AdminClient API 实现集群管理。
 *
 * @author CH
 */
public interface KafkaPanelService {

    /**
     * 打开连接并返回连接标识。
     *
     * @param host     主机地址
     * @param port     端口
     * @param username 用户名，可为空
     * @param password 密码，可为空
     * @param protocol 协议，可为空（默认 plaintext）
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
     * 查询集群健康信息。
     *
     * @param connectionId 连接标识
     * @return 健康信息
     */
    Map<String, Object> clusterHealth(String connectionId);

    /**
     * 查询 Broker 列表。
     *
     * @param connectionId 连接标识
     * @return broker 列表
     */
    List<Map<String, Object>> listBrokers(String connectionId);

    /**
     * 查询 Topic 列表。
     *
     * @param connectionId 连接标识
     * @return topic 列表
     */
    List<Map<String, Object>> listTopics(String connectionId);

    /**
     * 创建 Topic。
     *
     * @param connectionId     连接标识
     * @param topicName        topic 名称
     * @param numPartitions    分区数
     * @param replicationFactor 副本数
     * @return 创建结果
     */
    boolean createTopic(String connectionId, String topicName, int numPartitions, short replicationFactor);

    /**
     * 删除 Topic。
     *
     * @param connectionId 连接标识
     * @param topicName    topic 名称
     * @return 删除结果
     */
    boolean deleteTopic(String connectionId, String topicName);

    /**
     * 查询 Topic 详情（分区、偏移等）。
     *
     * @param connectionId 连接标识
     * @param topicName    topic 名称
     * @return topic 详情
     */
    Map<String, Object> topicDetail(String connectionId, String topicName);

    /**
     * 消费消息。
     *
     * @param connectionId 连接标识
     * @param topicName    topic 名称
     * @param partition    分区号，-1 表示所有分区
     * @param offset       偏移量，-1 表示最新
     * @param limit        最大返回条数
     * @return 消息列表
     */
    List<Map<String, Object>> consumeMessages(String connectionId, String topicName, int partition, long offset, int limit);

    /**
     * 查询消费者组列表。
     *
     * @param connectionId 连接标识
     * @return 消费者组列表
     */
    List<Map<String, Object>> listConsumerGroups(String connectionId);

    /**
     * 查询消费者组详情。
     *
     * @param connectionId 连接标识
     * @param groupId      组 ID
     * @return 消费者组详情
     */
    Map<String, Object> consumerGroupDetail(String connectionId, String groupId);

    /**
     * 查询 topic 各分区的 offset 范围（beginning / end / count）。
     *
     * @param connectionId 连接标识
     * @param topicName    topic 名称
     * @return 每个分区的 offset 范围列表
     */
    List<Map<String, Object>> topicOffsets(String connectionId, String topicName);

    /**
     * 重置消费者组 offset 到 earliest 或 latest。
     *
     * @param connectionId 连接标识
     * @param groupId      消费者组 ID
     * @param topicName    topic 名称
     * @param strategy     earliest 或 latest
     * @return 重置结果
     */
    boolean resetConsumerGroupOffset(String connectionId, String groupId, String topicName, String strategy);

    /**
     * 发送测试消息到 topic。
     *
     * @param connectionId 连接标识
     * @param topicName    topic 名称
     * @param key          消息 key
     * @param value        消息 value
     * @return 发送结果
     */
    boolean produceMessage(String connectionId, String topicName, String key, String value);
}
