// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.mybatis.logger;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.annotations.SysLog;
import com.chua.starter.common.support.logger.AbstractLoggerPointcutAdvisor;
import com.chua.starter.common.support.logger.LoggerIgnore;
import com.chua.starter.common.support.logger.SysLoggerInfo;
import com.chua.starter.common.support.network.UserAgent;
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
 * @since 2024/01/01
 * @see SysLog
 * @see SysLoggerInfo
 * @see LoggerIgnore
 * @author CH
 */
@Lazy
@Component
public class SysLoggerPointcutAdvisor extends AbstractLoggerPointcutAdvisor {

    @Override
    /**
     * Matches
     * @param method 参数
     * @param targetClass 参数
     * @return 返回值
    */
    public boolean matches(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(SysLog.class) && !method.isAnnotationPresent(LoggerIgnore.class);
    }

    @Override
    /**
     * 创建operate日志记录器
     * @param invocation 参数
     * @param proceed 对象
     * @param startTime 长整数
     * @param throwable 可抛出异常
     * @return 返回值
    */
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
        String ua = getHeader("User-Agent");
        if (ua != null) {
            UserAgent.UserAgentInfo agentInfo = UserAgent.parseUserAgentString(ua);
            sysLoggerInfo.setUa(ua);
            sysLoggerInfo.setBrowser(agentInfo.getBrowser() != null ? agentInfo.getBrowser().toString() : null);
            sysLoggerInfo.setSystem(agentInfo.getOperatingSystem() != null ? agentInfo.getOperatingSystem().getName() : null);
        }

        if (sysLog.logArgs()) {
            String json = buildParamJson(invocation);
            if (null != json && json.length() < 2000) {
                sysLoggerInfo.setLogParam(json);
            }
        }

        String content = sysLog.content();
        sysLoggerInfo.setLogStatus(resolveLogStatus(proceed, throwable));
        sysLoggerInfo.setLogMapping(getRequestUrl());
        sysLoggerInfo.setLogCode(java.util.UUID.randomUUID().toString());
        sysLoggerInfo.setLogContent(getContent(content, invocation, proceed));
        sysLoggerInfo.setMethodName(method.getName());

        // 发布日志事件
        /**
         * 发布事件。
         * @param sysLoggerInfo sys日志记录器信息
         */
        publishEvent(sysLoggerInfo);
    }

    /**
     * 判定一次操作为成功还是失败。
     *
     * <p>只按「是否抛异常」判定会把业务失败记成成功：monitor 的
     * {@code ServerManageService} 全部在 controller 内部 catch 后返回
     * {@link ReturnResult#error(String)}，异常没有穿出方法边界，切面拿到的
     * {@code throwable} 恒为 null。因此返回值是 {@link ReturnResult} 时，
     * 以 {@link ReturnResult#isOk()}（{@code 00000} / {@code 200} 视为成功）为准；
     * 其它返回类型仍沿用异常判定。</p>
     *
     * @param proceed   方法返回值
     * @param throwable 方法抛出的异常，为空表示没有异常
     * @return 1 表示成功；0 表示失败
     */
    private int resolveLogStatus(Object proceed, Throwable throwable) {
        if (null != throwable) {
            return 0;
        }
        if (proceed instanceof ReturnResult<?> result) {
            return result.isOk() ? 1 : 0;
        }
        return 1;
    }
}
