package com.chua.report.server.starter.report.endpoint;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.json.Json;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.socketio.support.session.SocketSessionTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;

import java.text.MessageFormat;

/**
 * 上线
 * @author CH
 * @since 2024/12/25
 */
@SpringBootConfiguration
@RequiredArgsConstructor
public class OfflineReport {
    private final SocketSessionTemplate socketSessionTemplate;

    /**
     * 处理下线相关报告事件
     *
     * @param reportEvent 包含JVM报告数据的事件对象
     */
    @OnRouterEvent("offline")
    public void report(ReportEvent<?> reportEvent) {
        // 计算事件ID数组
        socketSessionTemplate.send("offline",  """
            {"message": "%s:%s设备下线"}
            """.formatted(reportEvent.getApplicationHost(), reportEvent.getApplicationPort()));
    }
}
