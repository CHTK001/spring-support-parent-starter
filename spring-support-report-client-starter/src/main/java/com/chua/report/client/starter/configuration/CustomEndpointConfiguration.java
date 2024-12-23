package com.chua.report.client.starter.configuration;

import com.chua.report.client.starter.aop.TraceStaticMethodMatcherPointcutAdvisor;
import com.chua.report.client.starter.aop.TraceStaticMethodMatcherPointcutAdvisorCondition;
import com.chua.report.client.starter.report.event.LogEvent;
import com.chua.report.client.starter.report.event.MappingEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.service.ReportService;
import com.chua.report.client.starter.spring.endpoint.MapEndpoint;
import com.chua.report.client.starter.spring.endpoint.ThreadEndpoint;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.util.Collections;

/**
 * endpoint配置
 * @author CH
 * @since 2024/9/13
 */
public class CustomEndpointConfiguration {


    /**
     * map
     * @return MapEndpoint
     */
    @Bean("mapEndpoint-1")
    @ConditionalOnMissingBean
    public MapEndpoint mapEndpoint() {
        return new MapEndpoint();
    }
    /**
     * threadEndpoint
     * @return ThreadEndpoint
     */
    @Bean("threadEndpoint-1")
    @ConditionalOnMissingBean
    public ThreadEndpoint threadEndpoint() {
        return new ThreadEndpoint();
    }


    @Bean("traceStaticMethodMatcherPointcutAdvisor")
    @ConditionalOnMissingBean
    @Conditional(TraceStaticMethodMatcherPointcutAdvisorCondition.class)
    public TraceStaticMethodMatcherPointcutAdvisor traceStaticMethodMatcherPointcutAdvisor() {
        return new TraceStaticMethodMatcherPointcutAdvisor();
    }
    /**
     * reportFilter
     * @return FilterRegistrationBean
     */
    @Bean
    @ConditionalOnMissingBean
    @DependsOn("reportService")
    public FilterRegistrationBean<ReportFilter> reportFilter(ReportService reportService) {
        FilterRegistrationBean<ReportFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ReportFilter(reportService));
        registrationBean.setAsyncSupported(true);
        registrationBean.setEnabled(true);
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1000);
        registrationBean.setUrlPatterns(Collections.singleton("/*"));
        return registrationBean;
    }

    public static class ReportFilter implements Filter {

        private final ReportService reportService;

        public ReportFilter(ReportService reportService) {
            this.reportService = reportService;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            MappingEvent event = new MappingEvent();
            long startTime = System.currentTimeMillis();
            if(request instanceof HttpServletRequest httpServletRequest) {
                event.setAddress(httpServletRequest.getRemoteHost());
                event.setMethod(httpServletRequest.getMethod());
                event.setUrl(httpServletRequest.getServletPath());
            }
            chain.doFilter(request, response);
            event.setCost(System.currentTimeMillis() - startTime);
            ReportEvent<MappingEvent> reportEvent = new ReportEvent<>();
            reportEvent.setReportData(event);
            reportEvent.setReportType(ReportEvent.ReportType.URL);
            reportService.report(reportEvent);
        }
    }
}
