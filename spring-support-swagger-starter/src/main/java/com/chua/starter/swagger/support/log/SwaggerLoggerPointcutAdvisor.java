package com.chua.starter.swagger.support.log;

import com.chua.common.support.lang.date.DateTime;
import com.chua.starter.common.support.logger.*;
import com.chua.starter.common.support.utils.RequestUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.ExpressionParser;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.chua.starter.common.support.logger.LogGuidAspect.GuidhreadLocal;

/**
 * 切面
 *
 * @author CH
 */
@Lazy
public class SwaggerLoggerPointcutAdvisor extends LoggerPointcutAdvisor {

    @Autowired(required = false)
    HttpServletRequest request;
    @Autowired(required = false)
    HttpServletResponse response;
    private LoggerService loggerService;
    private ApplicationContext applicationContext;

    ExpressionParser expressionParser = new org.springframework.expression.spel.standard.SpelExpressionParser();

    public SwaggerLoggerPointcutAdvisor(LoggerService loggerService) {
        super(loggerService);
        this.loggerService = Optional.ofNullable(loggerService).orElse(DefaultLoggerService.getInstance());
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return (!AnnotatedElementUtils.hasAnnotation(method, LoggerIgnore.class) && !AnnotatedElementUtils.hasAnnotation(method, Logger.class) && !AnnotatedElementUtils.hasAnnotation(targetClass, Logger.class))
                && (AnnotatedElementUtils.hasAnnotation(method, ApiOperation.class) || AnnotatedElementUtils.hasAnnotation(method, Operation.class));


    }

    private static final Cache<String, Boolean> CACHE = CacheBuilder.newBuilder().expireAfterWrite(3, TimeUnit.SECONDS)
            .build();


    @Override
    public void saveLog(Object proceed, MethodInvocation invocation, int status, long startTime) {
        Method method = invocation.getMethod();
        String address = RequestUtils.getIpAddress(request);
        DateTime now = DateTime.now();
        Date date = now.toDate();
        HttpSession session = request.getSession();

        String logName = "", action = "";
        ApiOperation apiOperation = method.getDeclaredAnnotation(ApiOperation.class);
        if (null != apiOperation) {
            logName = apiOperation.value();
        } else {
            Operation operation = method.getDeclaredAnnotation(Operation.class);
            logName = operation.description();
        }

        String name = method.getName().toUpperCase();
        if (name.contains("SAVE") || name.contains("INSERT") || name.contains("ADD")) {
            action = "添加";
        } else if (name.contains("UPDATE") || name.contains("MODIFY")) {
            action = "修改";
        } else if (name.contains("DELETE") || name.contains("DROP") || name.contains("REMOVE")) {
            action = "删除";
        } else if (name.contains("LIST") || name.contains("PAGE") || name.contains("QUERY") || name.contains("SELECT")) {
            action = "查询";
        }

        String key = session.getAttribute("userId") + logName + action + address;
        Boolean ifPresent = CACHE.getIfPresent(key);
        if (null != ifPresent) {
            return;
        }

        CACHE.put(key, true);
        SysLog sysLog = new SysLog();
        sysLog.setLogMapping(RequestUtils.getUrl(request));
        sysLog.setCreateTime(date);
        try {
            sysLog.setCreateName((String) session.getAttribute("username"));
            sysLog.setCreateBy((String) session.getAttribute("userId"));
        } catch (Exception ignored) {
        }
        sysLog.setMethodName(method.getName());
        sysLog.setClassName(method.getDeclaringClass().getName());
        sysLog.setLogName(logName);
        sysLog.setLogAction(action);
        sysLog.setLogCode(GuidhreadLocal.get());
        sysLog.setLogAddress(address);

        Object username = request.getSession().getAttribute("username");
        recordLog(sysLog, proceed, null, null
                , status, startTime, expressionParser, DateTime.now().toStandard() + " " + username + action + "了模块" + logName);
    }


}