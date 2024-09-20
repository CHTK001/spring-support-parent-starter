package com.chua.report.server.starter.report.endpoint;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.json.Json;
import com.chua.report.client.starter.report.event.JvmEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.report.event.SysEvent;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.redis.support.service.TimeSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static com.chua.redis.support.constant.RedisConstant.REDIS_SIMPLE_SERIES_PREFIX;

/**
 * 系统信息上报
 * @author CH
 * @since 2024/9/13
 */
@SpringBootConfiguration
@RequiredArgsConstructor
public class SysReport {

    public static final String NAME = "sys";
    public static final String LOG_INDEX_NAME_PREFIX = REDIS_SIMPLE_SERIES_PREFIX + NAME + ":";

    private final TimeSeriesService timeSeriesService;
    private final SocketSessionTemplate socketSessionTemplate;

    /**
     * 处理JVM相关报告事件
     *
     * @param reportEvent 包含JVM报告数据的事件对象
     */
    @OnRouterEvent("sys")
    public void report(ReportEvent<?> reportEvent) {
        // 将报告数据转换为JvmEvent对象
        SysEvent sysEvent = BeanUtils.copyProperties(reportEvent.getReportData(), SysEvent.class);
        // 将SYS事件信息注册到Redis
        registerRedisTime(sysEvent, reportEvent);
        // 通过Socket.IO发送SYS事件信息
        reportToSocketIo(sysEvent, reportEvent);
    }

    /**
     * 通过Socket.IO报告SYS事件
     *
     * @param sysEvent    包含SYS监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void reportToSocketIo(SysEvent sysEvent, ReportEvent<?> reportEvent) {
        // 计算事件ID数组
        String[] eventIds = reportEvent.eventIds();
        // 遍历事件ID数组，发送SYS事件信息
        for (String eventId : eventIds) {
            socketSessionTemplate.send(eventId, Json.toJSONString(sysEvent));
        }
    }

    /**
     * 将SYS事件信息注册到Redis
     *
     * @param sysEvent    包含SYS监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void registerRedisTime(SysEvent sysEvent, ReportEvent<?> reportEvent) {
        // 将SYS事件信息以字符串形式保存到Redis
        timeSeriesService.set(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId(), JSON.toJSONString(sysEvent));
    }

}
