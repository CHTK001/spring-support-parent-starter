package com.chua.starter.common.support.logger;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.date.DateTime;
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
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.SpelParserConfiguration;
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
public class OperateLoggerPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor implements InitializingBean {

    @Autowired HttpServletRequest request;
    @Autowired HttpServletResponse response;

    @Autowired
    private ApplicationContext applicationContext;

    private static final Class<? extends Annotation> OPERATION = (Class<? extends Annotation>) ClassUtils.forName("io.swagger.v3.oas.annotations.Operation");
    private static final Class<? extends Annotation> API_OPERATION = (Class<? extends Annotation>) ClassUtils.forName("io.swagger.annotations.ApiOperation");

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(OperateLog.class) && !method.isAnnotationPresent(LoggerIgnore.class);
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
            createOperateLogger(proceed, throwable, invocation, startTime);
            return proceed;
        } catch (Throwable e) {
            throwable = e;
            createOperateLogger(proceed, throwable, invocation, startTime);
            throw e;
        }
    }

    void createOperateLogger(Object proceed, Throwable throwable, MethodInvocation invocation, long startTime) {
        Method method = invocation.getMethod();
        SysLogger sysLogger = method.getDeclaredAnnotation(SysLogger.class);
        if(null == sysLogger) {
            return;
        }
        String name = getName(sysLogger, method);
        if(null == name) {
            return;
        }

        String module = getModule(method);
        OperateLoggerInfo operateLoggerInfo = new OperateLoggerInfo(name);
        operateLoggerInfo.setCreateBy(RequestUtils.getUsername());
        operateLoggerInfo.setCreateName(RequestUtils.getUserId());
        operateLoggerInfo.setCreateTime(new Date());
        operateLoggerInfo.setLogName(name);
        operateLoggerInfo.setLogModule(StringUtils.defaultString(sysLogger.module(), module));
        operateLoggerInfo.setLogCost((System.currentTimeMillis() - startTime) / 1000D);
        operateLoggerInfo.setClientIp(RequestUtils.getIpAddress(request));

        if(sysLogger.logArgs()) {
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
                operateLoggerInfo.setLogParam(json);
            }
        }

        String content = sysLogger.content();
        operateLoggerInfo.setLogStatus(null == throwable ? 1 : 0);
        operateLoggerInfo.setLogMapping(RequestUtils.getUrl(request));
        operateLoggerInfo.setLogCode(IdUtils.createUlid());
        operateLoggerInfo.setLogContent(getContent(content, invocation, proceed));
        applicationContext.publishEvent(operateLoggerInfo);
    }

    private String getContent(String content, MethodInvocation invocation, Object proceed) {
        if(StringUtils.isEmpty(content)) {
            return CommonConstant.SYMBOL_EMPTY;
        }
        ExpressionParser expressionParser = new SpelExpressionParser(new SpelParserConfiguration(true,true));
        EvaluationContext evaluationContext = new StandardEvaluationContext();

        Method method = invocation.getMethod();
        Object[] arguments = invocation.getArguments();
        for (int i = 0; i < arguments.length; i++) {
            Object argument = arguments[i];
            evaluationContext.setVariable("arg" + i, argument);
        }
        evaluationContext.setVariable("args", arguments);

        evaluationContext.setVariable("now", DateTime.now().toStandard());
        evaluationContext.setVariable("method", method);
        evaluationContext.setVariable("result", proceed);

        Expression expression = expressionParser.parseExpression(content, new TemplateParserContext());
        try {
            return Optional.ofNullable( expression.getValue(evaluationContext)).orElse(content).toString();
        } catch (EvaluationException e) {
           // e.printStackTrace();
        }
        return content;
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

    private String getName(SysLogger sysLogger, Method method) {
        String name = sysLogger.name();
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
