package com.chua.report.server.starter.chain.filter;

import com.chua.common.support.annotations.SpiDescribe;
import com.chua.common.support.chain.ChainContext;
import com.chua.common.support.chain.FilterChain;
import com.chua.common.support.chain.filter.Filter;
import com.chua.common.support.geo.GeoCity;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.objects.annotation.Config;
import com.chua.common.support.protocol.request.BadResponse;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.netty.support.proxy.filter.LastNettyFinishChainFilter;
import com.chua.report.server.starter.entity.MonitorProxyLog;
import com.chua.report.server.starter.service.MonitorProxyLogService;
import com.chua.report.server.starter.service.ReportIptablesService;
import org.redisson.api.RedissonClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
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
@SpiDescribe(value = "黑名单")
public class ReportBlackChainFilter implements Filter {

    @AutoInject
    private RedissonClient redisson;
    @Config("serverId")
    private String serverId;

    @AutoInject
    private ReportBlackLimitFactory reportBlackLimitFactory;
    private static final ExecutorService POOL = ThreadUtils.newVirtualThreadExecutor();

    @AutoInject
    private ReportIptablesService reportIptablesService;
    @AutoInject
    private MonitorProxyLogService monitorProxyLogService;

    @Override
    public <T> void doFilter(ChainContext<T> context, FilterChain filterChain) {
        Request request = context.getRequest();
        String url = request.url();
        String hostString = request.getIpAddress();
        try {
            if(reportBlackLimitFactory.tryAcquire(hostString) ) {
                doRegisterLog(url, hostString, "allow");
                filterChain.doFilter(context);
                return;
            }
        } catch (Exception e) {
            doRegisterLog(url, hostString, "allow");
            context.setResponse(new BadResponse(request, 405, "系统繁忙"));
            return;
        }

        doRegisterLog(url, hostString, "deny");
        context.setResponse(new BadResponse(request, 405, "系统繁忙"));
        filterChain.setFinish();
        filterChain.addLast(new LastNettyFinishChainFilter());
        filterChain.doFilter(context);
    }

    private void doRegisterLog(String url, String hostString, String type) {
        POOL.execute(() -> {
            try {
                MonitorProxyLog monitorProxyLimitLog = new MonitorProxyLog();
                monitorProxyLimitLog.setMonitorProxyLogUrl(url);
                monitorProxyLimitLog.setMonitorProxyLogServerId(serverId);
                monitorProxyLimitLog.setMonitorProxyLogAddress(hostString);
                monitorProxyLimitLog.setMonitorProxyLogType("BLACK");
                monitorProxyLimitLog.setMonitorProxyLogCode("deny".equals(type) ? "-1" : "0");
                try {
                    ReturnResult<GeoCity> geoCityReturnResult = reportIptablesService.transferReportAddress(hostString);
                    if(geoCityReturnResult.isOk()) {
                        monitorProxyLimitLog.setMonitorProxyLogAddressGeo(geoCityReturnResult.getData().getCity());
                    }
                } catch (Exception ignored) {
                }
                monitorProxyLimitLog.setMonitorProxyLogDate(LocalDate.now().toEpochSecond(LocalTime.of(0, 0, 0), ZoneOffset.UTC));
                monitorProxyLimitLog.setMonitorProxyLogHour(LocalDateTime.now()
                        .withMinute(0).withSecond(0).withNano(0)
                        .toEpochSecond(ZoneOffset.UTC));
                monitorProxyLimitLog.setCreateTime(new Date());
                monitorProxyLogService.save(monitorProxyLimitLog);
            } catch (Exception ignored) {
            }
        });
    }
}

