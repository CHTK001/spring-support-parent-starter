package com.chua.starter.monitor.server.command;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.adaptor.Adaptor;
import com.chua.starter.monitor.server.properties.MonitorServerProperties;
import com.chua.starter.redis.support.service.TimeSeriesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
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
@RequiredArgsConstructor
public class Report implements InitializingBean, DisposableBean {

    final  RedisTemplate stringRedisTemplate;
    final  MonitorServerProperties monitorServerProperties;
    private final ScheduledExecutorService scheduledExecutorService = ThreadUtils.newScheduledThreadPoolExecutor(2, "com-ch-monitor-core-thread");


    final  TimeSeriesService timeSeriesService;
    /**
     * 心跳
     *
     * @param request 要求
     */
    @OnRouterEvent("report")
    public void heartbeat(MonitorRequest request) {
        try {
            Adaptor adaptor = ServiceProvider.of(Adaptor.class)
                    .getNewExtension(request.getReportType());
            if(null == adaptor) {
                return;
            }
            Class type = adaptor.getType();
            if(type == MonitorRequest.class) {
                adaptor.doAdaptor(request);
            } else {
                adaptor.doAdaptor(BeanUtils.copyProperties(request.getData(), type));
            }
            if(adaptor.intoDb()) {
                stringRedisTemplate.opsForZSet() .add(request.getUid(), request.getData(), System.currentTimeMillis());
            }
            if(adaptor.intoSet()) {
                stringRedisTemplate.opsForValue().set(request.getUid(), request.getData(), System.currentTimeMillis());
            }
        } catch (Exception ignored) {
        }
    }



    public void cacheCleaner() {
        Set<String> policyCacheKeys = stringRedisTemplate.keys("monitor:report:*");
        if (CollectionUtils.isEmpty(policyCacheKeys)) {
            return;
        }

        ZSetOperations<String, String> zSetOperations = stringRedisTemplate.opsForZSet();
        //移除过期数据
        long now = System.currentTimeMillis();
        long tts = now - monitorServerProperties.getReportDataKeepAlive() * 1000;
        for (String key : policyCacheKeys) {
           zSetOperations.removeRangeByScore(key, 0, tts);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        scheduledExecutorService.scheduleAtFixedRate(this::cacheCleaner, 0, 1, TimeUnit.HOURS);
    }

    @Override
    public void destroy() throws Exception {
        ThreadUtils.closeQuietly(scheduledExecutorService);
    }
}
