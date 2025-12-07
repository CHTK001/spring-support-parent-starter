package com.chua.starter.common.support.logger;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.net.UserAgent;
import com.chua.common.support.utils.*;
import com.chua.starter.common.support.annotations.UserLogger;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.chua.common.support.constant.NameConstant.*;

/**
 * 操作日志切入点顾问
 * <p>用于拦截带有 {@link UserLogger} 注解的方法，记录用户操作日志</p>
 * <p>日志格式：[时间] 用户名 执行了 模块-操作名称 (耗时: xxxms, 状态: 成功/失败)</p>
 *
 * @author CH
 * @version 1.0
 * @since 2024-01-01
 */
@Lazy
@Slf4j
public class UserLoggerPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor implements InitializingBean {

    /**
     * 日期格式化器，用于格式化日志时间
     */
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 匿名用户标识
     */
    private static final String ANONYMOUS_USER = "匿名用户";

    @Autowired HttpServletRequest request;
    @Autowired HttpServletResponse response;

    @Autowired
    private ApplicationContext applicationContext;

    private static final Class<? extends Annotation> OPERATION = (Class<? extends Annotation>) ClassUtils.forName("io.swagger.v3.oas.annotations.Operation");
    private static final Class<? extends Annotation> API_OPERATION = (Class<? extends Annotation>) ClassUtils.forName("io.swagger.annotations.ApiOperation");

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(UserLogger.class) && !method.isAnnotationPresent(LoggerIgnore.class);
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
     * 执行�?
     *
     * @param invocation 调用
     * @return {@link Object}
     * @throws Throwable 可丢�?
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

    /**
     * 创建操作日志
     * <p>记录用户操作的详细信息，包括操作人、操作时间、操作模块、操作名称、耗时等</p>
     *
     * @param proceed    方法执行结果
     * @param throwable  方法执行异常，为null表示执行成功
     * @param invocation 方法调用信息
     * @param startTime  方法执行开始时间戳
     */
    void createOperateLogger(Object proceed, Throwable throwable, MethodInvocation invocation, long startTime) {
        Method method = invocation.getMethod();
        UserLogger userLogger = method.getDeclaredAnnotation(UserLogger.class);
        if (null == userLogger) {
            log.debug("方法 {} 未标注 @UserLogger 注解，跳过日志记录", method.getName());
            return;
        }

        String name = getName(userLogger, method);
        if (null == name) {
            log.debug("无法获取操作名称，跳过日志记录，方法: {}", method.getName());
            return;
        }

        // 计算操作耗时
        long costTime = System.currentTimeMillis() - startTime;
        // 获取当前时间
        Date currentTime = new Date();
        String formattedTime = new SimpleDateFormat(DATE_FORMAT_PATTERN).format(currentTime);
        // 获取用户名
        String username = RequestUtils.getUsername();
        String displayUsername = StringUtils.isNotBlank(username) ? username : ANONYMOUS_USER;
        // 获取操作模块
        String module = getModule(method);
        String logModule = StringUtils.defaultString(userLogger.module(), module);
        // 判断操作状态
        boolean isSuccess = (null == throwable);
        String statusText = isSuccess ? "成功" : "失败";

        // 构建并输出格式化日志：[时间] 用户名 执行了 模块-操作名称
        String logMessage = buildLogMessage(formattedTime, displayUsername, logModule, name, costTime, statusText, throwable);
        outputLog(isSuccess, logMessage, throwable);

        // 构建日志信息对象
        String header = request.getHeader("login-agent");
        UserAgent userAgent = UserAgent.parseUserAgentString(header);
        UserLoggerInfo userLoggerInfo = new UserLoggerInfo(name);
        userLoggerInfo.setLoginType(userLogger.loginType());
        userLoggerInfo.setCreateBy(RequestUtils.getUserId());
        userLoggerInfo.setBrowser(userAgent.getBrowser().toString());
        userLoggerInfo.setUa(header);
        userLoggerInfo.setSystem(userAgent.getOperatingSystem().getName());
        userLoggerInfo.setCreateName(username);
        userLoggerInfo.setCreateTime(currentTime);
        userLoggerInfo.setLogName(name);
        userLoggerInfo.setLogModule(logModule);
        userLoggerInfo.setLogCost(costTime);
        userLoggerInfo.setClientIp(RequestUtils.getIpAddress(request));
        userLoggerInfo.setMethodName(method.getName());

        // 记录请求参数
        if (userLogger.logArgs()) {
            String paramJson = buildParamJson(invocation);
            if (null != paramJson && paramJson.length() < 2000) {
                userLoggerInfo.setLogParam(paramJson);
            }
            log.debug("请求参数: {}", paramJson);
        }

        String content = userLogger.content();
        userLoggerInfo.setLogStatus(isSuccess ? 1 : 0);
        userLoggerInfo.setLogMapping(RequestUtils.getUrl(request));
        userLoggerInfo.setLogCode(IdUtils.createUlid());
        userLoggerInfo.setLogContent(getContent(content, invocation, proceed));

        // 记录详细的debug日志
        log.debug("用户操作日志详情 - 日志编码: {}, IP: {}, 浏览器: {}, 系统: {}, 请求地址: {}",
                userLoggerInfo.getLogCode(),
                userLoggerInfo.getClientIp(),
                userLoggerInfo.getBrowser(),
                userLoggerInfo.getSystem(),
                userLoggerInfo.getLogMapping());

        // 发布日志事件
        applicationContext.publishEvent(userLoggerInfo);
    }

