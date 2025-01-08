package com.chua.report.client.starter.aop;

import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.report.event.TraceEvent;
import com.chua.report.client.starter.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 切面
 *
 * @author CH
 * @since 2024/9/20
 */
@Slf4j
public class TraceStaticMethodMatcherPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor implements InitializingBean {
    private final Map<Class<?>, Method> exceptionHandlerMap = new HashMap<>();
    @Autowired(required = false)
    private ReportService reportService;
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if(null == reportService) {
            return false;
        }
        if (!targetClass.isAnnotationPresent(Service.class)
                && !targetClass.isAnnotationPresent(Configuration.class)
                && !targetClass.isAnnotationPresent(SpringBootConfiguration.class)
                && !targetClass.isAnnotationPresent(Component.class)
                && !targetClass.isAnnotationPresent(Mapping.class)
        ) {
            return false;
        }
        return method.isAnnotationPresent(RequestMapping.class) ||
                method.isAnnotationPresent(GetMapping.class) ||
                method.isAnnotationPresent(PostMapping.class) ||
                method.isAnnotationPresent(PutMapping.class) ||
                method.isAnnotationPresent(DeleteMapping.class) ||
                method.isAnnotationPresent(PatchMapping.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                Method method = invocation.getMethod();
                Object[] arguments = invocation.getArguments();
                Object aThis = invocation.getThis();
                TraceEvent traceEvent = new TraceEvent();
                if (null != aThis) {
                    traceEvent.setClassName(aThis.getClass().getTypeName());
                }
                traceEvent.setMethod(method.getName());
                traceEvent.setParameterSize(arguments.length);
                try {
                    traceEvent.setLineNumber(Thread.currentThread().getStackTrace()[0].getLineNumber());
                    traceEvent.setParameters(arguments);
                } catch (Exception ignored) {
                }
                long startTime = System.currentTimeMillis();
                traceEvent.setCode(0);
                try {
                    return invocation.proceed();
                } catch (Throwable ex) {
                    traceEvent.setCode(1);
                    throw ex;
                } finally {
                    traceEvent.setCost(System.currentTimeMillis() - startTime);
                    ReportEvent<TraceEvent> reportEvent = new ReportEvent<>();
                    reportEvent.setReportType(ReportEvent.ReportType.TRACE);
                    reportEvent.setReportData(traceEvent);
                }
            }
        });

    }

}
