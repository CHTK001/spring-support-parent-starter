package com.chua.report.server.starter.report.endpoint;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.json.Json;
import com.chua.report.client.starter.report.event.JvmEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
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
public class JvmReport {

    public static final String NAME = "jvm";
    public static final String LOG_INDEX_NAME_PREFIX = REDIS_SIMPLE_SERIES_PREFIX + NAME + ":";

    private final SocketSessionTemplate socketSessionTemplate;
    private final TimeSeriesService timeSeriesService;

    /**
     * 处理JVM相关报告事件
     *
     * @param reportEvent 包含JVM报告数据的事件对象
     */
    @OnRouterEvent("jvm")
    public void report(ReportEvent<?> reportEvent) {
        // 将报告数据转换为JvmEvent对象
        JvmEvent jvmEvent = BeanUtils.copyProperties(reportEvent.getReportData(), JvmEvent.class);
        // 将JVM事件信息注册到Redis
        registerRedisTime(jvmEvent, reportEvent);
        // 通过Socket.IO发送JVM事件信息
        reportToSocketIo(jvmEvent, reportEvent);
    }

    /**
     * 通过Socket.IO报告JVM事件
     *
     * @param jvmEvent    包含JVM监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void reportToSocketIo(JvmEvent jvmEvent, ReportEvent<?> reportEvent) {
        // 计算事件ID数组
        String[] eventIds = reportEvent.eventIds();
        // 遍历事件ID数组，发送JVM事件信息
        for (String eventId : eventIds) {
            socketSessionTemplate.send(eventId, Json.toJSONString(jvmEvent));
        }
    }

    /**
     * 将JVM事件信息注册到Redis
     *
     * @param jvmEvent    包含JVM监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void registerRedisTime(JvmEvent jvmEvent, ReportEvent<?> reportEvent) {
        // 将JVM事件信息以字符串形式保存到Redis
        timeSeriesService.put(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId(), Json.toJSONString(jvmEvent));
    }

}
