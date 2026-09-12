package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.lang.json.Json;
import com.chua.starter.queue.MessageTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 监控实时推送服务
 * <p>
 * 通过消息队列统一封装 Socket 广播能力（plugin.queue.type 可选
 * local/kafka/rabbitmq/mqtt），由 socket-starter 的
 * {@code SocketQueueBridge} 订阅 socket:push topic 后广播到前端。
 * queue 未启用时推送静默跳过，不影响主链路。
 * </p>
 *
 * <h3>事件协议（前端 socket.subscribe(module, event, handler) 接收）</h3>
 * <pre>
 * 告警事件：
 *   module = "monitor", event = "alarm"
 *   data   = { id, monitorId, monitorName, metricName, alarmValue, threshold,
 *              level, status, triggerTime }
 *
 * 指标推送：
 *   module = "monitor", event = "metric"
 *   data   = { monitorId, monitorName, metricName, value, unit?, collectTime, tags? }
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorPushService {

    /** Socket 模块标识 */
    public static final String MODULE = "monitor";

    /** 告警事件名（前端 subscribe("monitor", "alarm") 接收） */
    public static final String EVENT_ALARM = "alarm";

    /** 指标推送事件名（前端 subscribe("monitor", "metric") 接收） */
    public static final String EVENT_METRIC = "metric";

    /** 采集任务执行状态事件名 */
    public static final String EVENT_TASK = "task";

    /** tshark 数据包实时推送事件名（前端 subscribe("monitor", "tshark-packet") 接收） */
    public static final String EVENT_TSHARK_PACKET = "tshark-packet";

    /** tshark 会话状态变更事件名（启动/停止/结束） */
    public static final String EVENT_TSHARK_SESSION = "tshark-session";

    /** 主机实时延迟推送事件名（前端 subscribe("monitor", "host-delay") 接收；
     *  data = { hostId, hostName, address, port, delayMs, online }，delayMs=-1 表示不可达） */
    public static final String EVENT_HOST_DELAY = "host-delay";

    /** 主机在线状态变更事件名（前端 subscribe("monitor", "host-online") 接收；
     *  data = { hostId, hostName, online }） */
    public static final String EVENT_HOST_ONLINE = "host-online";

    /** SocketQueueBridge 订阅的统一 topic */
    public static final String SOCKET_PUSH_TOPIC = "socket:push";

    /** 消息模板（queue 未启用时为空实现，推送静默跳过） */
    private final ObjectProvider<MessageTemplate> messageTemplateProvider;

    /**
     * 广播告警事件
     *
     * @param payload 事件数据
     */
    public void pushAlarm(Map<String, Object> payload) {
        broadcast(EVENT_ALARM, payload, null);
    }

    /**
     * 广播指标更新
     *
     * @param payload   事件数据
     * @param monitorId 监控 ID（用于前端按目标过滤）
     */
    public void pushMetric(Map<String, Object> payload, Long monitorId) {
        broadcast(EVENT_METRIC, payload, monitorId == null ? null : String.valueOf(monitorId));
    }

    /**
     * 广播采集任务执行状态
     *
     * @param payload 事件数据
     */
    public void pushTask(Map<String, Object> payload) {
        broadcast(EVENT_TASK, payload, null);
    }

    /**
     * 广播 tshark 抓包数据包（前端 subscribe("monitor", "tshark-packet") 接收）
     *
     * @param payload   数据包实体
     * @param sessionId 抓包会话 ID（用于前端按会话过滤）
     */
    public void pushTsharkPacket(Serializable payload, Long sessionId) {
        broadcast(EVENT_TSHARK_PACKET, payload, sessionId == null ? null : String.valueOf(sessionId));
    }

    /**
     * 广播 tshark 抓包会话状态变更（启动/停止/结束）
     *
     * @param payload   会话实体
     * @param sessionId 抓包会话 ID
     */
    public void pushTsharkSession(Serializable payload, Long sessionId) {
        broadcast(EVENT_TSHARK_SESSION, payload, sessionId == null ? null : String.valueOf(sessionId));
    }

    /**
     * 广播主机实时延迟（前端 subscribe("monitor", "host-delay") 接收）
     *
     * @param hostId   主机 ID
     * @param hostName 主机名
     * @param address  地址
     * @param port     端口
     * @param delayMs  延迟毫秒（-1 表示不可达）
     */
    public void pushHostDelay(Long hostId, String hostName, String address, Integer port, Long delayMs) {
        Map<String, Object> payload = new LinkedHashMap<>(8);
        payload.put("hostId", hostId);
        payload.put("hostName", hostName == null ? "" : hostName);
        payload.put("address", address == null ? "" : address);
        payload.put("port", port);
        payload.put("delayMs", delayMs);
        payload.put("online", delayMs != null && delayMs >= 0);
        broadcast(EVENT_HOST_DELAY, payload, hostId == null ? null : String.valueOf(hostId));
    }

    /**
     * 广播主机在线状态变更（前端 subscribe("monitor", "host-online") 接收）
     *
     * @param hostId   主机 ID
     * @param hostName 主机名
     * @param online   是否在线
     */
    public void pushHostOnline(Long hostId, String hostName, boolean online) {
        Map<String, Object> payload = new LinkedHashMap<>(4);
        payload.put("hostId", hostId);
        payload.put("hostName", hostName == null ? "" : hostName);
        payload.put("online", online);
        broadcast(EVENT_HOST_ONLINE, payload, hostId == null ? null : String.valueOf(hostId));
    }

    /**
     * 统一广播：经 queue 发送到 socket:push topic（queue 未启用或发送异常时静默跳过）
     * <p>
     * 事件协议对齐前端 WsMessage（packages/core socketTemplate）：
     * SocketQueueBridge 收到后以 socket.io 事件 "message" 广播，载荷含 {module, event, data, dataId, timestamp}，
     * 前端 socket.subscribe(module, event) 按 "module_event" 分发。
     * </p>
     *
     * @param event  事件名
     * @param data   数据
     * @param dataId 数据 ID（可用于前端按维度过滤）
     */
    private void broadcast(String event, Object data, String dataId) {
        try {
            MessageTemplate template = messageTemplateProvider.getIfAvailable();
            if (template == null) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>(6);
            payload.put("module", MODULE);
            payload.put("event", event);
            payload.put("data", data);
            if (dataId != null) {
                payload.put("dataId", dataId);
            }
            payload.put("timestamp", System.currentTimeMillis());
            template.send(SOCKET_PUSH_TOPIC, Json.toJson(payload));
        } catch (Exception e) {
            log.debug("[push] 实时推送跳过 {}: {}", event, e.getMessage());
        }
    }

    /**
     * 构造告警事件载荷
     *
     * @param monitorId   监控 ID
     * @param monitorName 监控名称
     * @param metricName  指标名
     * @param alarmValue  实际值
     * @param threshold   阈值
     * @param level       级别
     * @param status      状态
     * @return 载荷
     */
    public static Map<String, Object> buildAlarmPayload(Long monitorId, String monitorName, String metricName,
                                                        double alarmValue, double threshold, String level, String status) {
        return Map.of(
                "monitorId", monitorId,
                "monitorName", monitorName == null ? "" : monitorName,
                "metricName", metricName == null ? "" : metricName,
                "alarmValue", alarmValue,
                "threshold", threshold,
                "level", level == null ? "P2" : level,
                "status", status == null ? "NEW" : status,
                "triggerTime", java.time.LocalDateTime.now().toString()
        );
    }
}
