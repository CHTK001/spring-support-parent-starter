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
 * 通过消息队列统一封装 Socket 广播能力（plugin.队列.类型 可选
 * 本地/Kafka/RabbitMQ/MQTT），由 Socket-启动 的
 * {@code SocketQueueBridge} 订阅 socket:push topic 后广播到前端。
 * 队列 未启用时推送静默跳过，不影响主链路。
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

    /**
     * Socket 模块标识
    */
    public static final String MODULE = "monitor";

    /**
     * 告警事件名（前端 订阅("监控", "报警器") 接收）
    */
    public static final String EVENT_ALARM = "alarm";

    /**
     * 指标推送事件名（前端 订阅("监控", "指标") 接收）
    */
    public static final String EVENT_METRIC = "metric";

    /**
     * 采集任务执行状态事件名
    */
    public static final String EVENT_TASK = "task";

    /**
     * tshark 数据包实时推送事件名（前端 订阅("监控", "tshark-数据包") 接收）
     */
    public static final String EVENT_TSHARK_PACKET = "tshark-packet";

    /**
     * tshark 会话状态变更事件名（启动/停止/结束）
    */
    public static final String EVENT_TSHARK_SESSION = "tshark-session";

    /**
     * 主机实时延迟推送事件名（前端 订阅("监控", "主机-延迟") 接收；
     * 数据 = { 主机标识, 主机名称, 地址, 端口, 延迟ms, online }，延迟ms=-1 表示不可达
     */
    public static final String EVENT_HOST_DELAY = "host-delay";

    /**
     * 主机在线状态变更事件名（前端 订阅("监控", "主机-online") 接收；
     * 数据 = { 主机标识, 主机名称, online }
     */
    public static final String EVENT_HOST_ONLINE = "host-online";

    /**
     * 主机实时指标事件名（前端 订阅("监控", "host-metrics") 接收；
     * 数据 = { 主机标识, 主机名称, cpu, mem, disk }，单位 %
     */
    public static final String EVENT_HOST_METRICS = "host-metrics";

    /**
     * Socket队列bridge 订阅的统一 topic
    */
    public static final String SOCKET_PUSH_TOPIC = "socket:push";

    /**
     * 消息模板（队列 未启用时为空实现，推送静默跳过）
    */
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
     * @param monitorId 监控 标识（用于前端按目标过滤）
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
     * 广播 tshark 抓包数据包（前端 订阅("监控", "tshark-数据包") 接收）
     *
     * @param payload   数据包实体
     * @param sessionId 抓包会话 标识（用于前端按会话过滤）
     */
    public void pushTsharkPacket(Serializable payload, Long sessionId) {
        broadcast(EVENT_TSHARK_PACKET, payload, sessionId == null ? null : String.valueOf(sessionId));
    }

    /**
     * 广播 tshark 抓包会话状态变更（启动/停止/结束）
     *
     * @param payload   会话实体
     * @param sessionId 抓包会话 标识
     */
    public void pushTsharkSession(Serializable payload, Long sessionId) {
        broadcast(EVENT_TSHARK_SESSION, payload, sessionId == null ? null : String.valueOf(sessionId));
    }

    /**
     * 主机延迟推送载荷（Socket 推送共用）
     *
     * @param hostId   主机 标识
     * @param hostName 主机名
     * @param address  地址
     * @param port     端口
     * @param delayMs  延迟毫秒（-1 表示不可达）
     * @return 主机延迟的结果
     */
    public record HostDelay(Long hostId, String hostName, String address, Integer port, Long delayMs) {

        /**
         * 转为 Socket 广播用的 映射 载荷
         *
         * @return 载荷 映射
         */
        public Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>(6);
            payload.put("hostId", hostId);
            payload.put("hostName", hostName == null ? "" : hostName);
            payload.put("address", address == null ? "" : address);
            payload.put("port", port);
            payload.put("delayMs", delayMs);
            payload.put("online", delayMs != null && delayMs >= 0);
            return payload;
        }
    }

    /**
     * 广播主机实时延迟（前端 订阅("监控", "主机-延迟") 接收）
     *
     * @param hostId   主机 标识
     * @param hostName 主机名
     * @param address  地址
     * @param port     端口
     * @param delayMs  延迟毫秒（-1 表示不可达）
     */
    public void pushHostDelay(Long hostId, String hostName, String address, Integer port, Long delayMs) {
        broadcast(EVENT_HOST_DELAY, new HostDelay(hostId, hostName, address, port, delayMs).toMap(),
                hostId == null ? null : String.valueOf(hostId));
    }

    /**
     * 广播主机在线状态变更（前端 订阅("监控", "主机-online") 接收）
     *
     * @param hostId   主机 标识
     * @param hostName 主机名
     * @param online   是否在线
     */
    public void pushHostOnline(Long hostId, String hostName, boolean online) {
        Map<String, Object> payload = new LinkedHashMap<>(4);
        payload.put("hostId", hostId);
        payload.put("hostName", hostName == null ? "" : hostName);
        payload.put("online", online);
        /**
         * broadcast。
         * @param String.valueOf(hostId) 事件主机online
         * @param payload payload
         * @param String.valueOf(hostId 字符串.值的(主机标识
         */
        broadcast(EVENT_HOST_ONLINE, payload, hostId == null ? null : String.valueOf(hostId));
    }

    /**
     * 广播主机实时指标（CPU/内存/磁盘使用率 %，前端 订阅("监控", "host-metrics") 接收）
     *
     * @param hostId   主机 标识
     * @param hostName 主机名
     * @param cpu      CPU 使用率 %
     * @param mem      内存使用率 %
     * @param disk     磁盘使用率 %
     */
    public void pushHostMetrics(Long hostId, String hostName, Number cpu, Number mem, Number disk) {
        pushHostMetrics(hostId, hostName, cpu, mem, disk, null, null, null);
    }

    /**
     * 广播主机实时指标（含磁盘 IO 与软件，前端 订阅("监控", "host-metrics") 接收）
     *
     * @param hostId    主机 标识
     * @param hostName  主机名
     * @param cpu       CPU 使用率 %
     * @param mem       内存使用率 %
     * @param disk      磁盘使用率 %
     * @param diskRead  磁盘读取速率 KB/s，可为空
     * @param diskWrite 磁盘写入速率 KB/s，可为空
     * @param software  软件实例 JSON，可为空
     */
    public void pushHostMetrics(Long hostId, String hostName, Number cpu, Number mem, Number disk,
                                Number diskRead, Number diskWrite, String software) {
        Map<String, Object> payload = new LinkedHashMap<>(10);
        payload.put("hostId", hostId);
        payload.put("hostName", hostName == null ? "" : hostName);
        payload.put("cpu", cpu == null ? 0 : cpu);
        payload.put("mem", mem == null ? 0 : mem);
        payload.put("disk", disk == null ? 0 : disk);
        payload.put("diskRead", diskRead == null ? 0 : diskRead);
        payload.put("diskWrite", diskWrite == null ? 0 : diskWrite);
        if (software != null && !software.isBlank()) {
            payload.put("software", software);
        }
        broadcast(EVENT_HOST_METRICS, payload, hostId == null ? null : String.valueOf(hostId));
    }

    /**
     * 统一广播：经 队列 发送到 Socket:push topic（队列 未启用或发送异常时静默跳过）
     * <p>
     * 事件协议对齐前端 ws消息（包/核心 Sockettemplate）：
     * Socket队列bridge 收到后以 Socket.io 事件 "消息" 广播，载荷含 {module, 事件, 数据, 数据标识, 时间戳}，
     * 前端 Socket.订阅(module, 事件) 按 "module_事件" 分发。
     * </p>
     *
     * @param event  事件名
     * @param data   数据
     * @param dataId 数据 标识（可用于前端按维度过滤）
     */
    public void broadcast(String event, Object data, String dataId) {
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
     * 告警事件载荷（Socket 推送 / 告警触发与处理共用）
     *
     * @param monitorId   监控 标识，可为 空
     * @param monitorName 监控名称，允许 空
     * @param metricName  指标名，允许 空
     * @param alarmValue  实际值
     * @param threshold   阈值
     * @param level       级别 P0~P4，空 时按 P2 处理
     * @param status      状态 新/处理/FALSE_报警器，空 时按 新 处理
     */
    public record AlarmPayload(Long monitorId, String monitorName, String metricName,
                               double alarmValue, double threshold, String level, String status) {

        /**
         * 构造载荷
         *
         * @param monitorId   监控 标识
         * @param monitorName 监控名称
         * @param metricName  指标名
         * @param alarmValue  实际值
         * @param threshold   阈值
         * @param level       级别
         * @param status      状态
         * @return 载荷
         */
        public static AlarmPayload of(Long monitorId, String monitorName, String metricName,
                                      double alarmValue, double threshold, String level, String status) {
            return new AlarmPayload(monitorId, monitorName, metricName, alarmValue, threshold, level, status);
        }

        /**
         * 转为 Socket 广播用的 映射 载荷（对齐前端 ws消息 字段名）
         *
         * @return 载荷 映射
         */
        public Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>(8);
            payload.put("monitorId", monitorId);
            payload.put("monitorName", monitorName == null ? "" : monitorName);
            payload.put("metricName", metricName == null ? "" : metricName);
            payload.put("alarmValue", alarmValue);
            payload.put("threshold", threshold);
            payload.put("level", level == null ? "P2" : level);
            payload.put("status", status == null ? "NEW" : status);
            payload.put("triggerTime", java.time.LocalDateTime.now().toString());
            return payload;
        }
    }

    /**
     * 构造告警事件载荷（兼容旧调用，内部转为 record）
     *
     * @param monitorId   监控 标识
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
        return AlarmPayload.of(monitorId, monitorName, metricName, alarmValue, threshold, level, status).toMap();
    }
}