    /**
     * 构建格式化的日志消息
     *
     * @param formattedTime 格式化后的时间字符串
     * @param username      用户名
     * @param module        操作模块
     * @param name          操作名称
     * @param costTime      操作耗时(毫秒)
     * @param statusText    状态文本(成功/失败)
     * @param throwable     异常信息，可为null
     * @return 格式化后的日志消息
     */
    private String buildLogMessage(String formattedTime, String username, String module, String name,
                                   long costTime, String statusText, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(formattedTime).append("] ")
                .append(username).append(" 执行了 ")
                .append(module).append("-").append(name)
                .append(" (耗时: ").append(costTime).append("ms, ")
                .append("状态: ").append(statusText).append(")");

        if (null != throwable) {
            sb.append(" 异常: ").append(throwable.getMessage());
        }

        return sb.toString();
    }

    /**
     * 输出日志
     * <p>根据操作状态选择不同的日志级别输出</p>
     *
     * @param isSuccess  操作是否成功
     * @param logMessage 日志消息
     * @param throwable  异常信息，可为null
     */
    private void outputLog(boolean isSuccess, String logMessage, Throwable throwable) {
        if (isSuccess) {
            log.info(logMessage);
        } else {
            log.error(logMessage, throwable);
        }
    }

    /**
     * 构建请求参数的JSON字符串
     *
     * @param invocation 方法调用信息
     * @return 参数的JSON字符串，如果无参数或构建失败返回null
     */
    private String buildParamJson(MethodInvocation invocation) {
        List<Object> params = new LinkedList<>();
        for (Object argument : invocation.getArguments()) {
            if (null == argument) {
                continue;
            }
            Class<?> aClass = argument.getClass();
            String typeName = aClass.getTypeName();
            // 过滤掉特殊类型的参数
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
        } catch (Exception e) {
            log.debug("序列化请求参数失败: {}", e.getMessage());
            return null;
        }
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

    /**
     * 根据方法名自动识别操作模块类型
     * <p>支持的操作类型：添加、修改、删除、重置、查询、导入、导出、上传、下载</p>
     *
     * @param method 方法对象
     * @return 操作模块名称
     */
    protected String getModule(Method method) {
        String name = method.getName().toUpperCase();
        if (name.contains(SAVE) || name.contains(INSERT) || name.contains(ADD) || name.contains("CREATE")) {
            return "添加";
        }
        if (name.contains(UPDATE) || name.contains(MODIFY) || name.contains("EDIT") || name.contains("CHANGE")) {
            return "修改";
        }
        if (name.contains(DELETE) || name.contains(DROP) || name.contains(REMOVE) || name.contains("DEL")) {
            return "删除";
        }
        if (name.contains(RESET)) {
            return "重置";
        }
        if (name.contains("IMPORT")) {
            return "导入";
        }
        if (name.contains("EXPORT")) {
            return "导出";
        }
        if (name.contains("UPLOAD")) {
            return "上传";
        }
        if (name.contains("DOWNLOAD")) {
            return "下载";
        }
        if (name.contains("LOGIN") || name.contains("SIGNIN")) {
            return "登录";
        }
        if (name.contains("LOGOUT") || name.contains("SIGNOUT")) {
            return "登出";
        }
        if (name.contains("APPROVE") || name.contains("AUDIT")) {
            return "审批";
        }
        return "查询";
    }

    /**
     * 获取操作名称
     * <p>优先级：UserLogger.name() > Swagger Operation.summary() > Swagger ApiOperation.value()</p>
     *
     * @param userLogger 用户日志注解
     * @param method     方法对象
     * @return 操作名称，如果无法获取返回null
     */
    private String getName(UserLogger userLogger, Method method) {
        String name = userLogger.name();
        if (StringUtils.isNotBlank(name)) {
            return name;
        }

        // 尝试从Swagger 3.x注解获取
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

        // 尝试从Swagger 2.x注解获取
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

        // 如果都无法获取，使用方法名作为备选
        log.debug("无法从注解获取操作名称，使用方法名作为备选: {}", method.getName());
        return method.getName();
    }
}

