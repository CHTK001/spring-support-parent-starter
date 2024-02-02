package com.chua.starter.monitor.server.command;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.json.Json;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.adaptor.Adaptor;
import com.chua.starter.monitor.server.properties.MonitorServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
public class Report implements InitializingBean, DisposableBean {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private MonitorServerProperties monitorServerProperties;
    private final ScheduledExecutorService scheduledExecutorService = ThreadUtils.newScheduledThreadPoolExecutor(2, "monitor-core-thread");


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
            adaptor.doAdaptor(Converter.convertIfNecessary(request.getData(), adaptor.getType()));
        } catch (Exception ignored) {
        }
        stringRedisTemplate.opsForZSet()
                .add("monitor:report:" + request.getAppName()+ ":" + request.getServerHost() + "_" + request.getServerPort() + ":" +  request.getReportType(),
                                Json.toJson(request.getData()), System.currentTimeMillis());
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