package com.chua.starter.common.support.logger;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.*;
import com.chua.starter.common.support.annotations.SysLog;
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

import static com.chua.common.support.constant.NameConstant.*;

/**
 * 系统操作日志切入点顾问
 * <p>
 * 基于 AOP 的操作日志记录组件，用于自动记录带有 {@link SysLog} 注解的方法调用信息。
 * </p>
 *
 * <h3>功能特性：</h3>
 * <ul>
 *     <li>自动拦截带 @SysLog 注解的方法</li>
 *     <li>记录方法执行耗时</li>
 *     <li>记录请求参数（可配置）</li>
 *     <li>记录操作结果（成功/失败）</li>
 *     <li>支持 SpEL 表达式自定义日志内容</li>
 *     <li>自动识别 Swagger 注解获取操作名称</li>
 *     <li>根据方法名自动推断操作类型（添加/修改/删除/查询）</li>
 * </ul>
 *
 * <h3>记录的信息：</h3>
 * <ul>
 *     <li>logName - 操作名称（优先级：@SysLog.name > @Operation.summary > @ApiOperation.value）</li>
 *     <li>logModule - 操作模块</li>
 *     <li>logParam - 请求参数（JSON 格式，长度限制 2000）</li>
 *     <li>logContent - 日志内容（支持 SpEL 表达式）</li>
 *     <li>logMapping - 请求 URL</li>
 *     <li>logCost - 执行耗时（毫秒）</li>
 *     <li>logStatus - 执行状态（1-成功，0-失败）</li>
 *     <li>clientIp - 客户端 IP</li>
 *     <li>fingerprint - 设备指纹</li>
 *     <li>createBy - 操作人 ID</li>
 *     <li>createName - 操作人名称</li>
 *     <li>createTime - 操作时间</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>
 * &#64;SysLog(name = "用户登录", module = "认证", logArgs = true)
 * public void login(String username, String password) {
 *     // 业务逻辑
 * }
 *
 * // 使用 SpEL 表达式
 * &#64;SysLog(name = "更新用户", content = "更新用户 #{$arg0.username} 的信息，结果：#{$result}")
 * public User updateUser(User user) {
 *     // 业务逻辑
 * }
 * </pre>
 *
 * <h3>SpEL 表达式变量：</h3>
 * <ul>
 *     <li>$arg0, $arg1, ... - 方法参数</li>
 *     <li>$method - 当前方法</li>
 *     <li>$result - 方法返回值</li>
 * </ul>
 *
 * <h3>自动模块推断规则：</h3>
 * <ul>
 *     <li>方法名包含 save/insert/add → 添加</li>
 *     <li>方法名包含 update/modify → 修改</li>
 *     <li>方法名包含 delete/drop/remove → 删除</li>
 *     <li>方法名包含 reset → 重置</li>
 *     <li>其他 → 查询</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 * @see SysLog
 * @see SysLoggerInfo
 * @see LoggerIgnore
 */
@Lazy
public class SysLoggerPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor implements InitializingBean {

    public static final String X_REQ_FINGERPRINT = "x-req-fingerprint";
    @Autowired HttpServletRequest request;
    @Autowired HttpServletResponse response;

    @Autowired
    private ApplicationContext applicationContext;

    private static final Class<? extends Annotation> OPERATION = (Class<? extends Annotation>) ClassUtils.forName("io.swagger.v3.oas.annotations.Operation");
    private static final Class<? extends Annotation> API_OPERATION = (Class<? extends Annotation>) ClassUtils.forName("io.swagger.annotations.ApiOperation");

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(SysLog.class) && !method.isAnnotationPresent(LoggerIgnore.class);
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
     * @throws Throwable 可抛出
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
        SysLog sysLog = method.getDeclaredAnnotation(SysLog.class);
        if (!sysLog.enable()) {
            return;
        }

        String name = getName(sysLog, method);
        if(null == name) {
            return;
        }

        String module = getModule(method);
        SysLoggerInfo sysLoggerInfo = new SysLoggerInfo(name);
        sysLoggerInfo.setCreateBy(RequestUtils.getUserId());
        sysLoggerInfo.setCreateName(RequestUtils.getUsername());
        sysLoggerInfo.setCreateTime(new Date());
        sysLoggerInfo.setLogName(name);
        sysLoggerInfo.setFingerprint(RequestUtils.getHeader(request, X_REQ_FINGERPRINT));
        sysLoggerInfo.setLogModule(StringUtils.defaultString(sysLog.module(), module));
        sysLoggerInfo.setLogCost((System.currentTimeMillis() - startTime) );
        sysLoggerInfo.setClientIp(RequestUtils.getIpAddress(request));

        if (sysLog.logArgs()) {
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

        String content = sysLog.content();
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

    private String getName(SysLog sysLog, Method method) {
        String name = sysLog.name();
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

