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
* @since 2025-03-20
* @author CH
 */
@Slf4j
@Component
public class LoggingRpcInterceptor implements RpcInterceptor {

    @Override
    /** Pre处理 */
    public boolean preHandle(Method method, Object[] args) {
        log.debug("[RPC] 调用开始: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());
        return true;
    }

    @Override
    /** Post处理 */
    public Object postHandle(Method method, Object[] args, Object result) {
        log.debug("[RPC] 调用成功: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());
        return result;
    }

    @Override
    /** 处理异常 */
    public void handleException(Method method, Object[] args, Throwable error) throws Throwable {
        log.error("[RPC] 调用失败: {}.{}, error={}",
            method.getDeclaringClass().getSimpleName(), method.getName(), error.getMessage());
        throw error;
    }

    @Override
    /** 获取订单 */
    public int getOrder() {
        return Integer.MAX_VALUE; // 最低优先级，最后执行
    }
}
