package com.chua.report.server.starter.report.endpoint;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.DigestUtils;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.report.event.ServerEvent;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.redis.support.service.TimeSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;

import static com.chua.redis.support.constant.RedisConstant.REDIS_SIMPLE_SERIES_PREFIX;

/**
 * JVM上报
 * @author CH
 * @since 2024/9/13
 */
@SpringBootConfiguration
@RequiredArgsConstructor
public class ServerReport {

    public static final String NAME = "server";
    public static final String LOG_INDEX_NAME_PREFIX = REDIS_SIMPLE_SERIES_PREFIX + NAME + ":";

    private final SocketSessionTemplate socketSessionTemplate;
    private final TimeSeriesService timeSeriesService;

    /**
     * 处理JVM相关报告事件
     *
     * @param reportEvent 包含JVM报告数据的事件对象
     */
    @OnRouterEvent("server")
    public void report(ReportEvent<?> reportEvent) {
        // 将报告数据转换为JvmEvent对象
        ServerEvent serverEvent = BeanUtils.copyProperties(reportEvent.getReportData(), ServerEvent.class);
        // 将JVM事件信息注册到Redis
        registerRedisTime(serverEvent, reportEvent);
        // 通过Socket.IO发送JVM事件信息
        reportToSocketIo(serverEvent, reportEvent);
    }

    /**
     * 通过Socket.IO报告JVM事件
     *
     * @param serverEvent    包含JVM监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void reportToSocketIo(ServerEvent serverEvent, ReportEvent<?> reportEvent) {
        // 计算事件ID数组
        String[] eventIds = reportEvent.eventIds();
        // 遍历事件ID数组，发送JVM事件信息
        for (String eventId : eventIds) {
            socketSessionTemplate.send(eventId, Json.toJSONString(serverEvent));
        }
    }

    /**
     * 将JVM事件信息注册到Redis
     *
     * @param serverEvent    包含JVM监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void registerRedisTime(ServerEvent serverEvent, ReportEvent<?> reportEvent) {
        // 将JVM事件信息以字符串形式保存到Redis
        timeSeriesService.hSet(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId(),
                DigestUtils.md5Hex(
                        serverEvent.getSourceHost() + serverEvent.getSourcePort()
                        + serverEvent.getTargetHost() + serverEvent.getTargetPort()
                ),
                Json.toJSONString(serverEvent));
    }

}
