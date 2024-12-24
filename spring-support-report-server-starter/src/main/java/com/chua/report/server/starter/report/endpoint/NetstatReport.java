package com.chua.report.server.starter.report.endpoint;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.json.Json;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.report.event.StateEvent;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.redis.support.service.TimeSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;

import java.util.List;

import static com.chua.redis.support.constant.RedisConstant.REDIS_SIMPLE_SERIES_PREFIX;

/**
 * 端口上报
 * @author CH
 * @since 2024/9/13
 */
@SpringBootConfiguration
@RequiredArgsConstructor
public class NetstatReport {

    public static final String NAME = "netstat";
    public static final String LOG_INDEX_NAME_PREFIX = REDIS_SIMPLE_SERIES_PREFIX + NAME + ":";

    private final TimeSeriesService timeSeriesService;
    private final SocketSessionTemplate socketSessionTemplate;

    /**
     * 处理NETSTAT相关报告事件
     *
     * @param reportEvent 包含NETSTAT报告数据的事件对象
     */
    @OnRouterEvent("netstat")
    @SuppressWarnings("ALL")
    public void report(ReportEvent<?> reportEvent) {
        // 将报告数据转换为DiskEvent对象
        List reportData = (List) reportEvent.getReportData();
        List<StateEvent> diskEventList = BeanUtils.copyPropertiesList(reportData, StateEvent.class);
        // 将NETSTAT事件信息注册到Redis
        registerRedisTime(diskEventList, reportEvent);
        // 通过Socket.IO发送NETSTAT事件信息
        reportToSocketIo(diskEventList, reportEvent);
    }

    /**
     * 通过Socket.IO报告NETSTAT事件
     *
     * @param stateEvents    包含NETSTAT监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void reportToSocketIo( List<StateEvent> stateEvents, ReportEvent<?> reportEvent) {
        // 计算事件ID数组
        String[] eventIds = reportEvent.eventIds();
        // 遍历事件ID数组，发送NETSTAT事件信息
        for (String eventId : eventIds) {
            socketSessionTemplate.send(eventId, Json.toJSONString(stateEvents));
        }
    }

    /**
     * 将NETSTAT事件信息注册到Redis
     *
     * @param stateEvents    包含NETSTAT监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void registerRedisTime( List<StateEvent> stateEvents, ReportEvent<?> reportEvent) {
        // 将NETSTAT事件信息以字符串形式保存到Redis
        timeSeriesService.put(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId(), Json.toJSONString(stateEvents));
    }

}
