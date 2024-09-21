package com.chua.report.server.starter.report.endpoint;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.json.Json;
import com.chua.report.client.starter.report.event.MemEvent;
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
 * 内存上报
 * @author CH
 * @since 2024/9/13
 */
@SpringBootConfiguration
@RequiredArgsConstructor
public class MemReport {

    public static final String NAME = "mem";
    public static final String LOG_INDEX_NAME_PREFIX = REDIS_TIME_SERIES_PREFIX + NAME + ":";

    private final TimeSeriesService timeSeriesService;
    private final StringRedisTemplate stringRedisTemplate;
    private final SocketSessionTemplate socketSessionTemplate;

    /**
     * 处理JVM相关报告事件
     *
     * @param reportEvent 包含JVM报告数据的事件对象
     */
    @OnRouterEvent("mem")
    public void report(ReportEvent<?> reportEvent) {
        // 将报告数据转换为JvmEvent对象
        MemEvent memEvent = BeanUtils.copyProperties(reportEvent.getReportData(), MemEvent.class);
        // 将MEM事件信息注册到Redis
        registerRedisTime(memEvent, reportEvent);
        // 通过Socket.IO发送MEM事件信息
        reportToSocketIo(memEvent, reportEvent);
    }

    /**
     * 通过Socket.IO报告MEM事件
     *
     * @param memEvent    包含MEM监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void reportToSocketIo(MemEvent memEvent, ReportEvent<?> reportEvent) {
        // 计算事件ID数组
        String[] eventIds = reportEvent.eventIds();
        // 遍历事件ID数组，发送MEM事件信息
        for (String eventId : eventIds) {
            socketSessionTemplate.send(eventId, Json.toJSONString(memEvent));
        }
    }

    /**
     * 将MEM事件信息注册到Redis
     *
     * @param memEvent    包含MEM监控数据的事件对象
     * @param reportEvent 原始报告事件对象
     */
    private void registerRedisTime(MemEvent memEvent, ReportEvent<?> reportEvent) {
        // 将MEM事件信息以字符串形式保存到Redis
        ReportExpireSetting expire = GlobalSettingFactory.getInstance().get("expire", ReportExpireSetting.class);
        Long expireTime = expire.getMem();
        if(null == expireTime || expireTime <=0 ) {
            return;
        }
        timeSeriesService.save(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId(),
                reportEvent.getTimestamp(),
                memEvent.getUsedPercent().doubleValue(),
                expireTime * 1000L);
    }

}
