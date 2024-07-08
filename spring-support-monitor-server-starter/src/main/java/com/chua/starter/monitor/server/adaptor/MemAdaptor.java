package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.MapUtils;
import com.chua.redis.support.constant.RedisConstant;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.redis.support.service.TimeSeriesService;
import jakarta.annotation.Resource;

import java.util.Map;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
@Spi("memory")
public class MemAdaptor implements Adaptor<MonitorRequest> {

    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    @Resource
    private TimeSeriesService timeSeriesService;
    @Override
    public void doAdaptor(MonitorRequest mem) {
        Map map = (Map) MapUtils.get((Map)mem.getData(), "data");
        mem.setData(map);
        socketSessionTemplate.send("mem", Json.toJson(mem));
        try {
            timeSeriesService.save(RedisConstant.REDIS_TIME_SERIES_PREFIX + "REPORT:" + mem.getKey() + ":SYSTEM",
                    mem.getTimestamp(),
                    MapUtils.getDoubleValue(map, "system"),
                    RedisConstant.DEFAULT_RETENTION_PERIOD_FOR_WEEK
            );
            timeSeriesService.save(RedisConstant.REDIS_TIME_SERIES_PREFIX + "REPORT:" + mem.getKey() + ":PROCESS",
                    mem.getTimestamp(),
                    MapUtils.getDoubleValue(map, "process"),
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
