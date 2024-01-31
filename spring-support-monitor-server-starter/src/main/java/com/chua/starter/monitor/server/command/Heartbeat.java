package com.chua.starter.monitor.server.command;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.json.Json;
import com.chua.starter.monitor.request.MonitorRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 心跳
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Service
public class Heartbeat {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 心跳
     *
     * @param request 要求
     */
    @OnRouterEvent("heartbeat")
    public void heartbeat(MonitorRequest request) {
        stringRedisTemplate.opsForValue()
                        .set("monitor:heart:" + request.getAppName()+ ":" + request.getServerHost() + "_" + request.getServerPort(), Json.toJson(request.getData()), 2, TimeUnit.MINUTES);
    }
}
