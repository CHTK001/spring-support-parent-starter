// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
        if (event == null || event.hostId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>(10);
            payload.put("hostId", event.hostId());
            payload.put("address", event.address());
            payload.put("success", event.success());
            payload.put("openCount", event.openCount());
            payload.put("totalPorts", event.totalPorts());
            payload.put("elapsedSeconds", event.elapsedSeconds());
            payload.put("portsJson", event.portsJson());
            payload.put("scanTime", event.scanTime() == null ? null : event.scanTime().toString());
            payload.put("error", event.error());
            monitorPushService.broadcast("host-portscan", payload, String.valueOf(event.hostId()));
        } catch (Exception e) {
            log.debug("[event] 端口扫描推送跳过: {}", e.getMessage());
        }
    }
}