package com.chua.starter.swagger.support.log;

import com.chua.common.support.lang.date.DateTime;
import com.chua.starter.common.support.logger.Logger;
import com.chua.starter.common.support.logger.LoggerIgnore;
import com.chua.starter.common.support.logger.LoggerPointcutAdvisor;
import com.chua.starter.common.support.logger.LoggerService;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

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

    public SwaggerLoggerPointcutAdvisor(LoggerService loggerService) {
        super(loggerService);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return (!AnnotatedElementUtils.hasAnnotation(method, LoggerIgnore.class)
                && AnnotatedElementUtils.hasAnnotation(method, Logger.class))
                &&
                (AnnotatedElementUtils.hasAnnotation(method, ApiOperation.class) ||
                AnnotatedElementUtils.hasAnnotation(method, Operation.class));


    }

    @Override
    protected String getAction(Method method) {
        String name = method.getName().toUpperCase();
        if (name.contains("SAVE") || name.contains("INSERT") || name.contains("ADD")) {
            return "添加";
        }
        if (name.contains("UPDATE") || name.contains("MODIFY")) {
            return "修改";
        }
        if (name.contains("DELETE") || name.contains("DROP") || name.contains("REMOVE")) {
            return "删除";
        }
        if (name.contains("RESET")) {
            return "重置";
        }
        return "查询";
    }

    @Override
    protected String getName(Method method) {
        ApiOperation apiOperation = method.getDeclaredAnnotation(ApiOperation.class);
        if (null != apiOperation) {
            return apiOperation.value();
        }
        Operation operation = method.getDeclaredAnnotation(Operation.class);
        return operation.summary();
    }

    @Override
    protected String getContent(StandardEvaluationContext standardEvaluationContext, Method method) {
        Object username = request.getSession().getAttribute("username");
        return DateTime.now().toStandard() + " " + username + getAction(method) + "了模块" + getName(method);
    }


}