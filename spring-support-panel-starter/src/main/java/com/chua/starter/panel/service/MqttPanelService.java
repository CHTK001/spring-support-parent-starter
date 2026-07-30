package com.chua.starter.panel.service;

import java.util.List;
import java.util.Map;

/**
 * MQTT 面板服务。
 * 使用 Eclipse Paho 客户端直接连接 MQTT Broker。
 *
 * @author CH
 */
public interface MqttPanelService {

    /**
     * 连接 MQTT Broker 并返回连接标识。
     *
     * @param host     Broker 地址
     * @param port     端口（默认 1883）
     * @param username 用户名（可选）
     * @param password 密码（可选）
     * @param clientId 客户端 ID（可选，为空则自动生成）
     * @return 连接标识
     */
    String open(String host, int port, String username, String password, String clientId);

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     */
    void close(String connectionId);

    /**
     * 获取 Broker 信息。
     *
     * @param connectionId 连接标识
     * @return Broker 信息（连接状态、订阅数、消息统计）
     */
    Map<String, Object> brokerInfo(String connectionId);

    /**
     * 发布消息。
     *
     * @param connectionId 连接标识
     * @param topic        主题
     * @param payload      消息内容
     * @param qos          QoS 等级（0/1/2）
     * @return 发布结果
     */
    boolean publish(String connectionId, String topic, String payload, int qos);

    /**
     * 订阅主题。
     *
     * @param connectionId 连接标识
     * @param topic        主题（支持通配符 +/#）
     * @param qos          QoS 等级
     * @return 订阅结果
     */
    boolean subscribe(String connectionId, String topic, int qos);

    /**
     * 取消订阅。
     *
     * @param connectionId 连接标识
     * @param topic        主题
     * @return 取消结果
     */
    boolean unsubscribe(String connectionId, String topic);

    /**
     * 查询当前订阅列表。
     *
     * @param connectionId 连接标识
     * @return 订阅列表
     */
    List<Map<String, Object>> listSubscriptions(String connectionId);

    /**
     * 查询消息历史（服务端缓存）。
     *
     * @param connectionId 连接标识
     * @param topic        主题过滤（可选，为空则返回全部）
     * @param limit        最大条数
     * @return 消息列表
     */
    List<Map<String, Object>> messages(String connectionId, String topic, int limit);
}
