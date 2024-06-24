package com.chua.starter.monitor.server.chain.filter;

import com.chua.common.support.annotations.SpiDescribe;
import com.chua.common.support.annotations.SpiOptional;
import com.chua.common.support.chain.ChainContext;
import com.chua.common.support.chain.FilterChain;
import com.chua.common.support.chain.filter.ChainFilter;
import com.chua.common.support.function.request.BussinessResponse;
import com.chua.common.support.function.request.Request;
import com.chua.common.support.objects.annotation.Config;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.monitor.server.entity.MonitorProxyLimitLog;
import com.chua.starter.monitor.server.service.MonitorProxyLimitLogService;
import jakarta.annotation.Resource;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;

import java.util.Date;
import java.util.concurrent.ExecutorService;

/**
 * MonitorLimitChainFilter 类实现了 ChainFilter 接口，用于在请求处理链中添加监控和限制逻辑。
 * 该类的主要作用是通过链式调用的方式，对通过的请求进行监控，确保系统的稳定性和性能。
 * 它可以被插入到请求处理链的任何位置，以实现对特定请求的限制和监控。
 *
 * @author CH
 * @since 2024/6/21
 */
@SpiDescribe(value = "持久化限流")
@SpiOptional({"http-proxy", "proxy"})
public class MonitorLimitChainFilter implements ChainFilter {

    @Resource
    private RedissonClient redisson;
    @Config("serverId")
    private String serverId;
    private static final ExecutorService POOL = ThreadUtils.newFixedThreadExecutor(Runtime.getRuntime().availableProcessors() * 100);

    @Resource
    private MonitorProxyLimitLogService monitorProxyLimitLogService;

    @Override
    public <T> void doFilter(ChainContext<T> context, FilterChain filterChain) {
        Request request = context.getRequest();
        String url = request.url();
        String hostString = request.remoteAddress().getHostString();
        RRateLimiter rateLimiterAddress = redisson.getRateLimiter("monitor:proxy:limit:token:" + serverId + ":"+ url + ":" + hostString);
        RRateLimiter rateLimiter = redisson.getRateLimiter("monitor:proxy:limit:token:" + serverId + ":"+ url);
        try {
            if(rateLimiterAddress.tryAcquire(1) ) {
                filterChain.doFilter(context);
                doRegisterLog(url, hostString, "allow");
                return;
            }
        } catch (Exception e) {
            try {
                if(rateLimiter.tryAcquire(1) ) {
                    filterChain.doFilter(context);
                    doRegisterLog(url, hostString, "allow");
                    return;
                }
            } catch (Exception ex) {
                filterChain.doFilter(context);
                doRegisterLog(url, hostString, "allow");

                return;
            }
        }

        doRegisterLog(url, hostString, "deny");
        context.setResponse(new BussinessResponse(request));
        filterChain.doFilter(context);
    }

    private void doRegisterLog(String url, String hostString, String type) {
        POOL.execute(() -> {
            try {
                MonitorProxyLimitLog monitorProxyLimitLog = new MonitorProxyLimitLog();
                monitorProxyLimitLog.setLimitLogUrl(url);
                monitorProxyLimitLog.setLimitLogServerId(serverId);
                monitorProxyLimitLog.setLimitLogAddress(hostString);
                monitorProxyLimitLog.setLimitLogType(type);
                monitorProxyLimitLog.setCreateTime(new Date());
                monitorProxyLimitLogService.save(monitorProxyLimitLog);
            } catch (Exception ignored) {
            }
        });
    }
}

