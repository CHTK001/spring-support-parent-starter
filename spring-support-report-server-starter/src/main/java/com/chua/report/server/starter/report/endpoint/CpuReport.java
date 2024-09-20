package com.chua.report.server.starter.report.endpoint;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.json.Json;
import com.chua.redis.support.constant.RedisConstant;
import com.chua.report.client.starter.report.event.CpuEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.redis.support.service.TimeSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static com.chua.redis.support.constant.RedisConstant.REDIS_TIME_SERIES_PREFIX;

/**
 * CPU上报
 * @author CH
 * @since 2024/9/13
 */
@SpringBootConfiguration
@RequiredArgsConstructor
public class CpuReport {

    public static final String NAME = "cpu";
    public static final String LOG_INDEX_NAME_PREFIX = REDIS_TIME_SERIES_PREFIX + NAME + ":";

    private final TimeSeriesService timeSeriesService;
    private final StringRedisTemplate stringRedisTemplate;
    private final SocketSessionTemplate socketSessionTemplate;

    /**
     * 处理JVM相关报告事件
     *
     * @param reportEvent 包含JVM报告数据的事件对象
     */
    @OnRouterEvent("cpu")
    public void report(ReportEvent<?> reportEvent) {
        // 将报告数据转换为JvmEvent对象
        CpuEvent cpuEvent = BeanUtils.copyProperties(reportEvent.getReportData(), CpuEvent.class);
        // 将CPU事件信息注册到Redis
        registerRedisTime(cpuEvent, reportEvent);
        // 通过Socket.IO发送CPU事件信息
        reportToSocketIo(cpuEvent, reportEvent);
    }

    /**
     * 通过Socket.IO报告CPU事件
     *
     * @param cpuEvent    包含CPU监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void reportToSocketIo(CpuEvent cpuEvent, ReportEvent<?> reportEvent) {
        // 计算事件ID数组
        String[] eventIds = reportEvent.eventIds();
        // 遍历事件ID数组，发送CPU事件信息
        for (String eventId : eventIds) {
            socketSessionTemplate.send(eventId, Json.toJSONString(cpuEvent));
        }
    }

    /**
     * 将CPU事件信息注册到Redis
     *
     * @param cpuEvent    包含CPU监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void registerRedisTime(CpuEvent cpuEvent, ReportEvent<?> reportEvent) {
        // 将CPU事件信息以字符串形式保存到Redis
        timeSeriesService.save(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId(), reportEvent.getTimestamp(), 100 - cpuEvent.getFree(), RedisConstant.DEFAULT_RETENTION_PERIOD_FOR_WEEK * 1000L);
    }

}
