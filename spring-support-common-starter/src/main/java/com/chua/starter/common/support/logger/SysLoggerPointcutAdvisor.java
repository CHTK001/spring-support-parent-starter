package com.chua.starter.common.support.logger;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.*;
import com.chua.starter.common.support.annotations.OperateLog;
import com.chua.starter.common.support.annotations.SysLogger;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

import static com.chua.common.support.constant.NameConstant.*;

/**
 * 操作日志切入点顾问
 *
 * @author CH
 */
@Lazy
public class SysLoggerPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor implements InitializingBean {

    @Autowired HttpServletRequest request;
    @Autowired HttpServletResponse response;

    @Autowired
    private ApplicationContext applicationContext;

    private static final Class<? extends Annotation> OPERATION = (Class<? extends Annotation>) ClassUtils.forName("io.swagger.v3.oas.annotations.Operation");
    private static final Class<? extends Annotation> API_OPERATION = (Class<? extends Annotation>) ClassUtils.forName("io.swagger.annotations.ApiOperation");

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(SysLogger.class) && !method.isAnnotationPresent(LoggerIgnore.class);
    }

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
     * 执行值
     *
     * @param invocation 调用
     * @return {@link Object}
     * @throws Throwable 可丢弃
     */
    private Object invokeValue(MethodInvocation invocation) throws Throwable {
        Object proceed = null;
        Throwable throwable = null;
        long startTime = System.currentTimeMillis();
        try {
            proceed = invocation.proceed();
        } catch (Throwable e) {
            throwable = e;
        }
        createOperateLogger(proceed, throwable, invocation, startTime);
        return proceed;
    }

    void createOperateLogger(Object proceed, Throwable throwable, MethodInvocation invocation, long startTime) {
        Method method = invocation.getMethod();
        OperateLog operateLog = method.getDeclaredAnnotation(OperateLog.class);
        if(!operateLog.enable()) {
            return;
        }

        String name = getName(operateLog, method);
        if(null == name) {
            return;
        }

        String module = getModule(method);
        SysLoggerInfo sysLoggerInfo = new SysLoggerInfo(name);
        sysLoggerInfo.setCreateBy(RequestUtils.getUsername());
        sysLoggerInfo.setCreateName(RequestUtils.getUserId());
        sysLoggerInfo.setCreateTime(new Date());
        sysLoggerInfo.setLogName(name);
        sysLoggerInfo.setLogModule(StringUtils.defaultString(operateLog.module(), module));
        sysLoggerInfo.setLogCost((System.currentTimeMillis() - startTime) );
        sysLoggerInfo.setClientIp(RequestUtils.getIpAddress(request));

        if(operateLog.logArgs()) {
            List<Object> params = new LinkedList<>();
            for (Object argument : invocation.getArguments()) {
                if(null == argument) {
                    continue;
                }
                Class<?> aClass = argument.getClass();
                String typeName = aClass.getTypeName();
                if(HttpServletResponse.class.isAssignableFrom(aClass) || HttpServletRequest.class.isAssignableFrom(aClass) || typeName.startsWith("org.spring") || typeName.startsWith("javax") || typeName.startsWith("java")) {
                    continue;
                }

                params.add(argument);
            }
            String json = null;
            try {
                json = Json.prettyFormat(params);
            } catch (Exception ignored) {
            }
            if(null != json && json.length() < 2000) {
                sysLoggerInfo.setLogParam(json);
            }
        }

        String content = operateLog.content();
        sysLoggerInfo.setLogStatus(null == throwable ? 1 : 0);
        sysLoggerInfo.setLogMapping(RequestUtils.getUrl(request));
        sysLoggerInfo.setLogCode(IdUtils.createUlid());
        sysLoggerInfo.setLogContent(getContent(content, invocation, proceed));
        applicationContext.publishEvent(sysLoggerInfo);
    }

    private String getContent(String content, MethodInvocation invocation, Object proceed) {
        if(StringUtils.isEmpty(content)) {
            return CommonConstant.SYMBOL_EMPTY;
        }
        ExpressionParser expressionParser = new SpelExpressionParser();
        EvaluationContext evaluationContext = new StandardEvaluationContext();

        Method method = invocation.getMethod();
        Object[] arguments = invocation.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            Object argument = arguments[i];
            evaluationContext.setVariable("$arg" + i, argument);
        }

        evaluationContext.setVariable("$method", method);
        evaluationContext.setVariable("$result", proceed);

        Expression expression = expressionParser.parseExpression(content);
        return Optional.ofNullable( expression.getValue(evaluationContext)).orElse(content).toString();
    }

    protected String getModule(Method method) {
        String name = method.getName().toUpperCase();
        if (name.contains(SAVE) || name.contains(INSERT) || name.contains(ADD)) {
            return "添加";
        }
        if (name.contains(UPDATE) || name.contains(MODIFY)) {
            return "修改";
        }
        if (name.contains(DELETE) || name.contains(DROP) || name.contains(REMOVE)) {
            return "删除";
        }
        if (name.contains(RESET)) {
            return "重置";
        }
        return "查询";
    }

    private String getName(OperateLog operateLog, Method method) {
        String name = operateLog.name();
        if(StringUtils.isNotBlank(name)) {
            return name;
        }

        //"io.swagger.v3.oas.annotations.Operation"
        if(null != OPERATION) {
            Annotation declaredAnnotation = method.getDeclaredAnnotation(OPERATION);
            Map<String, Object> annotationAttributes = AnnotationUtils.getAnnotationAttributes(declaredAnnotation);
            return MapUtils.getString(annotationAttributes, "summary");
        }
        if(null != API_OPERATION) {
            Annotation declaredAnnotation = method.getDeclaredAnnotation(API_OPERATION);
            Map<String, Object> annotationAttributes = AnnotationUtils.getAnnotationAttributes(declaredAnnotation);
            return MapUtils.getString(annotationAttributes, "value");
        }
        return null;
    }
}
