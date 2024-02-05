package com.chua.starter.monitor.server.command;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.json.Json;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.constant.MonitorConstant;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class Heartbeat implements MonitorConstant {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    /**
     * 心跳
     *
     * @param request 要求
     */
    @OnRouterEvent("heartbeat")
    public void heartbeat(MonitorRequest request) {
        String key = HEART + request.getAppName()+ ":" + request.getServerHost() + "_" + request.getServerPort();

        if(!stringRedisTemplate.hasKey(key)) {
            stringRedisTemplate.delete(key + ":SERVER");
            socketSessionTemplate.send("online", Json.toJson(request));
        }
        stringRedisTemplate.opsForValue()
                        .set(key, Json.toJson(request), 2, TimeUnit.MINUTES);
        if(log.isDebugEnabled()) {
            log.debug("检测到: {}心跳 <- {}:{}", request.getAppName(), request.getServerHost(), request.getServerPort());
        }
    }
}
