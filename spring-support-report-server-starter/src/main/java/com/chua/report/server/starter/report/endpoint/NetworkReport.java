package com.chua.report.server.starter.report.endpoint;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.json.Json;
import com.chua.redis.support.constant.RedisConstant;
import com.chua.report.client.starter.report.event.DiskEvent;
import com.chua.report.client.starter.report.event.NetworkEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.redis.support.service.TimeSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;

import java.util.List;

import static com.chua.redis.support.constant.RedisConstant.REDIS_SIMPLE_SERIES_PREFIX;
import static com.chua.redis.support.constant.RedisConstant.REDIS_TIME_SERIES_PREFIX;

/**
 * 网络上报
 * @author CH
 * @since 2024/9/13
 */
@SpringBootConfiguration
@RequiredArgsConstructor
public class NetworkReport {

    public static final String NAME = "network";
    public static final String LOG_INDEX_NAME_PREFIX = REDIS_TIME_SERIES_PREFIX + NAME + ":";

    private final TimeSeriesService timeSeriesService;
    private final SocketSessionTemplate socketSessionTemplate;

    /**
     * 处理NETWORK相关报告事件
     *
     * @param reportEvent 包含NETWORK报告数据的事件对象
     */
    @OnRouterEvent("network")
    @SuppressWarnings("ALL")
    public void report(ReportEvent<?> reportEvent) {
        // 将报告数据转换为DiskEvent对象
        List reportData = (List) reportEvent.getReportData();
        List<NetworkEvent> networkEvents = BeanUtils.copyPropertiesList(reportData, NetworkEvent.class);
        // 将NETWORK事件信息注册到Redis
        registerRedisTime(networkEvents, reportEvent);
        // 通过Socket.IO发送NETWORK事件信息
        reportToSocketIo(networkEvents, reportEvent);
    }

    /**
     * 通过Socket.IO报告NETWORK事件
     *
     * @param networkEvents    包含NETWORK监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void reportToSocketIo( List<NetworkEvent> networkEvents, ReportEvent<?> reportEvent) {
        // 计算事件ID数组
        String[] eventIds = reportEvent.eventIds();
        // 遍历事件ID数组，发送NETWORK事件信息
        for (String eventId : eventIds) {
            socketSessionTemplate.send(eventId, Json.toJSONString(networkEvents));
        }
    }

    /**
     * 将NETWORK事件信息注册到Redis
     *
     * @param networkEvents    包含NETWORK监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void registerRedisTime( List<NetworkEvent> networkEvents, ReportEvent<?> reportEvent) {
        for (NetworkEvent networkEvent : networkEvents) {
            timeSeriesService.save(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId()
                     + ":READ:" + networkEvent.getName()
                    , reportEvent.getTimestamp(),
                    networkEvent.getReadBytes(),
                    RedisConstant.DEFAULT_RETENTION_PERIOD_FOR_WEEK * 1000L);
            timeSeriesService.save(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId()
                     + ":WRITE:" + networkEvent.getName()
                    , reportEvent.getTimestamp(),
                    networkEvent.getReadBytes(),
                    RedisConstant.DEFAULT_RETENTION_PERIOD_FOR_WEEK * 1000L);
        }
    }

}
