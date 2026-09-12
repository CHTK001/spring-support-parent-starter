package com.chua.starter.monitor.support.event;

import com.chua.starter.monitor.support.service.alarm.AlarmEngineService;
import com.chua.starter.monitor.support.service.impl.MonitorPushService;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* 指标采集事件监听器
* <p>
* 通过 {@link EventListener} 订阅 {@link MetricCollectedEvent}，在采集主链路入库后
* 异步解耦执行：
* <ul>
*   <li>告警规则逐点评估（原有 AlarmEngineService 链路）</li>
*   <li>最新指标 Socket 实时推送（新增实时能力）</li>
* </ul>
* 事件由 监控调度器 采集成功后发布；监听器内异常只记日志，不阻断主链路。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricCollectedEventListener {

    /** 告警引擎 */
    private final AlarmEngineService alarmEngineService;

    /** 实时推送服务 */
    private final MonitorPushService monitorPushService;

    /**
    * 采集完成后的告警评估与实时推送
    *
    * @param event 采集完成事件
     */
    @EventListener
    public void onCollected(MetricCollectedEvent event) {
        CollectResult result = event.result();
        if (result == null || result.getPoints() == null || result.getPoints().isEmpty()) {
            return;
        }
        Long monitorId = result.getMonitor() != null ? result.getMonitor().getId() : null;
        String monitorName = result.getMonitor() != null ? result.getMonitor().getMonitorName() : null;
        LocalDateTime collectTime = result.getCollectTime() != null ? result.getCollectTime() : LocalDateTime.now();
        List<MetricPoint> points = result.getPoints();

        // 1) 告警规则逐点评估
        try {
            for (MetricPoint point : points) {
                if (monitorId != null && point.getValue() != null) {
                    alarmEngineService.evaluate(monitorId, monitorName, point.getMetricName(), point.getValue());
                }
            }
        } catch (Exception e) {
            log.warn("[event] 告警评估异常: {}", e.getMessage());
        }

 // 2) 最新指标实时推送（套接字 未启用时静默跳过）
        try {
            Map<String, Object> payload = new HashMap<>(8);
            payload.put("monitorId", monitorId);
            payload.put("monitorName", monitorName == null ? "" : monitorName);
            payload.put("collectTime", collectTime.toString());
            Map<String, Object> metrics = new HashMap<>(points.size());
            for (MetricPoint point : points) {
                if (point.getMetricName() != null && point.getValue() != null) {
                    metrics.put(point.getMetricName(), point.getValue());
                }
            }
            payload.put("metrics", metrics);
            monitorPushService.pushMetric(payload, monitorId);
        } catch (Exception e) {
            log.debug("[event] 指标实时推送跳过: {}", e.getMessage());
        }
    }
}
