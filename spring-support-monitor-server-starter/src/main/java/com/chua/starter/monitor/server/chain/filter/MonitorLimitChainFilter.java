package com.chua.starter.monitor.server.chain.filter;

import com.chua.common.support.annotations.SpiDescribe;
import com.chua.common.support.annotations.SpiOptional;
import com.chua.common.support.chain.ChainContext;
import com.chua.common.support.chain.FilterChain;
import com.chua.common.support.chain.filter.ChainFilter;
import com.chua.common.support.geo.GeoCity;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.objects.annotation.Config;
import com.chua.common.support.protocol.request.BadResponse;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.monitor.server.entity.MonitorProxyLimitLog;
import com.chua.starter.monitor.server.service.IptablesService;
import com.chua.starter.monitor.server.service.MonitorProxyLimitLogService;
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

    @AutoInject
    private RedissonClient redisson;
    @Config("serverId")
    private String serverId;

    @AutoInject
    private LimitFactory limitFactory;
    private static final ExecutorService POOL = ThreadUtils.newVirtualThreadExecutor();

    @AutoInject
    private IptablesService iptablesService;
    @AutoInject
    private MonitorProxyLimitLogService monitorProxyLimitLogService;

    @Override
    public <T> void doFilter(ChainContext<T> context, FilterChain filterChain) {
        Request request = context.getRequest();
        String url = request.url();
        String hostString = request.remoteAddress().getHostString();
        try {
            if(limitFactory.tryAcquire(url, hostString) ) {
                filterChain.doFilter(context);
                doRegisterLog(url, hostString, "allow");
                return;
            }
        } catch (Exception e) {
            doRegisterLog(url, hostString, "allow");
            return;
        }

        doRegisterLog(url, hostString, "deny");
        context.setResponse(new BadResponse(request, 405, "系统繁忙"));
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
                try {
                    ReturnResult<GeoCity> geoCityReturnResult = iptablesService.transferAddress(hostString);
                    if(geoCityReturnResult.isOk()) {
                        monitorProxyLimitLog.setLimitLogAddressGeo(geoCityReturnResult.getData().getCity());
                    }
                } catch (Exception ignored) {
                }
                monitorProxyLimitLog.setCreateTimeMin( System.currentTimeMillis() / (60 * 1000) * (60 * 1000));
                monitorProxyLimitLog.setCreateTime(new Date());
                monitorProxyLimitLogService.save(monitorProxyLimitLog);
            } catch (Exception ignored) {
            }
        });
    }
}

