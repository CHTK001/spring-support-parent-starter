package com.chua.starter.common.support.logger;

import com.chua.common.support.text.json.Json;
import com.chua.common.support.core.utils.*;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
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
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

import static com.chua.common.support.core.constant.NameConstant.*;

/**
 * 抽象日志切入点顾问
 * <p>提供日志切面的通用功能，包括参数提取、SpEL表达式解析、Swagger注解识别等</p>
 *
 * @author CH
 * @since 2024-01-01
 */
@Lazy
public abstract class AbstractLoggerPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor implements InitializingBean {

    /**
     * 请求指纹头
     */
    public static final String X_REQ_FINGERPRINT = "x-req-fingerprint";

    @Autowired
    protected HttpServletRequest request;

    @Autowired
    protected HttpServletResponse response;

    @Autowired
    protected ApplicationContext applicationContext;

    /**
     * Swagger 3.x Operation 注解类
     */
    private static final Class<? extends Annotation> OPERATION = (Class<? extends Annotation>) ClassUtils.forName("io.swagger.v3.oas.annotations.Operation");

    /**
     * Swagger 2.x ApiOperation 注解类
     */
    private static final Class<? extends Annotation> API_OPERATION = (Class<? extends Annotation>) ClassUtils.forName("io.swagger.annotations.ApiOperation");

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
     * 执行方法并记录日志
     *
     * @param invocation 方法调用
     * @return 方法返回值
     * @throws Throwable 方法执行异常
     */
    protected Object invokeValue(MethodInvocation invocation) throws Throwable {
        Object proceed = null;
        Throwable throwable = null;
        long startTime = System.currentTimeMillis();
        try {
            proceed = invocation.proceed();
        } catch (Throwable e) {
            throwable = e;
        }
        createOperateLogger(proceed, throwable, invocation, startTime);
        if (null != throwable) {
            throw throwable;
        }
        return proceed;
    }

    /**
     * 创建操作日志
     * <p>子类需要实现此方法来构建具体的日志信息对象并发布事件</p>
     *
     * @param proceed    方法执行结果
     * @param throwable  方法执行异常，为null表示执行成功
     * @param invocation 方法调用信息
     * @param startTime  方法执行开始时间戳
     */
    protected abstract void createOperateLogger(Object proceed, Throwable throwable, MethodInvocation invocation, long startTime);

    /**
     * 获取操作名称
     * <p>优先级：注解name > Swagger 3.x Operation.summary > Swagger 2.x ApiOperation.value</p>
     *
     * @param annotationName 注解中指定的名称
     * @param method         方法对象
     * @return 操作名称，如果无法获取返回null
     */
    protected String getName(String annotationName, Method method) {
        if (StringUtils.isNotBlank(annotationName)) {
            return annotationName;
        }

        // 从 Swagger 3.x 注解获取
        if (null != OPERATION) {
            Annotation declaredAnnotation = method.getDeclaredAnnotation(OPERATION);
            if (null != declaredAnnotation) {
                Map<String, Object> annotationAttributes = AnnotationUtils.getAnnotationAttributes(declaredAnnotation);
                String summary = MapUtils.getString(annotationAttributes, "summary");
                if (StringUtils.isNotBlank(summary)) {
                    return summary;
                }
            }
        }

        // 从 Swagger 2.x 注解获取（兼容老版本）
        if (null != API_OPERATION) {
            Annotation declaredAnnotation = method.getDeclaredAnnotation(API_OPERATION);
            if (null != declaredAnnotation) {
                Map<String, Object> annotationAttributes = AnnotationUtils.getAnnotationAttributes(declaredAnnotation);
                String value = MapUtils.getString(annotationAttributes, "value");
                if (StringUtils.isNotBlank(value)) {
                    return value;
                }
            }
        }

        return null;
    }

    /**
     * 根据方法名自动识别操作模块类型
     *
     * @param method 方法对象
     * @return 操作模块名称
     */
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

    /**
     * 构建请求参数的JSON字符串
     *
     * @param invocation 方法调用信息
     * @return 参数的JSON字符串，如果无参数或构建失败返回null
     */
    protected String buildParamJson(MethodInvocation invocation) {
        List<Object> params = new LinkedList<>();
        for (Object argument : invocation.getArguments()) {
            if (null == argument) {
                continue;
            }
            Class<?> aClass = argument.getClass();
            String typeName = aClass.getTypeName();
            if (HttpServletResponse.class.isAssignableFrom(aClass)
                    || HttpServletRequest.class.isAssignableFrom(aClass)
                    || typeName.startsWith("org.spring")
                    || typeName.startsWith("javax")
                    || typeName.startsWith("java")) {
                continue;
            }
            params.add(argument);
        }

        try {
            return Json.prettyFormat(params);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 解析SpEL表达式内容
     *
     * @param content    表达式内容
     * @param invocation 方法调用信息
     * @param proceed    方法返回值
     * @return 解析后的内容
     */
    protected String getContent(String content, MethodInvocation invocation, Object proceed) {
        if (StringUtils.isEmpty(content)) {
            return "";
        }

        ParserContext parserContext = new TemplateParserContext();
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

        Expression expression = expressionParser.parseExpression(content, parserContext);
        return Optional.ofNullable(expression.getValue(evaluationContext)).orElse(content).toString();
    }

    /**
     * 发布日志事件
     *
     * @param event 日志事件对象
     */
    protected void publishEvent(Object event) {
        applicationContext.publishEvent(event);
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    protected String getUserId() {
        return RequestUtils.getUserId();
    }

    /**
     * 获取用户名
     *
     * @return 用户名
     */
    protected String getUsername() {
        return RequestUtils.getUsername();
    }

    /**
     * 获取客户端IP
     *
     * @return 客户端IP
     */
    protected String getClientIp() {
        return RequestUtils.getIpAddress(request);
    }

    /**
     * 获取请求URL
     *
     * @return 请求URL
     */
    protected String getRequestUrl() {
        return RequestUtils.getUrl(request);
    }

    /**
     * 获取请求头
     *
     * @param name 头名称
     * @return 头值
     */
    protected String getHeader(String name) {
        return RequestUtils.getHeader(request, name);
    }
}

