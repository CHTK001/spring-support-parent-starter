package com.chua.report.client.starter.aop;

import com.chua.common.support.utils.StringUtils;
import com.chua.report.client.starter.properties.ReportClientProperties;
import com.chua.report.client.starter.report.event.ReportEvent;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;
import java.util.Set;

import static com.chua.report.client.starter.report.event.ReportEvent.ReportType.ALL;

/**
 * trace aop过滤条件
 * @author CH
 * @since 2024/9/20
 */
public class TraceStaticMethodMatcherPointcutAdvisorCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, ReportClientProperties> beansOfType = context.getBeanFactory().getBeansOfType(ReportClientProperties.class);
        ReportClientProperties reportClientProperties;
        if(beansOfType.isEmpty()) {
            reportClientProperties = Binder.get(context.getEnvironment()).bindOrCreate(ReportClientProperties.PRE, ReportClientProperties.class);
        } else {
            reportClientProperties = beansOfType.values().iterator().next();
        }

        if(StringUtils.isBlank(reportClientProperties.getTraceAop())) {
            return false;
        }

        Set<ReportEvent.ReportType> report = reportClientProperties.getReport();

        return report.contains(ALL) || report.contains(ReportEvent.ReportType.TRACE);
    }
}
