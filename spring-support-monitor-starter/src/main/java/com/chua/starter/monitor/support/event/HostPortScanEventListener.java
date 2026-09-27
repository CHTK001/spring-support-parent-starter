package com.chua.starter.monitor.support.event;

import com.chua.starter.monitor.support.service.impl.MonitorPushService;
import com.chua.starter.server.support.event.HostPortScanEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 主机端口扫描事件桥接
 * <p>
 * 端口扫描在 server 模块后台异步执行，完成后发布 {@link HostPortScanEvent}；
 * 本监听器把它转发为 Socket {@code host-portscan} 推给前端，前端据此实时更新端口字段，
 * 不阻塞请求、也不轮询。监听器内异常只记日志，不影响扫描主链路。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HostPortScanEventListener {

    /**
     * 实时推送服务
     */
    private final MonitorPushService monitorPushService;

    /**
     * 扫描完成后转发 Socket
     *
     * @param event 端口扫描事件
     */
    @EventListener
    public void onPortScan(HostPortScanEvent event) {
        if (event == null || event.getHostId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>(10);
            payload.put("hostId", event.getHostId());
            payload.put("address", event.getAddress());
            payload.put("success", event.isSuccess());
            payload.put("openCount", event.getOpenCount());
            payload.put("totalPorts", event.getTotalPorts());
            payload.put("elapsedSeconds", event.getElapsedSeconds());
            payload.put("portsJson", event.getPortsJson());
            payload.put("scanTime", event.getScanTime() == null ? null : event.getScanTime().toString());
            payload.put("error", event.getError());
            monitorPushService.broadcast("host-portscan", payload, String.valueOf(event.getHostId()));
        } catch (Exception e) {
            log.debug("[event] 端口扫描推送跳过: {}", e.getMessage());
        }
    }
}
