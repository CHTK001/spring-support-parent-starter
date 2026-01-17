package com.chua.starter.oauth.client.support.logger;

import com.chua.common.support.time.date.DateTime;
import com.chua.common.support.network.net.UserAgent;
import com.chua.common.support.core.utils.IdUtils;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.common.support.annotations.UserLog;
import com.chua.starter.common.support.logger.AbstractLoggerPointcutAdvisor;
import com.chua.starter.common.support.logger.LoggerIgnore;
import com.chua.starter.common.support.logger.UserLoggerInfo;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.annotation.Lazy;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

import static com.chua.common.support.core.constant.NameConstant.*;

/**
 * 用户操作日志切入点顾问
 * <p>用于拦截带有 {@link UserLog} 注解的方法，记录用户操作日志</p>
 * <p>日志格式：[时间] 用户名 执行了 模块-操作名称 (耗时: xxxms, 状态: 成功/失败)</p>
 * <p>通过事件机制发布日志信息，各模块可以监听并处理日志事件</p>
 *
 * @author CH
 * @version 1.0
 * @since 2024-01-01
 * @see UserLog
 * @see UserLoggerInfo
 */
@Lazy
@Slf4j
@Component
public class UserLoggerPointcutAdvisor extends AbstractLoggerPointcutAdvisor {

    /**
     * 日期格式化器，用于格式化日志时间
     */
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 匿名用户标识
     */
    private static final String ANONYMOUS_USER = "匿名用户";

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(UserLog.class) && !method.isAnnotationPresent(LoggerIgnore.class);
    }

    @Override
    protected void createOperateLogger(Object proceed, Throwable throwable, MethodInvocation invocation, long startTime) {
        Method method = invocation.getMethod();
        UserLog userLog = method.getDeclaredAnnotation(UserLog.class);
        if (null == userLog) {
            log.debug("方法 {} 未标注 @UserLog 注解，跳过日志记录", method.getName());
            return;
        }

        String name = getName(userLog.name(), method);
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
        String username = getUsername();
        String displayUsername = StringUtils.isNotBlank(username) ? username : ANONYMOUS_USER;
        // 获取操作模块
        String module = getModule(method);
        String logModule = StringUtils.defaultString(userLog.module(), module);
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
        userLoggerInfo.setLoginType(userLog.loginType());
        userLoggerInfo.setCreateBy(getUserId());
        userLoggerInfo.setBrowser(userAgent.getBrowser().toString());
        userLoggerInfo.setUa(header);
        userLoggerInfo.setSystem(userAgent.getOperatingSystem().getName());
        userLoggerInfo.setCreateName(username);
        userLoggerInfo.setCreateTime(currentTime);
        userLoggerInfo.setLogName(name);
        userLoggerInfo.setLogModule(logModule);
        userLoggerInfo.setLogCost(costTime);
        userLoggerInfo.setClientIp(getClientIp());
        userLoggerInfo.setMethodName(method.getName());

        // 记录请求参数
        if (userLog.logArgs()) {
            String paramJson = buildParamJson(invocation);
            if (null != paramJson && paramJson.length() < 2000) {
                userLoggerInfo.setLogParam(paramJson);
            }
            log.debug("请求参数: {}", paramJson);
        }

        String content = userLog.content();
        userLoggerInfo.setLogStatus(isSuccess ? 1 : 0);
        userLoggerInfo.setLogMapping(getRequestUrl());
        userLoggerInfo.setLogCode(java.util.UUID.randomUUID().toString().replace("-", ""));
        userLoggerInfo.setLogContent(getContent(content, invocation, proceed));

        // 发布日志事件
        publishEvent(userLoggerInfo);
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

    @Override
    protected String getContent(String content, MethodInvocation invocation, Object proceed) {
        if (StringUtils.isEmpty(content)) {
            return "";
        }
        ExpressionParser expressionParser = new SpelExpressionParser(new SpelParserConfiguration(true, true));
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
            return Optional.ofNullable(expression.getValue(evaluationContext)).orElse(content).toString();
        } catch (EvaluationException e) {
            // 表达式解析失败，返回原内容
        }
        return content;
    }

    @Override
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

    @Override
    protected String getName(String annotationName, Method method) {
        String name = super.getName(annotationName, method);
        if (null != name) {
            return name;
        }
        // 如果都无法获取，使用方法名作为备选
        log.debug("无法从注解获取操作名称，使用方法名作为备选: {}", method.getName());
        return method.getName();
    }
}

