package com.chua.starter.common.support.logger;

import com.chua.common.support.function.Joiner;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.common.support.utils.RequestUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.chua.starter.common.support.logger.LogGuidAspect.GuidhreadLocal;

/**
 * 切面
 *
 * @author CH
 */
@Lazy
public class LoggerPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor implements InitializingBean {

    @Autowired(required = false)
    HttpServletRequest request;
    @Autowired(required = false)
    HttpServletResponse response;
    private LoggerService loggerService;
    private ApplicationContext applicationContext;

    ExpressionParser expressionParser = new org.springframework.expression.spel.standard.SpelExpressionParser();

    public LoggerPointcutAdvisor(LoggerService loggerService) {
        this.loggerService = Optional.ofNullable(loggerService).orElse(DefaultLoggerService.getInstance());
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(method, Logger.class) && StringUtils.isNotEmpty(method.getDeclaredAnnotation(Logger.class).value());

    }

    private static final Cache<String, Boolean> CACHE = CacheBuilder.newBuilder().expireAfterWrite(3, TimeUnit.SECONDS)
            .build();

    @Override
    public void afterPropertiesSet() throws Exception {
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                return invokeValue(invocation);
            }


        });
    }

    /**
     * 执行方法
     *
     * @param invocation 方法
     * @return 结果
     */
    protected Object invokeValue(MethodInvocation invocation) {
        Object proceed = null;
        int status = 0;
        long startTime = System.nanoTime();
        Throwable e1 = null;
        try {
            proceed = invocation.proceed();
        } catch (Throwable e) {
            status = 1;
            e1 = e;
        }
        try {
            saveLog(proceed, invocation, status, startTime);
        } catch (Exception ignored) {
        }

        if (status == 1) {
            throw new RuntimeException(e1);
        }

        return proceed;
    }

    /**
     * 获取日志动作
     *
     * @param method 方法
     */
    protected String getAction(Method method) {
        Logger logger = method.getDeclaredAnnotation(Logger.class);
        return logger.action();
    }

    /**
     * 获取日志名称
     *
     * @param method 方法
     */
    protected String getName(Method method) {
        Logger logger = method.getDeclaredAnnotation(Logger.class);
        return logger.value();
    }

    /**
     * 获取日志内容
     *
     * @param standardEvaluationContext spel
     * @param method                    方法
     */
    protected String getContent(StandardEvaluationContext standardEvaluationContext, Method method) {
        Logger logger = method.getDeclaredAnnotation(Logger.class);
        try {//+ ' 账号在( '+ #ip + ' )'  + #args[0].username + '登录系统(状态: ' + #result['code'] + '  '   #result['msg'] + ') 登录方式('WEB' ) '
            return expressionParser.parseExpression(
                            logger.content(), new TemplateParserContext())
                    .getValue(standardEvaluationContext, String.class);
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * 保存日志
     *
     * @param proceed    方法结果
     * @param invocation 方法
     * @param status     方法是否异常
     * @param startTime  开始时间(ns)
     */
    protected void saveLog(Object proceed, MethodInvocation invocation, int status, long startTime) {
        String key = GuidhreadLocal.get();
        Boolean ifPresent = CACHE.getIfPresent(key);
        if (null != ifPresent) {
            return;
        }

        CACHE.put(key, true);

        Method method = invocation.getMethod();
        String address = RequestUtils.getIpAddress(request);
        DateTime now = DateTime.now();
        Date date = now.toDate();
        HttpSession session = request.getSession();


        SysLog sysLog = new SysLog();
        StandardEvaluationContext standardEvaluationContext = new StandardEvaluationContext(applicationContext);
        standardEvaluationContext.addPropertyAccessor(new BeanFactoryAccessor());
        sysLog.setLogMapping(RequestUtils.getUrl(request));
        sysLog.setCreateTime(date);
        try {
            sysLog.setCreateName((String) session.getAttribute("username"));
            sysLog.setCreateBy((String) session.getAttribute("userId"));
        } catch (Exception ignored) {
        }
        sysLog.setMethodName(method.getName());
        sysLog.setClassName(method.getDeclaringClass().getName());
        sysLog.setLogName(getName(method));
        sysLog.setLogAction(getAction(method));
        sysLog.setLogCode(GuidhreadLocal.get());
        sysLog.setLogParam(analysisParam(invocation));
        sysLog.setLogAddress(address);
        if (StringUtils.isEmpty(sysLog.getLogName()) || StringUtils.isEmpty(sysLog.getLogAction())) {
            return;
        }

        standardEvaluationContext.setVariable("ip", RequestUtils.getIpAddress());
        Object username = request.getSession().getAttribute("username");
        standardEvaluationContext.setVariable("current_username", null == username ? "" : username);
        standardEvaluationContext.setVariable("now", now.toStandard());
        standardEvaluationContext.setVariable("nowDate", now);
        standardEvaluationContext.setVariable("result", proceed);
        standardEvaluationContext.setVariable("method", method);
        standardEvaluationContext.setVariable("args", invocation.getArguments());
        recordLog(sysLog, proceed, status, startTime, getContent(standardEvaluationContext, method));
    }

    /**
     * 解析参数
     *
     * @param invocation 方法
     * @return 参数
     */
    private String analysisParam(MethodInvocation invocation) {
        Parameter[] parameters = invocation.getMethod().getParameters();
        StringBuilder builder = new StringBuilder();
        Object[] arguments = invocation.getArguments();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object argument = arguments[i];
            builder.append(parameter.getName()).append(":").append(value(argument)).append("\r\n");
        }

        return builder.toString();
    }

    /**
     * 解析参数
     *
     * @param argument 参数
     * @return 参数
     */
    private Object value(Object argument) {
        if (null == argument) {
            return null;
        }

        Class<?> aClass = argument.getClass();
        if (ClassUtils.isJavaType(aClass)) {
            return argument;
        }

        if (ClassUtils.isJavaType(aClass)) {
            return null;
        }

        try {
            return Json.toJson(argument);
        } catch (Throwable ignored) {
        }
        return null;
    }

    public void recordLog(SysLog sysLog,
                          Object proceed,
                          int status,
                          long startTime,
                          String content
    ) {
        sysLog.setResult(proceed);
        sysLog.setLogCost((System.nanoTime() - startTime) / 1000000.0);

        sysLog.setLogStatus("1");
        if (status == 1) {
            sysLog.setLogStatus("0");
        }
        if (proceed instanceof ResultData) {
            sysLog.setLogStatus(((ResultData<?>) proceed).getCode());
        }
        Enumeration<String> headerNames = request.getHeaderNames();
        List<String> stack = new LinkedList<>();
        stack.add("<strong class=\"node-details__name collapse-handle\">Request headers</strong>");
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            stack.add(headerName + ": " + request.getHeader(headerName));
        }
        stack.add("<strong class=\"node-details__name collapse-handle\">Request parameter</strong>");
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            stack.add(entry.getKey() + ": " + (entry.getValue().length == 1 ? entry.getValue()[0] : entry.getValue()));
        }
        sysLog.setLogWatch(Joiner.on("<br />").join(stack));
        sysLog.setLogContent(content);
        loggerService.save(sysLog);
    }

}
