package com.chua.report.server.starter.report.endpoint;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.json.Json;
import com.chua.report.client.starter.report.event.DiskEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.report.event.UsbDeviceEvent;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.redis.support.service.TimeSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;

import java.util.List;

import static com.chua.redis.support.constant.RedisConstant.REDIS_SIMPLE_SERIES_PREFIX;

/**
 * 磁盘上报
 * @author CH
 * @since 2024/9/13
 */
@SpringBootConfiguration
@RequiredArgsConstructor
public class UsbReport {

    public static final String NAME = "usb";
    public static final String LOG_INDEX_NAME_PREFIX = REDIS_SIMPLE_SERIES_PREFIX + NAME + ":";

    private final TimeSeriesService timeSeriesService;
    private final SocketSessionTemplate socketSessionTemplate;

    /**
     * 处理DISK相关报告事件
     *
     * @param reportEvent 包含DISK报告数据的事件对象
     */
    @OnRouterEvent("usb")
    @SuppressWarnings("ALL")
    public void report(ReportEvent<?> reportEvent) {
        // 将报告数据转换为DiskEvent对象
        List reportData = (List) reportEvent.getReportData();
        List<UsbDeviceEvent> list = BeanUtils.copyPropertiesList(reportData, UsbDeviceEvent.class);
        // 将DISK事件信息注册到Redis
        registerRedisTime(list, reportEvent);
        // 通过Socket.IO发送DISK事件信息
        reportToSocketIo(list, reportEvent);
    }

    /**
     * 通过Socket.IO报告DISK事件
     *
     * @param usbDeviceEvents    包含DISK监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void reportToSocketIo( List<UsbDeviceEvent> usbDeviceEvents, ReportEvent<?> reportEvent) {
        // 计算事件ID数组
        String[] eventIds = reportEvent.eventIds();
        // 遍历事件ID数组，发送DISK事件信息
        for (String eventId : eventIds) {
            socketSessionTemplate.send(eventId, Json.toJSONString(usbDeviceEvents));
        }
    }

    /**
     * 将DISK事件信息注册到Redis
     *
     * @param usbDeviceEvents    包含DISK监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void registerRedisTime( List<UsbDeviceEvent> usbDeviceEvents, ReportEvent<?> reportEvent) {
        // 将DISK事件信息以字符串形式保存到Redis
        timeSeriesService.set(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId(), JSON.toJSONString(usbDeviceEvents));
    }

}
