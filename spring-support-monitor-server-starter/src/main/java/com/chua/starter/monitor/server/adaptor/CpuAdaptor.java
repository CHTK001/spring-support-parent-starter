package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.json.Json;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.utils.MapUtils;
import com.chua.redis.support.constant.RedisConstant;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.redis.support.service.TimeSeriesService;

import java.util.Map;

import static com.chua.starter.monitor.server.constant.RedisConstant.REDIS_TIME_SERIES_REPORT_PREFIX;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class CpuAdaptor implements Adaptor<MonitorRequest> {

    @AutoInject
    private SocketSessionTemplate socketSessionTemplate;
    @AutoInject
    private TimeSeriesService timeSeriesService;
    @Override
    public void doAdaptor(MonitorRequest cpu) {
        Map object = (Map) MapUtils.get((Map) cpu.getData(), "data");
        cpu.setData(object);
        socketSessionTemplate.send("cpu", Json.toJson(cpu));
        try {
            timeSeriesService.save(REDIS_TIME_SERIES_REPORT_PREFIX + cpu.getKey() + ":SYSTEM",
                    cpu.getTimestamp(),
                    MapUtils.getDoubleValue(object, "system"),
                    RedisConstant.DEFAULT_RETENTION_PERIOD_FOR_WEEK
            );
            timeSeriesService.save(REDIS_TIME_SERIES_REPORT_PREFIX + cpu.getKey() + ":PROCESS",
                    cpu.getTimestamp(),
                    MapUtils.getDoubleValue(object, "process"),
                    RedisConstant.DEFAULT_RETENTION_PERIOD_FOR_WEEK
            );
        } catch (Exception ignored) {
        }
    }

    @Override
    public Class<MonitorRequest> getType() {
        return MonitorRequest.class;
    }

}
