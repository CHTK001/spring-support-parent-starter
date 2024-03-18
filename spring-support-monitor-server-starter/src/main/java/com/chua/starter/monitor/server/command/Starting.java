package com.chua.starter.monitor.server.command;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.constant.MonitorConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 心跳
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Service
@Slf4j
public class Starting implements MonitorConstant {

    @Resource
    private RedisTemplate stringRedisTemplate;

    /**
     * 心跳
     *
     * @param request 要求
     */
    @OnRouterEvent("start")
    public void heartbeat(MonitorRequest request) {
        String key = HEART + request.getAppName()+ ":" + request.getServerHost() + "_" + request.getServerPort();
        stringRedisTemplate.delete(key + ":SERVER");
    }
}
