package com.chua.starter.monitor.filter;

import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.monitor.factory.MonitorFactory;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.request.MonitorRequestType;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class EmptyFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        MonitorFactory monitorFactory = MonitorFactory.getInstance();
        if(request instanceof HttpServletRequest && monitorFactory.isIpEnable()) {
            MonitorRequest monitorRequest = monitorFactory.createMonitorRequest();
            monitorRequest.setType(MonitorRequestType.REPORT);
            monitorRequest.setReportType("IP");
            monitorRequest.setData(RequestUtils.getIpAddress((HttpServletRequest) request));
            monitorFactory.report(monitorRequest);
        }

        chain.doFilter(request, response);
    }
}
