package com.chua.starter.mybatis.logger;

import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.annotations.SysLog;
import com.chua.starter.common.support.logger.AbstractLoggerPointcutAdvisor;
import com.chua.starter.common.support.logger.LoggerIgnore;
import com.chua.starter.common.support.logger.SysLoggerInfo;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Date;

/**
 * 系统操作日志切入点顾问
 * <p>
 * 基于 AOP 的操作日志记录组件，用于自动记录带有 {@link SysLog} 注解的方法调用信息。
 * 通过事件机制发布日志信息，各模块可以监听并处理日志事件。
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
 *     <li>通过事件机制发布日志，支持模块化处理</li>
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
@Component
public class SysLoggerPointcutAdvisor extends AbstractLoggerPointcutAdvisor {

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(SysLog.class) && !method.isAnnotationPresent(LoggerIgnore.class);
    }

    @Override
    protected void createOperateLogger(Object proceed, Throwable throwable, MethodInvocation invocation, long startTime) {
        Method method = invocation.getMethod();
        SysLog sysLog = method.getDeclaredAnnotation(SysLog.class);
        if (null == sysLog || !sysLog.enable()) {
            return;
        }

        String name = getName(sysLog.name(), method);
        if (null == name) {
            return;
        }

        String module = getModule(method);
        SysLoggerInfo sysLoggerInfo = new SysLoggerInfo(name);
        sysLoggerInfo.setCreateBy(getUserId());
        sysLoggerInfo.setCreateName(getUsername());
        sysLoggerInfo.setCreateTime(new Date());
        sysLoggerInfo.setLogName(name);
        sysLoggerInfo.setFingerprint(getHeader(X_REQ_FINGERPRINT));
        sysLoggerInfo.setLogModule(StringUtils.defaultString(sysLog.module(), module));
        sysLoggerInfo.setLogCost((System.currentTimeMillis() - startTime));
        sysLoggerInfo.setClientIp(getClientIp());

        if (sysLog.logArgs()) {
            String json = buildParamJson(invocation);
            if (null != json && json.length() < 2000) {
                sysLoggerInfo.setLogParam(json);
            }
        }

        String content = sysLog.content();
        sysLoggerInfo.setLogStatus(null == throwable ? 1 : 0);
        sysLoggerInfo.setLogMapping(getRequestUrl());
        sysLoggerInfo.setLogCode(IdUtils.createUlid());
        sysLoggerInfo.setLogContent(getContent(content, invocation, proceed));
        sysLoggerInfo.setMethodName(method.getName());

        // 发布日志事件
        publishEvent(sysLoggerInfo);
    }
}

