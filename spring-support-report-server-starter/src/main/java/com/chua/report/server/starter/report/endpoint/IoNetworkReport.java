package com.chua.report.server.starter.report.endpoint;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.json.Json;
import com.chua.report.client.starter.report.event.IoEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.setting.ReportExpireSetting;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.common.support.application.GlobalSettingFactory;
import com.chua.starter.redis.support.service.TimeSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static com.chua.redis.support.constant.RedisConstant.REDIS_TIME_SERIES_PREFIX;

/**
 * 网络io上报
 * @author CH
 * @since 2024/12/23
 */
@SpringBootConfiguration
@RequiredArgsConstructor
public class IoNetworkReport {

    public static final String NAME = "io-network";
    public static final String LOG_INDEX_NAME_PREFIX = REDIS_TIME_SERIES_PREFIX + NAME + ":";

    private final TimeSeriesService timeSeriesService;
    private final StringRedisTemplate stringRedisTemplate;
    private final SocketSessionTemplate socketSessionTemplate;

    /**
     * 处理JVM相关报告事件
     *
     * @param reportEvent 包含JVM报告数据的事件对象
     */
    @OnRouterEvent("io_network")
    public void report(ReportEvent<?> reportEvent) {
        // 将报告数据转换为JvmEvent对象
        IoEvent report = BeanUtils.copyProperties(reportEvent.getReportData(), IoEvent.class);
        // 将CPU事件信息注册到Redis
        registerRedisTime(report, reportEvent);
        // 通过Socket.IO发送CPU事件信息
        reportToSocketIo(report, reportEvent);
    }

    /**
     * 通过Socket.IO报告CPU事件
     *
     * @param report    包含CPU监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void reportToSocketIo(IoEvent report, ReportEvent<?> reportEvent) {
        // 计算事件ID数组
        String[] eventIds = reportEvent.eventIds();
        // 遍历事件ID数组，发送CPU事件信息
        for (String eventId : eventIds) {
            socketSessionTemplate.send(eventId, Json.toJSONString(report));
        }
    }

    /**
     * 将CPU事件信息注册到Redis
     *
     * @param report    包含CPU监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void registerRedisTime(IoEvent report, ReportEvent<?> reportEvent) {
        // 将CPU事件信息以字符串形式保存到Redis
        ReportExpireSetting expire = GlobalSettingFactory.getInstance().get("expire", ReportExpireSetting.class);
        Long expireTime = expire.getCpu();
        if(null == expireTime || expireTime <=0 ) {
            return;
        }
        timeSeriesService.save(REDIS_TIME_SERIES_PREFIX + NAME + ":read:" + reportEvent.clientEventId(), reportEvent.getTimestamp(),
                report.getReceiveBytes(), expireTime * 1000L);
        timeSeriesService.save(REDIS_TIME_SERIES_PREFIX + NAME + ":write:" + reportEvent.clientEventId(), reportEvent.getTimestamp(),
                report.getTransmitBytes(), expireTime * 1000L);
    }

}
