package com.chua.starter.rpc.support.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * RPC日志拦截器
 * <p>
 * 记录RPC调用的日志信息
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
@Component
public class LoggingRpcInterceptor implements RpcInterceptor {

    @Override
    public boolean preHandle(Method method, Object[] args) {
        log.debug("[RPC] 调用开始: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());
        return true;
    }

    @Override
    public Object postHandle(Method method, Object[] args, Object result) {
        log.debug("[RPC] 调用成功: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());
        return result;
    }

    @Override
    public void handleException(Method method, Object[] args, Throwable error) throws Throwable {
        log.error("[RPC] 调用失败: {}.{}, error={}",
            method.getDeclaringClass().getSimpleName(), method.getName(), error.getMessage());
        throw error;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE; // 最低优先级，最后执行
    }
}
