package com.chua.report.server.starter.consumer;

import com.chua.common.support.chain.ChainContext;
import com.chua.common.support.chain.FilterChain;
import com.chua.common.support.chain.filter.Filter;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.request.OkResponse;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.server.starter.router.Router;

import java.util.concurrent.ExecutorService;

/**
 * 注册器
 * @author CH
 * @since 2024/12/24
 */
public class ReportFilter implements Filter {
    private final Router router;
    private final String topic;

    private final ExecutorService executorService = ThreadUtils.newVirtualThreadExecutor();

    public ReportFilter(Router router, String topic) {
        this.router = router;
        this.topic = topic;
    }

    @Override
    public <T> void doFilter(ChainContext<T> context, FilterChain filterChain) {
        Request request = context.getRequest();
        context.setResponse(new OkResponse(request));
        try {
            executorService.execute(() -> {
                try {
                    ReportEvent reportEvent = createEvent(request);
                    router.doRoute(reportEvent);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
        filterChain.doFilter(context);
    }

    private ReportEvent createEvent(Request request) {
        String method = request.method();
        if ("topic".equals(method)) {
            return Json.fromJson(request.getBody(), ReportEvent.class);
        }

        ReportEvent reportEvent = Json.fromJson(request.getBody(), ReportEvent.class);
        reportEvent.setReportType(ReportEvent.ReportType.valueOf(method.toUpperCase()));
        return reportEvent;
    }
}

